"""Reproducible collection from the Vietnamese Wikibooks Cooking namespace.

No social/private scraping. Content retains revision, authors/history and CC BY-SA.
Recipes lacking ingredient and method sections are excluded, never fabricated.
"""
from pathlib import Path
import argparse, concurrent.futures, hashlib, html, json, re, time, unicodedata
from urllib.parse import quote
import requests

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / 'datasets/raw'
OUT = ROOT / 'app/src/main/assets/catalog'
API = 'https://vi.wikibooks.org/w/api.php'
UA = 'MyRecipeLab/2.0 (educational open-source recipe catalog; respectful MediaWiki API client)'

def key(text):
    return ''.join(c for c in unicodedata.normalize('NFD', text.lower().replace('đ','d')) if not unicodedata.combining(c))

def api(params, cache_name):
    RAW.mkdir(parents=True,exist_ok=True)
    file = RAW / cache_name
    if file.exists(): return json.loads(file.read_text(encoding='utf-8'))
    for attempt in range(4):
        try:
            response=requests.get(API,params={'format':'json',**params},headers={'User-Agent':UA},timeout=45)
            if response.status_code==429:
                delay=min(60,max(15,int(response.headers.get('Retry-After','30'))))
                print(f'HTTP429; respecting Retry-After/backoff {delay}s',flush=True)
                time.sleep(delay)
            response.raise_for_status();data=response.json()
            if 'error' in data: raise ValueError(data['error'])
            file.write_text(json.dumps(data,ensure_ascii=False),encoding='utf-8')
            time.sleep(1)
            return data
        except Exception:
            if attempt==3: raise
            time.sleep(2**attempt)

def clean(s):
    s=re.sub(r'<ref\b[^>]*>.*?</ref>', '',s,flags=re.S|re.I)
    s=re.sub(r'<ref\b[^>]*/>', '',s,flags=re.I)
    s=re.sub(r'<!--.*?-->', '',s,flags=re.S)
    s=re.sub(r'\[\[(?:Thể loại|Category|Tập tin|File|Image):[^\]]*\]\]', '',s,flags=re.I)
    for _ in range(8): s=re.sub(r'\{\{[^{}]*\}\}', '',s)
    s=re.sub(r'\[\[([^\]|]+)\|([^\]]+)\]\]',lambda m:m[2],s)
    s=re.sub(r'\[\[([^\]]+)\]\]',lambda m:m[1].split(':')[-1],s)
    s=re.sub(r'\[https?://[^\s\]]+\s*([^\]]*)\]',lambda m:m[1],s)
    s=re.sub(r'<[^>]+>', '',s).replace("'''",'').replace("''",'')
    lines=[]
    for line in html.unescape(s).splitlines():
        line=re.sub(r'^[*#:;\s]+','',line).strip()
        if line and not line.startswith(('[[','{{','|','==')): lines.append(line)
    return '\n'.join(lines).strip()

