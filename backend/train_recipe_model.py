"""Train a recipe-only retrieval model from this catalog; no pretrained text weights.

This is TF-IDF information retrieval, NOT a newly pretrained language model.
Artifacts use JSON/NPZ instead of executable pickle files.
"""
from pathlib import Path
import hashlib,json,time
from scipy.sparse import save_npz
from backend.engine import RecipeEngine,MODELS,CATALOG

def main():
    start=time.time();engine=RecipeEngine()
    path=CATALOG/'recipes.json';digest=hashlib.sha256(path.read_bytes()).hexdigest()
    metadata={'modelType':'TF-IDF recipe retriever trained from scratch on catalog text, not an LLM',
        'catalogSha256':digest,'recipeKeys':[r['catalogKey'] for r in engine.recipes],
        'vocabulary':{word:int(index) for word,index in engine.vectorizer.vocabulary_.items()},'idf':engine.vectorizer.idf_.tolist()}
    target=MODELS/'recipe_retriever.json';target.write_text(json.dumps(metadata,ensure_ascii=False),encoding='utf-8')
    save_npz(MODELS/'recipe_vectors.npz',engine.vectors)
    checks=[]
    for query in ['phở bò nước dùng','bún bò Huế','bánh xèo','cơm tấm','gỏi cuốn']:
        checks.append({'query':query,'top5':[r['title'] for r in engine.text(query)]})
    report={'modelType':metadata['modelType'],'recipes':len(engine.recipes),'vocabularySize':len(engine.vectorizer.vocabulary_),
        'weightsBytes':target.stat().st_size+(MODELS/'recipe_vectors.npz').stat().st_size,
        'seconds':round(time.time()-start,2),'sanityQueries':checks,
        'evaluationLimit':'Sanity queries against indexed recipes only; not held-out/generalization accuracy. No claim that all Vietnamese recipes are covered.'}
    (Path(__file__).resolve().parents[1]/'docs/evidence/ai-model-training.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
