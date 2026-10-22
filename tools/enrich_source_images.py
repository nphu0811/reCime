"""Recover directly cited photo filenames from cached source revisions, no network."""
from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parents[1]
path=ROOT/'app/src/main/assets/catalog/recipes.json';data=json.loads(path.read_text(encoding='utf-8'))
by_key={r['catalogKey']:r for r in data['recipes']};count=0
for file in (ROOT/'datasets/raw').glob('revisions-*.json'):
    for page in json.loads(file.read_text(encoding='utf-8')).get('query',{}).get('pages',{}).values():
        key=f'viwikibooks:{page["pageid"]}'
        if key not in by_key:continue
        raw=page['revisions'][0]['slots']['main']['*']
        images=re.findall(r'\[\[(?:Image|File|Tập tin|Hình):([^\]|]+\.(?:jpe?g|png|webp))',raw,re.I)
        if images:by_key[key]['imageFile']=images[0];count+=1
path.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
print('Recipes with directly cited source photos',count)
