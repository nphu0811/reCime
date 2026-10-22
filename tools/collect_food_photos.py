"""Download reusable Commons photographs with per-image licenses and attribution.

Exact recipe-title matches preferred. Family illustrations are explicitly marked.
No image search thumbnails or images with unknown/non-free licenses are included.
"""
from pathlib import Path
from urllib.parse import quote
import concurrent.futures,hashlib,json,re,time,unicodedata
import requests
from bs4 import BeautifulSoup

ROOT=Path(__file__).resolve().parents[1]
CAT=ROOT/'app/src/main/assets/catalog'
CACHE=ROOT/'datasets/raw/photos';CACHE.mkdir(parents=True,exist_ok=True)
UA='MyRecipeLab/2.0 (educational open recipe catalog; per-image attribution retained)'

def fetch(api,params):
    digest=hashlib.sha256((api+json.dumps(params,sort_keys=True,ensure_ascii=False)).encode()).hexdigest()
    path=CACHE/(digest+'.json')
    if path.exists():return json.loads(path.read_text(encoding='utf-8'))
    for attempt in range(4):
        try:
            response=requests.get(api,params={'format':'json',**params},headers={'User-Agent':UA},timeout=40);response.raise_for_status();data=response.json()
            path.write_text(json.dumps(data,ensure_ascii=False),encoding='utf-8');time.sleep(.15);return data
        except Exception:
            if attempt==3:return {}
            time.sleep(1+attempt)

def plain(s):return BeautifulSoup(s,'html.parser').get_text(' ',strip=True)

def main():
    path=CAT/'recipes.json';data=json.loads(path.read_text(encoding='utf-8'));recipes=data['recipes']
    credits_path=CAT/'photo_credits.json'
    previous_credits=json.loads(credits_path.read_text(encoding='utf-8')) if credits_path.exists() else {}
    family=['Bún bò Huế','Bánh canh','Bánh bèo','Bánh bột lọc','Bánh bò','Bánh cuốn','Bánh đúc','Bánh mì','Bánh xèo','Bánh giò','Bánh tiêu','Bánh chuối','Bánh tráng','Bánh tét','Bánh khúc','Bún chả','Bún riêu','Bún mắm','Bún thịt nướng','Bún bò','Bún cá','Bún ốc','Cơm tấm','Cơm gà','Cơm hến','Cơm chiên','Chả giò','Chả cá','Chả lụa','Gỏi cuốn','Gỏi','Nem rán','Nem chua','Phở','Hủ tiếu','Mì Quảng','Cao lầu','Bò kho','Bò lúc lắc','Bò lá lốt','Canh chua','Thịt kho','Cá kho','Chè','Xôi','Cháo','Lẩu','Đậu phụ','Gà nướng','Gà kho','Cá hấp','Rau muống','Sườn xào chua ngọt','Bánh chưng']
    family.sort(key=len,reverse=True)
    names=list(dict.fromkeys([r['title'] for r in recipes]+family))
    wikipedia={}
    for start in range(0,len(names),35):
        batch=fetch('https://vi.wikipedia.org/w/api.php',{'action':'query','titles':'|'.join(names[start:start+35]),'prop':'pageimages','piprop':'name','redirects':1,'pilicense':'free'})
        query=batch.get('query',{});found={p['title']:p['pageimage'] for p in query.get('pages',{}).values() if p.get('pageimage')}
        wikipedia.update(found)
        for redirect in query.get('redirects',[]):
            if redirect['to'] in found:wikipedia[redirect['from']]=found[redirect['to']]
    assignments={}
    for r in recipes:
        name=r['title'];filename=r.get('imageFile','').removeprefix('File:').removeprefix('Tập tin:')
        match='Ảnh được bài công thức dẫn chiếu'
        if not filename:
            filename=wikipedia.get(name,'');match='Ảnh cùng tên món từ Wikipedia/Commons'
        if not filename:
            for f in family:
                if name.lower().startswith(f.lower()) and f in wikipedia:
                    filename=wikipedia[f];match=f'Ảnh minh họa nhóm món {f}; không phải ảnh đúng biến thể công thức';break
        if filename and re.search(r'\.(jpg|jpeg|png|webp)$',filename,re.I):assignments[r['catalogKey']]=(filename.replace('_',' '),match)
    metadata={};files=list(dict.fromkeys(x[0] for x in assignments.values()))
    for start in range(0,len(files),20):
        batch=fetch('https://commons.wikimedia.org/w/api.php',{'action':'query','titles':'|'.join('File:'+n for n in files[start:start+20]),'prop':'imageinfo','iiprop':'url|extmetadata','iiurlwidth':640,'redirects':1})
        for page in batch.get('query',{}).get('pages',{}).values():
            info=page.get('imageinfo',[])
            if not info:continue
            info=info[0];ext=info.get('extmetadata',{});license=plain(ext.get('LicenseShortName',{}).get('value',''))
            # Attribution/sharealike/public-domain only; never assume a Wikimedia thumbnail is free.
            if not re.match(r'^(CC BY(?:-SA)? [\d.]|CC0|Public domain)',license):continue
            url=info.get('thumburl',info.get('url',''));author=plain(ext.get('Artist',{}).get('value',''))
            if not url or not author:continue
            filename=page['title'].removeprefix('File:').replace('_',' ');local='images/'+hashlib.sha256(filename.encode()).hexdigest()[:16]+'.jpg'
            metadata[filename]={'image':local,'imageAttribution':author,'imageLicense':license,'imageLicenseUrl':ext.get('LicenseUrl',{}).get('value',''),'imageSource':info.get('descriptionurl','https://commons.wikimedia.org/wiki/'+quote(page['title'])),'downloadUrl':url,'imageModification':'Resized to <=640px; JPEG conversion for offline app.'}
    (CAT/'images').mkdir(exist_ok=True)
    import threading
    rate_limited=threading.Event()
    def download(pair):
        filename,item=pair;target=CAT/item['image']
        if target.exists():return filename
        if rate_limited.is_set():return None
        try:
            response=requests.get(item['downloadUrl'],headers={'User-Agent':UA},timeout=40)
            if response.status_code==429:
                rate_limited.set()
                print('Wikimedia requested backoff; stopping further photo requests this run. Existing licensed assets retained.',flush=True)
                return None
            response.raise_for_status()
            if len(response.content)>10*1024*1024:return None
            from PIL import Image
            import io
            image=Image.open(io.BytesIO(response.content)).convert('RGB');image.thumbnail((640,640));image.save(target,'JPEG',quality=82)
            return filename
        except Exception as e:print('Photo failed',filename,str(e),flush=True);return None
    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:downloaded=set(pool.map(download,metadata.items()))
    used={}
    for r in recipes:
        pair=assignments.get(r['catalogKey'])
        if pair and pair[0] in downloaded:
            item=metadata[pair[0]];r.update({k:v for k,v in item.items() if k!='downloadUrl'});r['imageMatch']=pair[1];used[pair[0]]=item
    referenced={r['image'] for r in recipes if r.get('image')}
    for filename,item in previous_credits.items():
        if item['image'] in referenced:used.setdefault(filename,item)
    data['imageCoverage']={'recipesWithPhotos':sum(bool(r['image']) for r in recipes),'uniquePhotos':len(referenced),'policy':'Per-file Commons licenses checked; exact vs family illustration labeled.'}
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    (CAT/'photo_credits.json').write_text(json.dumps(used,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(data['imageCoverage'],ensure_ascii=False),flush=True)

if __name__=='__main__':main()
