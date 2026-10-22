"""Local, source-grounded recipe AI. No generative claims about unseen quantities."""
from pathlib import Path
import hashlib,io,json,re,sqlite3,threading,unicodedata
import numpy as np
from PIL import Image,ImageOps
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

ROOT=Path(__file__).resolve().parents[1]
CATALOG=ROOT/'app/src/main/assets/catalog'
MODELS=Path(__file__).resolve().parent/'models'

def normalized(text):
    return ''.join(c for c in unicodedata.normalize('NFD',text.lower().replace('đ','d')) if not unicodedata.combining(c))

class RecipeEngine:
    def __init__(self):
        data=json.loads((CATALOG/'recipes.json').read_text(encoding='utf-8'))
        self.recipes=data['recipes'];self.by_key={r['catalogKey']:r for r in self.recipes}
        metadata=MODELS/'recipe_retriever.json';matrix=MODELS/'recipe_vectors.npz'
        fingerprint=hashlib.sha256((CATALOG/'recipes.json').read_bytes()).hexdigest()
        saved=json.loads(metadata.read_text(encoding='utf-8')) if metadata.exists() else {}
        if matrix.exists() and saved.get('catalogSha256')==fingerprint:
            from scipy.sparse import load_npz
            self.vectorizer=TfidfVectorizer(preprocessor=normalized,ngram_range=(1,2),sublinear_tf=True,vocabulary=saved['vocabulary'])
            self.vectorizer.idf_=np.asarray(saved['idf']);self.vectors=load_npz(matrix)
        else:
            self.vectorizer=TfidfVectorizer(preprocessor=normalized,ngram_range=(1,2),sublinear_tf=True,max_features=16000)
            self.vectors=self.vectorizer.fit_transform([r['title']+' '+r['title']+' '+r['ingredients']+' '+r['cookbook'] for r in self.recipes])
        MODELS.mkdir(exist_ok=True)
        # Transparent, inspectable local database; mobile maintains its own Room database.
        db=sqlite3.connect(MODELS/'catalog.sqlite3')
        db.execute('CREATE TABLE IF NOT EXISTS recipes (catalog_key TEXT PRIMARY KEY, title TEXT NOT NULL, document TEXT NOT NULL)')
        with db:
            db.executemany('INSERT OR REPLACE INTO recipes VALUES (?,?,?)',[(r['catalogKey'],r['title'],json.dumps(r,ensure_ascii=False)) for r in self.recipes])
        db.close()
        self.session=None;self.gallery=None;self.gallery_keys=[];self.lock=threading.Lock()

    def compact(self,recipe,score,method):
        return {**recipe,'score':round(float(score),4),'method':method,'scoreMeaning':'độ tương đồng, không phải xác suất đúng','requiresConfirmation':True}

    def text(self,text,limit=5):
        query=self.vectorizer.transform([text]);scores=cosine_similarity(query,self.vectors)[0]
        q=normalized(text)
        for i,r in enumerate(self.recipes):
            title=normalized(r['title'])
            if len(title)>3 and title in q:scores[i]=min(1,scores[i]+.35)
            if 'chay' in title and 'chay' not in q:scores[i]*=.8
        ranked=np.argsort(scores)[::-1][:limit]
        return [self.compact(self.recipes[i],scores[i],'recipe-tfidf-local') for i in ranked if scores[i]>=.055]

    def vision_available(self):return (MODELS/'clip/vision_model_quantized.onnx').is_file()

    def _load_vision(self):
        if self.session is not None:return
        if not self.vision_available():raise FileNotFoundError('Chưa có model ảnh. Chạy python backend/download_model.py trên laptop.')
        import onnxruntime as ort
        options=ort.SessionOptions();options.intra_op_num_threads=2
        self.session=ort.InferenceSession(str(MODELS/'clip/vision_model_quantized.onnx'),sess_options=options,providers=['CPUExecutionProvider'])
        cache=MODELS/'gallery.npz';manifest=MODELS/'gallery.json'
        images={}
        for r in self.recipes:
            if r.get('image') and (CATALOG/r['image']).is_file():images.setdefault(r['image'],[]).append(r['catalogKey'])
        fingerprint=hashlib.sha256(json.dumps(images,sort_keys=True).encode()).hexdigest()
        if cache.exists() and manifest.exists():
            info=json.loads(manifest.read_text(encoding='utf-8'))
            if info.get('fingerprint')==fingerprint:
                self.gallery=np.load(cache)['vectors'];self.gallery_keys=info['keys'];return
        if not images:raise ValueError('Dataset chưa có ảnh được cấp phép; chưa thể nhận diện.')
        vectors=[];keys=[]
        for image,recipe_keys in images.items():
            with Image.open(CATALOG/image) as im:vectors.append(self._embedding(im))
            keys.append(recipe_keys)
        self.gallery=np.stack(vectors);self.gallery_keys=keys
        np.savez_compressed(cache,vectors=self.gallery)
        manifest.write_text(json.dumps({'fingerprint':fingerprint,'keys':keys,'referenceImages':len(keys)},ensure_ascii=False,indent=2),encoding='utf-8')

    def _embedding(self,image):
        image=ImageOps.exif_transpose(image).convert('RGB')
        # CLIP resize short edge 224 then center crop, RGB mean/std from upstream processor.
        ratio=224/min(image.size);image=image.resize((round(image.width*ratio),round(image.height*ratio)),Image.Resampling.BICUBIC)
        left=(image.width-224)//2;top=(image.height-224)//2;image=image.crop((left,top,left+224,top+224))
        values=np.asarray(image,dtype=np.float32)/255
        values=(values-np.array([.48145466,.4578275,.40821073],np.float32))/np.array([.26862954,.26130258,.27577711],np.float32)
        pixels=np.transpose(values,(2,0,1))[None].astype(np.float32)
        outputs=self.session.run(None,{'pixel_values':pixels})
        # ONNX vision encoder advertises image_embeds (512D); never use hidden-state tensors.
        names=[o.name for o in self.session.get_outputs()]
        vector=outputs[names.index('image_embeds') if 'image_embeds' in names else 0][0]
        return vector/(np.linalg.norm(vector)+1e-9)

    def image(self,content,limit=5):
        with Image.open(io.BytesIO(content)) as image:
            if image.width*image.height>40_000_000:raise ValueError('Ảnh quá lớn; hãy chọn ảnh dưới40 megapixel.')
            image.load()
            with self.lock:
                self._load_vision();query=self._embedding(image)
        scores=self.gallery@query;result=[];seen=set()
        for index in np.argsort(scores)[::-1]:
            if scores[index]<.45:continue
            # Shared family photographs are one candidate group, not many independent votes.
            recipe_keys=self.gallery_keys[index]
            r=self.by_key[recipe_keys[0]]
            if r['title'] in seen:continue
            candidate=self.compact(r,scores[index],'clip-image-retrieval-local')
            candidate['relatedRecipes']=[{'catalogKey':k,'title':self.by_key[k]['title']} for k in recipe_keys[:12]]
            result.append(candidate);seen.add(r['title'])
            if len(result)>=limit:break
        category=None;head=MODELS/'vision-scratch/weights.npz'
        report=MODELS/'vision-scratch/report.json'
        approved=json.loads(report.read_text(encoding='utf-8')).get('approvedForRecipeSelection',False) if report.exists() else False
        if head.exists() and approved:
            from backend.train_vision_scratch import features
            weights=np.load(head)
            scores=weights['coef']@features(io.BytesIO(content))+weights['intercept']
            class_index=int(scores[0]>0) if len(weights['classes'])==2 and len(scores)==1 else int(np.argmax(scores))
            category={'label':str(weights['classes'][class_index]),'model':'food-category scratch linear model; experimental, not exact dish identity'}
        return {'candidates':result,'referenceImages':len(self.gallery_keys),'experimentalCategory':category,'warning':'Ảnh không thể xác minh toàn bộ nguyên liệu, định lượng hay chất gây dị ứng. Chọn món để đọc công thức nguồn; các biến thể trong nhóm có thể khác ảnh.'}
