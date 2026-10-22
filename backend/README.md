# AI công thức chạy miễn phí trên laptop

App Java/XML dùng SQLiteOpenHelper v4 trên điện thoại. Backend này chạy trên laptop. CLIP và TF-IDF không cần API key; tính năng hỏi AI theo công thức là tùy chọn và cần `OPENAI_API_KEY` trong env server riêng. Thư viện công thức và CRUD vẫn mở khi backend tắt. Đã thử trên Windows, Python3.11, RTX4050 6GB; inference hiện dùng CPU để cài đặt nhẹ và không cần CUDA/PyTorch.

## Chạy

Tại thư mục project:

```powershell
python -m venv backend/.venv
.\backend\.venv\Scripts\python.exe -m pip install -r backend/requirements.txt
.\backend\.venv\Scripts\python.exe backend/download_model.py
.\backend\.venv\Scripts\python.exe -m backend.train_recipe_model
.\backend\.venv\Scripts\python.exe -m uvicorn backend.app:app --host 127.0.0.1 --port 8765
```

Máy hiện tại đã có `.venv`, model ảnh và model truy hồi. Đừng tạo lại venv nếu đã có. Kiểm tra [health](http://127.0.0.1:8765/health) và [API thử nghiệm local](http://127.0.0.1:8765/docs). Nếu port báo đang dùng, server có thể vẫn đang chạy từ phiên kiểm thử; kiểm tra health trước.

Kết nối emulator hoặc điện thoại qua USB:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8765 tcp:8765
```

Trong app: **Nhập → Kết nối AI**, giữ địa chỉ `http://127.0.0.1:8765`, chọn **Lưu & kiểm tra kết nối**. Nếu không dùng ADB reverse trên emulator, có thể dùng `http://10.0.2.2:8765`.

Nếu dùng điện thoại qua Wi-Fi: chạy uvicorn với `--host 0.0.0.0`, đặt app tới `http://IP-LAN-CỦA-LAPTOP:8765`. Hai thiết bị phải cùng mạng tin cậy. Chỉ mở quyền firewall cho mạng riêng nếu Windows hỏi; không cần port-forward router. Mặc định server chỉ bind localhost, chưa có xác thực cho LAN. Bản debug cho phép HTTP để phát triển local; bản release giữ mặc định HTTPS của Android.

## AI đang làm gì

1. **Ảnh công thức có chữ:** ML Kit OCR trên điện thoại → bạn sửa chữ → parser Việt/Anh → form review → Room. Không gửi ảnh OCR tới cloud.
2. **Ảnh món ăn:** bạn chọn “Ảnh món ăn”/camera AI, hoặc ảnh chia sẻ không có chữ → app gửi JPEG thu nhỏ tới laptop → CLIP ONNX tạo embedding → so với kho ảnh món có giấy phép → trả danh sách ứng viên → bạn xác nhận món/biến thể → mở công thức nguồn đã có trong Room. Không tự đoán rằng ảnh chứng minh đúng định lượng, dị ứng hay công thức của đầu bếp gốc.
3. **Caption:** model TF-IDF tự học từ title/nguyên liệu trong dataset tìm công thức liên quan. Không có dữ liệu liên quan thì trả rỗng; không tạo công thức bịa.
4. **TikTok:** gọi **oEmbed chính thức** để lấy metadata/caption khi TikTok trả dữ liệu. Caption đưa tới model truy hồi và ô review. Không tải video/âm thanh, không vượt đăng nhập hoặc chống tải. Caption thiếu nguyên liệu/bước thì cần bạn dán nội dung hợp lệ hoặc OCR screenshot. Lượt gọi live với video ví dụ tài liệu chính thức đã không có caption; test mock chỉ chứng minh hợp đồng API, không chứng minh mọi link TikTok nhập được.

`score` là cosine similarity, **không phải xác suất đúng**. Ảnh chung nhóm được ghi nhãn trong source/credits; nhiều biến thể dùng một ảnh không được tính như nhiều bằng chứng độc lập. CLIP không được fine-tune ở phiên này. Lượt kiểm thử dùng ảnh có trong gallery là smoke test, chưa phải độ chính xác với ảnh mới.

## Các model và chi phí

| Thành phần | Loại | Tình trạng |
|---|---|---|
| CLIP ViT-B/32 quantized ONNX | Model ảnh pretrained, khoảng89MB | Đã tải, SHA256 kiểm tra theo metadata, chạy thật qua ONNX Runtime CPU |
| Recipe TF-IDF | Model truy hồi tự train từ văn bản dataset, khoảng0.8MB | Đã train; artifact JSON/NPZ trong `models/`, không dùng pickle |
| Scratch food-category model | Descriptor ảnh64×64 gradient/màu → linear SGD classifier | Có script train từ đầu và báo cáo holdout thật; chỉ thí nghiệm phân3 nhóm, không phải model nhận diện từng món |
| LLM tạo văn bản/multimodal lớn | Chưa tích hợp | Không có phí API, không có lời gọi bí mật tới model trả tiền |

Nguồn model ảnh: [ONNX conversion](https://huggingface.co/Xenova/clip-vit-base-patch32), [CLIP upstream](https://github.com/openai/CLIP). Revision và hash ở `models/clip/provenance.json`, bản ghi download trong `docs/evidence/vision-model-download.log`.

“Chuyên công thức” ở phiên bản này nghĩa là dữ liệu và đầu ra bị giới hạn vào catalog công thức có nguồn; không có khả năng chat kiến thức chung. Không có tuyên bố rằng model CLIP nền chỉ từng học ảnh đồ ăn. Train một LLM từ số0 để hiểu tiếng Việt, ảnh và nấu ăn không thực tế với ngân sách0đ/6GB VRAM; fine-tune một base model nhỏ bằng LoRA là hướng nghiên cứu tiếp theo, cần thêm dữ liệu kiểm duyệt và đánh giá. Model nhỏ miễn phí cũng không đảm bảo đúng công thức chỉ từ ảnh.

## Tự train model nhẹ từ đầu

Đã có pipeline thực thi, không dùng pretrained weights cho model này:

```powershell
.\backend\.venv\Scripts\python.exe tools/prepare_scratch_training.py
.\backend\.venv\Scripts\python.exe -m backend.train_vision_scratch datasets/training-scratch
```

Pipeline gom ảnh nguồn có license, loại file trùng SHA256, chia train/validation **trước** huấn luyện. Bộ thí nghiệm hiện tại178 ảnh độc lập theo file, phân3 nhóm `banh_che`, `mon_nuoc`, `mon_khac`; đây là nhãn thô từ tên món, cần rà soát thủ công. Xem báo cáo hiện tại tại `docs/evidence/scratch-model-report.json`; chỉ số trên holdout nhỏ không đủ để đưa ra đảm bảo thực tế. Model này là thí nghiệm phụ, không quyết định công thức trả về thay CLIP.

Để nhận diện từng món tự train: chuẩn bị thư mục một lớp cho mỗi món và `labels.json` ánh xạ tới catalogKey; cần nhiều ảnh gốc khác góc/ánh sáng/nguồn, ít nhất5 ảnh/lớp để script cho chạy nhưng cần nhiều hơn đáng kể để có model hữu ích. Không chia các bản crop/augment của cùng ảnh sang cả train và validation. Nên giữ test set độc lập theo người chụp/nguồn, thêm ảnh “không phải đồ ăn”, đánh giá nhầm giữa các món tương tự và cho model được từ chối. Script xuất `weights.npz` và `report.json`; không gắn nhãn “đã chính xác” chỉ vì training chạy xong.

## Kiểm thử

```powershell
.\backend\.venv\Scripts\python.exe -m unittest backend.test_backend -v
```

Test gồm dataset/license/path ảnh, health/text API, ảnh lỗi, host TikTok, oEmbed mock và real CLIP gallery roundtrip. App có AiLaptopIntegrationTest gọi server thật; chạy backend và ADB reverse trước khi chạy test đó. Không chạy test này với backend cloud lạ.

API:

```text
GET  /health
POST /analyze/text    {"text":"bún bò Huế"}
POST /analyze/image   multipart photo=<image>
POST /import/tiktok   {"url":"https://www.tiktok.com/@creator/video/..."}
```

Backend tạo SQLite thật tại `backend/models/catalog.sqlite3`. Bản catalog SQLite có thể mở riêng tại `datasets/recipe_catalog.sqlite3`; app dùng database riêng `myrecipelab.db` trong private storage. Không cần PostgreSQL hoặc dịch vụ thu phí cho quy mô hiện tại.


## Trợ lý công thức (tùy chọn, tính phí API riêng)

Tạo file `backend/.env` bằng cách copy `backend/.env.example`, điền `OPENAI_API_KEY` trên máy, không commit vào Git. Khởi động `.\\backend\\run.ps1` để nạp file env. Android gọi `POST /assistant/ask` qua backend; không lưu key vào APK. Nếu chưa có key, endpoint trả 503 thay vì câu trả lời giả. Xem `docs/EXTERNAL_INTEGRATIONS_AND_GATES.md`.