def parse(page):
    revisions=page.get('revisions',[])
    if not revisions:return None
    rev=revisions[0];raw=rev['slots']['main']['*']
    if re.match(r'\s*#(?:redirect|đổi)',raw,re.I):return None
    if re.search(r'\{\{\s*(?:vi phạm bản quyền|copyvio|chờ xóa)',raw,re.I):return None
    sections=re.split(r'^\s*(={2,5})\s*(.+?)\s*\1\s*$',raw,flags=re.M)
    ingredients=[];steps=[];phase=None
    for i in range(1,len(sections)-2,3):
        heading=key(sections[i+1]).strip(' :')
        body=clean(sections[i+2])
        if any(x in heading for x in ['nguyen lieu','thanh phan','vat lieu']):phase='ingredients'
        elif any(x in heading for x in ['cach lam','thuc hien','che bien','cach nau','cach che bien','cach thuc hien']):phase='steps'
        elif any(x in heading for x in ['tham khao','lien ket','chu thich','dinh duong','trinh bay','yeu cau','luu y']):phase=None
        if body and phase=='ingredients': ingredients.extend(body.splitlines())
        elif body and phase=='steps': steps.extend(body.splitlines())
    if len(ingredients)<2 or len(' '.join(steps))<45:return None
    name=page['title'].split(':',1)[1]
    kind=re.search(r'\|\s*thể loại\s*=\s*([^\n|}]+)',raw,re.I)
    category=clean(kind[1]) if kind else ''
    k=key(name)
    if not category:
        category='Món nước' if k.startswith(('bun','pho','hu tieu','mi ','banh canh','lau','canh')) else 'Bánh & chè' if k.startswith(('banh','che','xoi')) else 'Món chay' if 'chay' in k else 'Món mặn'
    servings=re.search(r'\|\s*số người ăn\s*=\s*(\d+)',raw,re.I)
    picture=re.search(r'\|\s*hình\s*=\s*([^\n|}]+)',raw,re.I)
    image_file=clean(picture[1]).strip() if picture else ''
    inline_images=re.findall(r'\[\[(?:Image|File|Tập tin|Hình):([^\]|]+\.(?:jpe?g|png|webp))',raw,re.I)
    if inline_images:image_file=inline_images[0]
    # Preserve the entire recipe when serving count was absent; do not invent servings.
    return {'catalogKey':f'viwikibooks:{page["pageid"]}', 'title':name,'ingredients':'\n'.join(ingredients),'steps':'\n'.join(steps),
        'servings':int(servings[1]) if servings else 1,'servingsKnown':bool(servings),'cookbook':category,
        'tags':f'Wikibooks, {category}', 'source':f'https://vi.wikibooks.org/w/index.php?oldid={rev["revid"]}',
        'sourcePage':f'https://vi.wikibooks.org/wiki/{quote(page["title"].replace(" ","_"))}',
        'authors':'Cộng tác viên Wikibooks tiếng Việt', 'history':f'https://vi.wikibooks.org/w/index.php?title={quote(page["title"])}&action=history',
        'license':'CC BY-SA 4.0','licenseUrl':'https://creativecommons.org/licenses/by-sa/4.0/','revision':rev['revid'],
        'imageFile':image_file,'image':'','imageAttribution':'','imageLicense':'','imageSource':'',
        'modifications':'Tách mục nguyên liệu/cách làm, bỏ markup wiki; không thêm định lượng còn thiếu.'}

def main():
    pages=[];continuation={};index=0
    while True:
        data=api({'action':'query','list':'allpages','apnamespace':106,'aplimit':500,'apfilterredir':'nonredirects',**continuation},f'inventory-{index}.json')
        pages.extend(data['query']['allpages']);index+=1
        if 'continue' not in data:break
        continuation=data['continue']
    print('Inventory:',len(pages),flush=True)
    recipes=[]
    for start in range(0,len(pages),40):
        ids='|'.join(str(p['pageid']) for p in pages[start:start+40])
        digest=hashlib.sha256(ids.encode()).hexdigest()[:12]
        try:batch=api({'action':'query','pageids':ids,'prop':'revisions','rvprop':'ids|timestamp|content','rvslots':'main'},f'revisions-{digest}.json')
        except Exception as e:
            print('Collection paused; keeping verified partial catalog:',str(e),flush=True)
            break
        for page in batch['query']['pages'].values():
            recipe=parse(page)
            if recipe:recipes.append(recipe)
        print(f'{min(start+40,len(pages))}/{len(pages)} pages; {len(recipes)} complete recipes',flush=True)
    recipes.sort(key=lambda r:key(r['title']))
    OUT.mkdir(parents=True,exist_ok=True)
    document={'version':2,'collectedAt':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'source':API,'license':'CC BY-SA 4.0',
        'scope':'Công thức tiếng Việt trong namespace Nấu ăn của Wikibooks; bao gồm món Việt và một số món quốc tế. Không khẳng định đầy đủ mọi món ăn Việt Nam.',
        'inventoryCount':len(pages),'recipes':recipes}
    (OUT/'recipes.json').write_text(json.dumps(document,ensure_ascii=False,indent=2),encoding='utf-8')
    print('Saved',len(recipes),'recipes to',OUT/'recipes.json',flush=True)

if __name__=='__main__':main()
