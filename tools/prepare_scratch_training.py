"""Build a small, source-deduplicated food-category training set from licensed photos.
This is a coarse three-category experiment, not a named-dish recognition benchmark.
"""
from pathlib import Path
import hashlib,json,re,shutil,unicodedata
ROOT=Path(__file__).resolve().parents[1]
CAT=ROOT/'app/src/main/assets/catalog'
OUT=ROOT/'datasets/training-scratch'

def key(value):return ''.join(c for c in unicodedata.normalize('NFD',value.lower().replace('đ','d')) if not unicodedata.combining(c))

def main():
    recipes=json.loads((CAT/'recipes.json').read_text(encoding='utf-8'))['recipes'];seen=set();records=[];labels={}
    old_records=json.loads((OUT/'credits.json').read_text(encoding='utf-8')) if (OUT/'credits.json').exists() else []
    for r in recipes:
        if not r.get('image'):continue
        path=CAT/r['image'];digest=hashlib.sha256(path.read_bytes()).hexdigest()
        if digest in seen:continue
        seen.add(digest);name=key(r['title'])
        if re.match(r'^(bun|pho|canh|lau|hu tieu|mi |banh canh|chao)',name):label='mon_nuoc'
        elif re.match(r'^(banh|che |kem|xoi)',name):label='banh_che'
        else:label='mon_khac'
        dest=OUT/label/(digest+'.jpg');dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,dest)
        labels.setdefault(label,[]).append(r['catalogKey'])
        records.append({'file':str(dest.relative_to(OUT)),'sha256':digest,'recipeKey':r['catalogKey'],'label':label,'source':r['imageSource'],'license':r['imageLicense'],'author':r['imageAttribution']})
    current={r['file'] for r in records}
    for old in old_records:
        stale=OUT/old['file']
        if old['file'] not in current and stale.resolve().is_relative_to(OUT.resolve()) and stale.is_file():stale.unlink()
    (OUT/'labels.json').write_text(json.dumps(labels,ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/'credits.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')
    print('Distinct source photos',len(records),'category sizes',{k:len(v) for k,v in labels.items()})

if __name__=='__main__':main()
