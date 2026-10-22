"""Run: python -m uvicorn backend.app:app --host 127.0.0.1 --port 8765"""
import os,re,urllib.parse
from functools import lru_cache
import requests
from fastapi import FastAPI,UploadFile,File,HTTPException
from pydantic import BaseModel,Field
from backend.engine import RecipeEngine

app=FastAPI(title='reCime AI service',version='3.0')

@lru_cache(maxsize=1)
def engine():return RecipeEngine()

class TextInput(BaseModel):text:str=Field(min_length=2,max_length=16000)
class LinkInput(BaseModel):url:str=Field(min_length=8,max_length=2048)
class NutritionInput(BaseModel):
    ingredients:str=Field(min_length=2,max_length=12000)
    servings:float=Field(gt=0,le=10000)

class AskInput(BaseModel):
    question:str=Field(min_length=2,max_length=1500)
    title:str=Field(min_length=1,max_length=300)
    ingredients:str=Field(min_length=1,max_length=12000)
    steps:str=Field(min_length=1,max_length=12000)

@app.get('/health')
def health():
    e=engine()
    return {'status':'ready','recipes':len(e.recipes),'assistantAvailable':bool(os.getenv('OPENAI_API_KEY')),'visionModelDownloaded':e.vision_available(),'cost':'local, no API fee','engine':'CLIP ONNX image retrieval + recipe-only TF-IDF','privacy':'Images processed in memory; uploads not logged or saved.'}

@app.post('/analyze/text')
def text(body:TextInput):
    return {'candidates':engine().text(body.text),'warning':'Kết quả lấy từ dataset có nguồn; không tạo định lượng còn thiếu.'}

@app.post('/analyze/image')
def image(photo:UploadFile=File(...)):
    content=photo.file.read(8*1024*1024+1)
    if len(content)>8*1024*1024:raise HTTPException(413,'Ảnh tối đa8MB')
    try:return engine().image(content)
    except FileNotFoundError as e:raise HTTPException(503,str(e))
    except (ValueError,OSError) as e:raise HTTPException(422,str(e))

def tiktok_caption(value):
    parsed=urllib.parse.urlsplit(value)
    allowed={'www.tiktok.com','tiktok.com','vm.tiktok.com','vt.tiktok.com'}
    if parsed.scheme!='https' or parsed.hostname not in allowed or parsed.username or parsed.port not in (None,443):
        raise ValueError('Chỉ nhận link HTTPS TikTok chính thức')
    url=value
    for _ in range(4):
        parsed=urllib.parse.urlsplit(url)
        if parsed.scheme!='https' or parsed.hostname not in allowed:raise ValueError('Chuyển hướng ngoài TikTok không được hỗ trợ')
        if '/video/' in parsed.path:break
        response=requests.get(url,allow_redirects=False,timeout=15,headers={'User-Agent':'MyRecipeLab/2.0'})
        if response.is_redirect:url=urllib.parse.urljoin(url,response.headers['Location'])
        else:break
    if '/video/' not in urllib.parse.urlsplit(url).path:raise ValueError('Không tìm được URL video công khai; dán link đầy đủ hoặc caption.')
    response=requests.get('https://www.tiktok.com/oembed',params={'url':url},timeout=20)
    if response.status_code!=200:raise ValueError(f'TikTok không cung cấp oEmbed (HTTP{response.status_code}). Hãy dán caption hoặc screenshot có quyền sử dụng.')
    data=response.json();caption=str(data.get('title','')).strip()
    if not caption:raise ValueError('oEmbed không có caption; hãy dán văn bản hoặc dùng OCR.')
    return {'caption':caption,'canonicalUrl':url,'author':data.get('author_name',''),'provider':'TikTok official oEmbed','warning':'Chỉ đọc metadata/caption công khai. Không tải video, âm thanh hoặc vượt đăng nhập; caption có thể không chứa công thức đầy đủ.'}

@app.post('/import/tiktok')
def tiktok(body:LinkInput):
    try:
        result=tiktok_caption(body.url);result['candidates']=engine().text(result['caption']);return result
    except (ValueError,requests.RequestException) as e:raise HTTPException(422,str(e))


@app.post('/assistant/ask')
def ask(body:AskInput):
    # Optional, real remote AI. Key is server-side only; never send it to Android.
    key=os.getenv('OPENAI_API_KEY','').strip()
    if not key:raise HTTPException(503,'Chưa cấu hình OPENAI_API_KEY trong backend. Không sử dụng AI giả.')
    model=os.getenv('OPENAI_MODEL','gpt-4.1-mini')
    instructions=(
      'Bạn là trợ lý nấu ăn reCime, chỉ dùng công thức được gửi để giải thích. '
      'Nếu thiếu dữ liệu thì nói rõ không biết. Không bịa nhiệt độ an toàn, '
      'dị ứng hay giá trị dinh dưỡng. Không tự tuyên bố recipe an toàn cho người dị ứng.')
    payload={
      'model':model,'store':False,'max_output_tokens':500,'instructions':instructions,
      'input':('Tên món: '+body.title+'\nNguyên liệu:\n'+body.ingredients+
               '\nCác bước:\n'+body.steps+'\nCâu hỏi: '+body.question)
    }
    try:
      response=requests.post('https://api.openai.com/v1/responses',
          headers={'Authorization':'Bearer '+key,'Content-Type':'application/json'},
          json=payload,timeout=55)
      if response.status_code!=200:
        raise HTTPException(502,'Nhà cung cấp AI lỗi HTTP '+str(response.status_code))
      data=response.json()
      parts=[]
      for item in data.get('output',[]):
        for c in item.get('content',[]):
          if c.get('type')=='output_text':parts.append(c.get('text',''))
      answer='\n'.join(parts).strip()
      if not answer:raise HTTPException(502,'AI không trả lời được. Bạn có thể thử lại.')
      return {'answer':answer,'model':model,'grounding':'user-supplied-recipe',
              'warning':'Câu trả lời AI cần được kiểm tra, nhất là dị ứng và an toàn thực phẩm.'}
    except requests.RequestException:
      raise HTTPException(503,'Không kết nối được tới nhà cung cấp AI.')


@app.post('/nutrition/estimate')
def estimate_nutrition(body:NutritionInput):
    from backend.nutrition import estimate
    try:return estimate(body.ingredients,body.servings)
    except ValueError as e:raise HTTPException(422,str(e))
    except RuntimeError as e:raise HTTPException(503,str(e))
    except requests.RequestException:raise HTTPException(503,'Không truy cập được USDA FoodData Central.')
