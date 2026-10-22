# Dataset, database và AI — bản2

## Phạm vi dữ liệu thật

Đã thu thập656 trang không redirect trong namespace106 **Nấu ăn** của Wikibooks tiếng Việt qua MediaWiki API. Sàng lọc yêu cầu có cả phần nguyên liệu và cách làm, loại bài thiếu nội dung: thu được544 công thức. Thêm3 bản dịch/biên tập có nguồn từ English Wikibooks: phở bò, gỏi cuốn tôm thịt, bánh chưng. **Tổng547 công thức** trong `app/src/main/assets/catalog/recipes.json`.

Nguồn có món Việt và một số món quốc tế viết bằng tiếng Việt. Đây là tập khởi đầu có thể mở rộng, **không phải tất cả món ăn tại Việt Nam**, chưa được chuyên gia bếp kiểm định là công thức chuẩn duy nhất. Những món phổ biến vẫn có nhiều biến thể vùng miền; không hợp nhất chúng thành một “công thức đúng tuyệt đối”.

Ảnh thật: **178 file JPEG Commons được tái sử dụng cho211 công thức**. Một số ảnh dùng chung nhóm món, đánh dấu `imageMatch`;336 công thức chưa có ảnh hợp lệ thì giữ minh họa trống riêng, không gán ảnh món khác. Ảnh và chữ có giấy phép riêng: text CC BY-SA4.0; ảnh lưu tác giả, giấy phép từng file và URL mô tả Commons. Nguồn bài/revision/history/modification được hiển thị ở cuối màn chi tiết.

Không sao chép dữ liệu ReciMe hay ảnh quảng cáo đầu vào. Không lấy ảnh tìm kiếm không rõ quyền. Bộ sưu tập ảnh có thể có sai sót nguồn/nhãn; cần rà soát thủ công, đặc biệt khi dùng cho training. Đây là dataset ứng dụng, chưa phải benchmark nhận diện món ăn đã kiểm duyệt.

## Tái tạo và mở rộng

```powershell
python -X utf8 tools/collect_open_recipes.py
# Script bổ sung dùng snapshot en-popular-recipes.json đã thu từ API;
# xem nguồn/revision trong các record được xuất.
python -X utf8 tools/add_translated_staples.py
python -X utf8 tools/enrich_source_images.py
.\backend\.venv\Scripts\python.exe -X utf8 tools/collect_food_photos.py
```

`datasets/raw/` giữ cache API trên máy này và không commit toàn bộ. Catalog JSON/ảnh/credits/SQLite được bàn giao trực tiếp. Collector có backoff/cache; downloader ảnh hiện dừng các yêu cầu tiếp theo khi Wikimedia trả429. Lượt thu có một số429 và file không có license đọc được; các trường hợp đó không được coi là tải thành công. Không lặp mãi hoặc đổi nguồn để vượt giới hạn truy cập.

Thêm nguồn mới cần giấy phép sử dụng rõ, recipe schema hợp lệ, authors/source/license và quy trình review. Không dùng AI tự tạo hàng nghìn công thức rồi gọi chúng là dữ liệu lấy từ wiki. Nếu nguồn không ghi khẩu phần thì lưu `servingsKnown=false`, baseline1 **mẻ công thức**, không tự bịa số người ăn. Khoảng như `4–8 con tôm` giữ nguyên để review, không lấy số4 làm lượng chính xác để scale.

## Database và chi phí

- **Điện thoại:** Room/SQLite thật tại private storage `databases/myrecipelab.db`. Schema2, recipes/meals/groceries, unique catalogKey nullable cho món người dùng, foreign key cascade, migration1→2. Import theo transaction, chống chèn trùng; nâng dataset bổ sung ảnh còn thiếu và giữ sửa đổi/yêu thích của người dùng ở bản đã có.
- **Catalog độc lập:** `datasets/recipe_catalog.sqlite3`,547 dòng, `PRAGMA integrity_check` trả `ok`. Có thể mở bằng DB Browser for SQLite hoặc công cụ SQLite bất kỳ, không cần chạy app.
- **Laptop backend:** tạo `backend/models/catalog.sqlite3` khi khởi động, bên cạnh index/model riêng. Không cần server database khác để dùng app.

