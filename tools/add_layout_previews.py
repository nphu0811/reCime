"""Add design-time data without rewriting runtime attributes or user preview values."""
from pathlib import Path
import re,json,shutil
from xml.sax.saxutils import quoteattr
from xml.dom import minidom
ROOT=Path(__file__).resolve().parents[1]
LAYOUT=ROOT/'app/src/main/res/layout'

def attributes(source,name,values):
    pattern=re.compile(r'<[\w.]+\b[^<>]*\bandroid:id="@\+id/'+re.escape(name)+r'"[^<>]*>',re.S)
    def replace(match):
        tag=match.group();tail=' />' if tag.rstrip().endswith('/>') else '>'
        base=tag.rstrip()[:-2].rstrip() if tail==' />' else tag.rstrip()[:-1].rstrip()
        offset=match.start();line_start=source.rfind('\n',0,offset)+1;indent=source[line_start:offset]+'    '
        for key,value in values.items():
            if re.search(r'\b'+re.escape(key)+r'\s*=',tag):continue
            base+='\n'+indent+key+'='+quoteattr(value)
        return base+tail
    return pattern.sub(replace,source)

samples={
 'fragment_board.xml':{'board_title':'Một tuần ngon lành','board_subtitle':'Chọn từng bữa để thêm hoặc thay món.','week_label':'19–25 tháng 10','board_action':'Tạo giỏ từ tuần này','board_secondary':'Hôm nay / tuần hiện tại','board_empty':'21 ô bữa ăn · lưu offline'},
 'item_board.xml':{'row_title':'Bún bò Huế','row_meta':'Thứ hai · Bữa trưa'},
 'fragment_detail.xml':{'detail_title':'Bún bò Huế','detail_meta':'Món nước · Wikibooks','portions':'1 mẻ công thức','detail_ingredients':'• Bún và thịt theo công thức nguồn\n• Sả, gia vị và rau ăn kèm','detail_steps':'1. Sơ chế nguyên liệu theo hướng dẫn.\n\n2. Nấu nước dùng và hoàn thiện món.','nutrition':'Dinh dưỡng: chưa đủ dữ liệu','source':'Wikibooks · CC BY-SA 4.0\nTác giả và giấy phép ảnh có trong chi tiết nguồn.'},
 'fragment_editor.xml':{'edit_title':'Gỏi cuốn tôm thịt','edit_book':'Món nhà','edit_tags':'gỏi, cuốn, món Việt','edit_servings':'4','edit_ingredients':'4 cái bánh tráng\n50 g bún đã nấu chín\nTôm và thịt đã chín theo nguồn','edit_steps':'Chuẩn bị nguyên liệu.\nCuốn bánh tráng và dùng cùng nước chấm.','edit_source':'Wikibooks / công thức gia đình'},
 'item_recipe.xml':{'title':'Bún bò Huế','meta':'Wikibooks · CC BY-SA 4.0'},
}
contexts={'fragment_board.xml':'.fragment.BoardFragment','fragment_detail.xml':'.fragment.DetailFragment','fragment_editor.xml':'.fragment.EditorFragment','fragment_import.xml':'.fragment.ImportFragment','fragment_recipes.xml':'.fragment.RecipesFragment','fragment_ai_settings.xml':'.fragment.AiSettingsFragment','activity_main.xml':'.activity.MainActivity'}

def main():
    for path in LAYOUT.glob('*.xml'):
        source=path.read_text(encoding='utf-8')
        if 'xmlns:tools=' not in source:
            source=source.replace('xmlns:android="http://schemas.android.com/apk/res/android"','xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:tools="http://schemas.android.com/tools"',1)
        if path.name in contexts:
            root=re.search(r'<[\w.]+\b[^<>]*>',source,re.S)
            if root and 'tools:context=' not in root.group():
                source=source[:root.end()-1]+'\n    tools:context="'+contexts[path.name]+'">'+source[root.end():]
        for name,text in samples.get(path.name,{}).items():source=attributes(source,name,{'tools:text':text})
        for name,item in [('board_list','item_board'),('list','item_recipe')]:source=attributes(source,name,{'tools:listitem':'@layout/'+item,'tools:itemCount':'4'})
        for name in ['photo','hero_photo']:source=attributes(source,name,{'tools:src':'@drawable/preview_bun_bo_hue'})
        minidom.parseString(source)
        path.write_text(source,encoding='utf-8')
    data=json.loads((ROOT/'app/src/main/assets/catalog/recipes.json').read_text(encoding='utf-8'))
    recipe=next(r for r in data['recipes'] if r['title']=='Bún bò Huế' and r.get('image'))
    target=ROOT/'app/src/main/res/drawable-nodpi/preview_bun_bo_hue.jpg';target.parent.mkdir(exist_ok=True)
    shutil.copyfile(ROOT/'app/src/main/assets/catalog'/recipe['image'],target)
    (ROOT/'docs/evidence/preview-photo-credit.json').write_text(json.dumps({k:recipe.get(k,'') for k in ['title','image','imageAttribution','imageLicense','imageLicenseUrl','imageSource']},ensure_ascii=False,indent=2),encoding='utf-8')
    print('Added preview-only tools attributes; existing user-supplied preview text retained.')

if __name__=='__main__':main()
