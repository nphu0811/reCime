"""Three human-reviewed Vietnamese adaptations of CC BY-SA English Wikibooks recipes.
Original revisions cached by the collector; no invented quantities for missing fields.
"""
from pathlib import Path
import json,urllib.parse
ROOT=Path(__file__).resolve().parents[1]
translations={
'Cookbook:Phở Bò (Vietnamese Beef Noodle Soup)':{
 'title':'Phở bò','cookbook':'Món nước','servings':1,'servingsKnown':False,
 'ingredients':'2 củ hành tây trắng lớn, bổ tư\n1 muỗng canh dầu thực vật hoặc dầu lạc\n2.268 kg xương bò có dính thịt (nguồn ghi 5 lb)\n1 nhánh gừng nhỏ, băm\n1 thanh quế nhỏ\n2 quả thảo quả\n4 nụ đinh hương\n1 hoa hồi\n1 muỗng canh tiêu đen nguyên hạt\nBánh phở, ngâm mềm rồi để ráo (nguồn không ghi lượng)\nHành tây thái mỏng, hành lá và rau mùi\nThịt bò thăn thái mỏng (nguồn không ghi lượng)\nỚt, giá đỗ, chanh, húng quế\nNước mắm, tương ớt, tương đen tùy khẩu vị',
 'steps':'Phi hành tây với dầu cho hơi vàng, vớt ra để ráo.\nRửa xương bò, cho vào nồi, đổ nước lạnh ngập xương. Đun sôi chậm rồi hạ lửa, ninh không đậy nắp 10–15 phút và hớt bọt.\nCho hành đã phi, gừng, quế, thảo quả, hoa hồi, đinh hương và tiêu vào. Ninh nhẹ, hé nắp tối thiểu 6 giờ; nguồn gợi ý tối đa 12 giờ, thường xuyên hớt bọt và thêm nước để xương luôn ngập.\nLọc bỏ xương và gia vị, giữ nước dùng nóng.\nXếp bánh phở vào bát cùng thịt bò thái mỏng, thêm nước dùng nóng, hành và rau mùi. Điều chỉnh: nấu thịt chín kỹ trước khi ăn; không coi việc chan nước nóng là bảo đảm thịt đã chín.\nDọn kèm giá, chanh, húng quế, ớt và các loại nước chấm tùy thích.\nGhi chú nguồn: bước ninh gốc có nhắc tỏi nhưng danh sách nguyên liệu không ghi lượng; bản này không tự thêm định lượng tỏi.',
},
'Cookbook:Summer Rolls':{
 'title':'Gỏi cuốn tôm thịt','cookbook':'Gỏi & cuốn','servings':4,'servingsKnown':True,
 'ingredients':'4 cái bánh tráng\n4–8 con tôm\n50 g thịt lợn hoặc thịt gà đã nấu chín\n50 g bún gạo đã nấu chín\n1 quả dưa chuột, thái que\n4 lá xà lách\n2 lá cải thảo, cắt đôi theo chiều dọc\nRau mùi, húng quế, bạc hà tùy khẩu vị\nNước ấm để nhúng bánh tráng',
 'steps':'Nhúng bánh tráng vào nước ấm vài giây cho ướt hoàn toàn rồi lấy ra ngay.\nTrải bánh tráng trên đĩa hoặc thớt.\nXếp một phần nguyên liệu thành hàng gọn gần mép bánh; đặt dưa chuột song song với mép. Dùng tôm và thịt đã chín khi ăn.\nGấp mép bánh qua phần nhân rồi cuộn một vòng.\nGấp hai bên bánh vào trong để đóng hai đầu cuốn.\nCuộn hết phần bánh còn lại cho kín, để khoảng 5 phút cho bớt ướt rồi dùng.\nLặp lại với phần nguyên liệu còn lại. Biến thể chay của nguồn: bỏ tôm và thịt, thêm rau, giá hoặc cà rốt.',
},
'Cookbook:Bánh Chưng':{
 'title':'Bánh chưng','cookbook':'Bánh & chè','servings':1,'servingsKnown':False,
 'ingredients':'1 1/2 cup đậu xanh, rửa và ngâm 4 giờ hoặc qua đêm\n500 g thịt ba chỉ, cắt miếng lớn\n2 1/2 muỗng cà phê muối\n2 1/2 muỗng cà phê tiêu đen xay\n3 muỗng canh hành khô phi\n1 muỗng canh dầu thực vật\n3 củ hành tím, băm\n2 muỗng canh nước mắm\n1 kg lá chuối tươi hoặc rã đông (biến thể lá chuối theo nguồn)\n5 1/2 cup gạo nếp, rửa và ngâm qua đêm',
 'steps':'Cho đậu xanh vào nồi, đổ ngập nước, đun sôi rồi hạ lửa nấu khoảng 20 phút cho mềm.\nNghiền đậu, trộn 1 muỗng cà phê muối, hành phi, 1 muỗng cà phê tiêu và dầu thực vật.\nƯớp thịt với 1/2 muỗng cà phê muối, 1 1/2 muỗng cà phê tiêu, hành tím và nước mắm trong vài giờ.\nDàn khoảng 1 cup đậu nghiền, đặt thịt vào giữa rồi nắn thành khối tròn hoặc vuông.\nXé lá chuối thành miếng lớn khoảng 46–51 cm. Trộn gạo đã ngâm với chút muối còn lại.\nTrải hai lá chồng một phần lên nhau. Cho 1 cup gạo, đặt nhân lên, phủ thêm 1 cup gạo rồi gấp lá thật kín.\nBọc lớp lá thứ hai và buộc chắc bằng dây. Lớp trong quay mặt lá sẫm về phía gạo; lớp ngoài quay mặt sẫm ra ngoài.\nXếp bánh khít trong nồi lớn, đổ ngập nước, đun sôi rồi nấu nhỏ lửa 6–8 giờ tới khi gạo dẻo kết khối.\nTháo dây và lá, dùng sợi chỉ sạch cắt bánh khi ăn. Nguồn ghi khoảng 10–15 người ăn, không có số khẩu phần chính xác; ứng dụng giữ định lượng theo toàn mẻ.',
}}