SQLite đủ cho quy mô này và miễn phí, không cần PostgreSQL/cloud subscription. Khi thư viện lớn hơn nhiều, nên thêm Room FTS và paging trước khi đổi loại database. Đồng bộ nhiều tài khoản/thiết bị sẽ là bài toán riêng; chưa thêm dịch vụ cloud hay khoản phí nào.

## Luồng AI thực thi

**Ảnh chữ:** Photo Picker/camera/share → bản sao private có giới hạn dung lượng → ML Kit OCR bundled → sửa text → parserVI/EN → formreview → Room.

**Ảnh món ăn:** explicit AI picker/camera hoặc ảnh share rất ít chữ → JPEG thu nhỏ → laptop local FastAPI → CLIP quantized ONNX embedding → similarity với ảnh catalog → nhiều ứng viên → người dùng chọn/khước từ → mở công thức nguồn trong Room. Không dùng ảnh để khẳng định biết chắc nguyên liệu ẩn hoặc định lượng của món đã chụp.

**TikTok:** HTTPS host chính thức → official oEmbed metadata/caption khi có → recipe-only retrieval → caption được giữ trong ô review. Lượt live dùng video ví dụ từ tài liệu TikTok không có caption; adapter đã có error/fallback. Mock test xác minh endpoint/hợp đồng, không chứng minh tải TikTok ngoài thực tế luôn thành công. Không có tải audio/video hoặc vượt đăng nhập.

## Model và huấn luyện — phân biệt rõ

| Model | Đã làm | Giới hạn |
|---|---|---|
| CLIP ViT-B/32 ONNX quantized | Đã tải89,117,001bytes, chạy CPU, gọi thật từ Android | Pretrained general vision; chưa fine-tune món Việt, chưa có accuracy ảnh ngoài gallery |
| Recipe TF-IDF | Tự train từ547 recipe,13,098 features,792,809bytes JSON/NPZ | Model truy hồi, không phải LLM, không sinh công thức hoặc hiểu hội thoại tổng quát |
| Scratch image classifier | Đã train từ đầu178 ảnh riêng biệt theo SHA256,3 nhóm món;133 train/45 validation | Chỉ nhóm thô; validation46.67%, balanced40%, thấp hơn majority baseline53.33%; **không phê duyệt dùng để chọn công thức** |

Model scratch dùng descriptor gradient/màu64×64 và linearSGD, không có pretrained weights. Đây là kết quả thực nghiệm chưa đạt, được giữ để bạn học và thay dataset/model; không đưa nó vào quyết định chọn món của app. CLIP retrieval là đường nhận diện đang hoạt động.

Không tuyên bố đã train một LLM chỉ biết công thức từ số0. Với tài nguyên laptop6GB VRAM và ngân sách không trả API, hướng đang triển khai là model ảnh nhẹ + truy hồi có nguồn. Nếu sau này cần diễn giải công thức bằng LLM, có thể đánh giá base model nhỏ miễn phí và LoRA trên dữ liệu đã kiểm duyệt; cần test riêng, không chỉ thêm system prompt rồi gọi là model chuyên biệt đã train.

## Nguồn và bằng chứng

- [Wikibooks tiếng Việt](https://vi.wikibooks.org/), API siteinfo xác nhận namespace106 và CC BY-SA4.0; exact revision trong từng record.
- [Commons reuse](https://commons.wikimedia.org/wiki/Commons:Reusing_content_outside_Wikimedia): giữ tác giả và điều kiện từng ảnh; index ở `app/src/main/assets/catalog/photo_credits.json`.
- [CLIP upstream](https://github.com/openai/CLIP), [ONNX conversion](https://huggingface.co/Xenova/clip-vit-base-patch32): revision `d15189d7028b43f1d3e65039190477f6af591c2a`, SHA256 `583fd1110a514667812fee7d684952aaf82a99b959760c8d7dca7e0ab9839299`.
- [TikTok oEmbed](https://developers.tiktok.com/doc/embed-videos): endpoint công khai đã triển khai; `docs/evidence/tiktok-live-smoke.json` ghi giới hạn lượt live.
- `docs/evidence/ai-model-training.json`, `scratch-model-report.json`, `backend-tests-final.log`, `v2-verification-complete.log`.

Phần nghiên cứu ReciMe ban đầu vẫn là báo cáo lịch sử riêng tại REA_ANALYSIS.md; dữ liệu và code bản2 không biến những phần UNKNOWN về ReciMe thành kết luận đã reverse-engineer.
