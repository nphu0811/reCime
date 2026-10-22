"""Source-linked USDA FoodData Central lookup; returns no fake nutrition for unknown items."""
import os,re
import requests

AMOUNT=re.compile(r'^\s*(\d+(?:[.,]\d+)?)\s*(kg|g)\s+(.+?)\s*$',re.I)
NUTRIENTS={'Energy':'calories','Protein':'protein',
           'Carbohydrate, by difference':'carbs','Total lipid (fat)':'fat'}
VI_TO_EN={
 'gao':'rice raw','gaọ':'rice raw',
 'ca chua':'tomatoes raw',
 'trung ga':'egg whole raw',
 'sua tuoi':'milk whole',
 'duong':'sugar granulated'
}

def estimate(ingredients,servings,key=None):
    key=(key if key is not None else os.getenv('FDC_API_KEY','')).strip()
    if not key:raise RuntimeError('FDC_API_KEY chưa được cấu hình trên backend.')
    rows=[line.strip() for line in ingredients.splitlines() if line.strip()]
    if len(rows)>18:raise ValueError('Tối đa 18 nguyên liệu mỗi lần.')
    if not rows:raise ValueError('Danh sách nguyên liệu trống.')
    if not 0<servings<=10000:raise ValueError('Khẩu phần phải là số dương.')
    matched=[];missing=[];totals={key:0.0 for key in NUTRIENTS.values()}
    for line in rows:
        amount=AMOUNT.match(line)
        if not amount:
            missing.append({'input':line,'reason':'Cần gram hoặc kg; không tự suy diễn quả, muỗng hay ml.'})
            continue
        grams=float(amount.group(1).replace(',','.'))*(1000 if amount.group(2).lower()=='kg' else 1)
        if not 0<grams<=20000:
            missing.append({'input':line,'reason':'Khối lượng ngoài khoảng hỗ trợ.'})
            continue
        query=amount.group(3).strip()
        import unicodedata
        ascii_key=''.join(c for c in unicodedata.normalize('NFD',query.lower())
                          if unicodedata.category(c)!='Mn').replace('đ','d')
        query=VI_TO_EN.get(ascii_key,query)
        resp=requests.get('https://api.nal.usda.gov/fdc/v1/foods/search',
          params={'api_key':key,'query':query,'pageSize':5,'dataType':'Foundation,SR Legacy'},
          timeout=18)
        if resp.status_code!=200:raise RuntimeError('USDA FoodData Central HTTP '+str(resp.status_code))
        foods=resp.json().get('foods',[])
        if not foods:
            missing.append({'input':line,'reason':'USDA không tìm được nguyên liệu tương ứng.'})
            continue
        food=foods[0]
        byname={n.get('nutrientName'):n for n in food.get('foodNutrients',[])}
        values={}
        for name,label in NUTRIENTS.items():
            nutrient=byname.get(name)
            if nutrient is None or nutrient.get('value') is None:
                values={};break
            values[label]=float(nutrient['value'])*grams/100
        if len(values)!=4:
            missing.append({'input':line,'reason':'USDA thiếu một hoặc nhiều chỉ số bắt buộc.'})
            continue
        matched.append({'input':line,'fdcId':food.get('fdcId'),
           'description':food.get('description'),
           'source':'https://fdc.nal.usda.gov/food-details/'+str(food.get('fdcId'))+'/nutrients',
           'grams':grams,'totals':values})
        for label,value in values.items():totals[label]+=value
    complete=bool(matched) and not missing
    return {'complete':complete,'matched':matched,'missing':missing,
      'perServing':({label:round(value/servings,2) for label,value in totals.items()}
                    if complete else None),
      'source':'USDA FoodData Central',
      'warning':'Số ước tính chỉ đáng dùng sau khi kiểm tra nguyên liệu USDA được ghép và khẩu phần. '
                'Không sử dụng làm khẳng định y tế, dị ứng hoặc an toàn thực phẩm.'}