def main():
 path=ROOT/'app/src/main/assets/catalog/recipes.json';data=json.loads(path.read_text(encoding='utf-8'))
 pages=json.loads((ROOT/'datasets/raw/en-popular-recipes.json').read_text(encoding='utf-8'))['query']['pages']
 for page in pages.values():
  if page['title'] not in translations:continue
  value=translations[page['title']];revision=page['revisions'][0]['revid'];catalog_key=f'enwikibooks:{page["pageid"]}'
  data['recipes']=[r for r in data['recipes'] if r['catalogKey']!=catalog_key]
  data['recipes'].append({**value,'catalogKey':catalog_key,'tags':'Món Việt, Wikibooks, bản dịch tiếng Việt','source':f'https://en.wikibooks.org/w/index.php?oldid={revision}',
   'authors':'English Wikibooks contributors; bản dịch/biên tập tiếng Việt MyRecipeLab','history':f'https://en.wikibooks.org/w/index.php?title={urllib.parse.quote(page["title"])}&action=history',
   'license':'CC BY-SA 4.0','licenseUrl':'https://creativecommons.org/licenses/by-sa/4.0/','revision':revision,'imageFile':'','image':'','imageAttribution':'','imageLicense':'','imageSource':'',
   'modifications':'Dịch và rút gọn sang tiếng Việt; quy đổi 5lb xương bò thành2.268kg khi có; nhắc nấu thịt chín kỹ; giữ các khoảng/định lượng nguồn chưa ghi, không tự bổ sung.'})
 path.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8');print('Total recipes',len(data['recipes']))

if __name__=='__main__':main()
