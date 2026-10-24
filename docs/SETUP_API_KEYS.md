# Cấu hình reCime độc lập

Không cần key để dùng thư viện, Editor/ảnh, JSON-LD web, OCR, Cookbooks, Planner, Groceries, Cooking, PDF, backup tệp hoặc tài khoản email/password trên backend riêng.

Từ root repository (PowerShell):

```powershell
python -m venv backend/.venv
backend/.venv/Scripts/python.exe -m pip install -r backend/requirements.txt
if (-not (Test-Path backend/.env)) { Copy-Item backend/.env.example backend/.env }
backend/.venv/Scripts/python.exe -m uvicorn backend.app:app --host 127.0.0.1 --port 8765
```

Backend đọc `backend/.env` khi khởi động; biến process environment được ưu tiên. Chạy từ repository root để đường dẫn SQLite/catalog đúng. `.env`, database, models và outputs được gitignore. Không đặt server key trong Gradle/APK hoặc gửi key vào chat.

| Biến được code sử dụng | Giá trị/mục đích | Cần làm |
|---|---|---|
| APP_ENV | development hoặc production | production bắt buộc session cho các API AI/USDA/import trả phí |
| DATABASE_URL | sqlite:///backend/data/recime.db | SQLite có WAL/FK, migration server v1; bảo vệ thư mục dữ liệu |
| ALLOW_REGISTRATION | true/false | Tắt đăng ký khi chỉ dùng riêng; không có email verification/password reset |
| OPENAI_API_KEY | key server của bạn | Chỉ để trống nếu không dùng AI từ xa; thiếu key trả 503 rõ ràng |
| OPENAI_MODEL | gpt-4.1-mini mặc định, model hỗ trợ Responses structured output và image input | Mặc định gpt-4.1-mini đã smoke test live text/ảnh/Q&A trong phiên này; model khác cần kiểm tra riêng |
| FDC_API_KEY | key USDA FoodData Central của bạn | Đăng ký tại [USDA](https://fdc.nal.usda.gov/api-key-signup.html); thiếu key không tạo số dinh dưỡng |

`JWT_SECRET`, Google OAuth/SMTP/YouTube secrets không có trong template vì code hiện chưa dùng. Auth hiện dùng scrypt cho mật khẩu và session ngẫu nhiên có hạn 7 ngày; server chỉ lưu SHA-256 của bearer token. APK dùng Android Keystore mã hóa session. Logout offline chỉ thu hồi local; server token vẫn có thể dùng tới khi hết hạn. Không có Google Login giả.

Trên Emulator/USB:

```powershell
& "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" reverse tcp:8765 tcp:8765
```

Mở ⚙ trong Recipes → backend URL `http://127.0.0.1:8765` → kiểm tra kết nối. Emulator cũng dùng được `http://10.0.2.2:8765`. HTTP chỉ được client account/AI chấp nhận cho debug localhost/10.0.2.2. Thiết bị thật qua mạng cần HTTPS riêng; release không cho cleartext. URL/backend OAuth client IDs là public configuration; không phải API secret. Chưa có Google client ID được sử dụng.

Tạo tài khoản email/password trên chính backend này (mật khẩu ≥10 ký tự). Không dùng tài khoản ReciMe official. `/health` chỉ báo key đã cấu hình, **không chứng minh provider trả lời thành công**. Tài khoản và cloud backup không cần OpenAI/USDA keys. Cloud backup tối đa 8MB; backup tệp 32MB.

Model ảnh local là tra cứu CLIP catalog, cần model tải hợp pháp bằng công cụ `backend/download_model.py` đang có. Đây là tìm công thức có nguồn tương tự ảnh, không phải trích nguyên liệu từ ảnh món ăn. OCR Latin chạy trên thiết bị; dấu tiếng Việt cần kiểm tra thủ công. TikTok chỉ đọc caption/oEmbed công khai; các nền tảng khác yêu cầu dán caption/transcript/ảnh do bạn có quyền sử dụng.

API thực tế có OpenAPI tại `/docs`: legacy AI/USDA vẫn tương thích; `/v1/auth/*`, `/v1/account`, `/v1/backup`, `/v1/sync/push`, `/v1/sync/pull`, `/v1/discover`, `/v1/import/ai-text`, `/v1/import/ai-image`. Record sync đã có server contract, chưa nối tự động với SQLite Android; UI ghi rõ cloud backup thủ công.

Contract OpenAI đã đối chiếu [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs) (`text.format`, JSON schema strict) và [model GPT-4.1 mini](https://developers.openai.com/api/docs/models/gpt-4.1-mini). Ngày 24/10/2026 đã smoke test HTTP200 bằng key do người dùng cấu hình: OpenAI trích văn bản, đọc ảnh công thức tiếng Việt tự tạo và Q&A; USDA trả candidate cho 100 g rice raw. Đây là kiểm tra kết nối/schema trên ví dụ nhỏ, không chứng nhận accuracy mọi công thức. Hướng dẫn image input: [Images and vision](https://developers.openai.com/api/docs/guides/images-vision).

Import ảnh AI: chọn SCAN CHỮ/Camera để có ảnh và OCR local → “Đọc ảnh đã chọn bằng OpenAI” → xác nhận Gửi ảnh → sửa văn bản → Review/Editor → Lưu. Ảnh chỉ được gửi sau xác nhận, được chuẩn hướng EXIF/ảnh lật, thu nhỏ và bỏ metadata; backend giới hạn 8MB/40MP, không suy công thức từ hình món ăn. OpenAI có thể tính phí và áp dụng chính sách dữ liệu riêng. Nếu không có chữ đọc được, trả lỗi thật; OCR/nháp vẫn dùng được.
