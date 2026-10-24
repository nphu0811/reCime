# Cooking AI handoff — 24/10/2026

PR#5 merge bằng GitHub merge commit `91311aad73bcebacb84da043d506b373bc56b4eb`. Local sạch trước merge; source bàn giao `8a5b5f7` là ancestor. Mọi thay đổi tiếp tục trên master, không branch/PR mới.

Cooking AI độc lập trong `cooking_ai/`: schema evidence/unknown, manifest/rights/hash/source splits, catalog import, video preprocessing, real LoRA train/validation/save/resume/export, checkpoint inference và held-out lexical metrics. Chi tiết chạy trong TRAINING.md; thao tác cần người dùng trong HUMAN_ACTIONS.md.

Backend thêm `/v1/cooking/models`, `/v1/cooking/jobs`, job status/cancel/retry. Luôn require account, owner isolation,10MB/180s, explicit provider/consent, không fallback ngầm. Metadata SQLite tách biệt; timeout/cancel kill process tree, dọn media sau terminal; restart báo interrupted, không tự gửi lại. Retry cần upload lại. Một Uvicorn process; queue tối đa4,2/user,1 worker; terminal metadata giữ7ngày và thu dọn khi tạo job tiếp. Account delete hủy jobs/xóa kết quả. Không có secrets/provider exception details trong responses.

Video thực: local MP4/MOV→12 bounded frames→tối đa6 frame scene-ranked→audio WAV→ASR local nếu có→OCR nếu có→timeline→VLM→schema/evidence checks. Sampling không đảm bảo thấy mọi bước. Không có audio/OCR thì warning rõ; thiếu quyền URL dùng caption/file, không nhận metadata là video analysis. ASR/OCR tiếng Việt thật và video nấu ăn annotated còn chưa kiểm chứng. FFmpeg tone/test-pattern fixtures chỉ chứng minh plumbing.

Android Java/XML Import: chọn ảnh đã OCR/chụp hoặc chọn/share video, chọn local/OpenAI, consent, multipart job/progress, cancel/retry, saved job ID/scope để resume, review kết quả và provenance/unknown, Editor→Lưu. Không chatbot, không auto-save, DB/network background. Request key/idempotency và local temporary file giúp retry upload gián đoạn; scope đổi tài khoản/backend thì dừng. Giữ JSON-LD, OCR, OpenAI text/image cũ và duplicate review. Không đổi SQLite schema/data trong checkpoint này.

## Training/evaluation thực tế

SmolVLM256M pretrained đã forward/backward và AdamW2steps trên text-card fixture, resume lên3steps, changed hashes, validation1.917718. Chỉ là SMOKE_TRAINED, không phải chất lượng nhận diện thực phẩm. Dataset Wiki614mẫu đã export. Mixed pilot34mẫu gặp lỗi native c10.dll trên Windows, không có checkpoint và không tính PASS. Pilot ảnh thật12mẫu (8/2/2) chạy8steps với promptv1, inference không hợp lệ. Promptv2 được train lại từ base24steps, resume25steps, validation1.618588, optimizer/adapter/hash/logs thật. Checkpoint-load inference PASS về cơ chế nhưng JSON thử không hợp lệ; chưa dùng được làm model production. Artifact/log local tại `cooking_ai/runs/` và `.tools/cooking-*`; proof aggregate được giữ trong `cooking_ai/evaluation/`.

Qwen3-VL4B chỉ TRAINING READY: license/API/config đã chuẩn bị, chưa load/train GPU4B, chưa benchmark VRAM; không báo đã training. CPU torch được cài để kiểm chứng SmolVLM. Bộ cài CUDA quá lớn/full download không hoàn tất; không gọi GPU training là đã chạy.

Không tự bật pilot vào phục vụ: model info yêu cầu adapter hash đúng, optimizer steps>0, changed weights, không fixture, checkpoint inference, JSON hợp lệ và production_approved=true trong run metadata. Chưa có human evaluation production nên pilot không được approve. Chỉ bật sau khi đánh giá chất lượng/an toàn đã review; OpenAI vẫn là lựa chọn riêng có consent. Benchmark không gửi media đến OpenAI.

## Phạm vi còn thiếu

Automatic record sync Android vẫn thiếu (server revisions/tombstones/idempotency giữ nguyên); cloud backup không thay thế sync từng entity. Google Login/email recovery/collaboration/public links vẫn thiếu code/QA. Không triển khai merchant/paywall. Không chứng nhận mọi process-kill/offline/rotation/ANR/Android version hoặc broad cooking accuracy.

## Kết quả đo đã lưu

`evaluation/results/wiki_image_pilot_run.json`: 25steps thật, validation1.61858785, resume24→25, checkpoint-load inference=true, JSON=false, production_approved=false. Trạng thái **PILOT_TRAINED_VERIFIED / QUALITY_FAILED** chỉ xác minh cơ chế training theo manifest/optimizer/weights/logs/validation/checkpoint load; không công bố model đã giỏi nấu ăn.

| Hai ảnh held-out từ nguồn thật, nhãn chưa review tay | Base | LoRA25steps |
|---|---:|---:|
| JSON hợp lệ |0/2|0/2|
| Tên món exact theo nguồn |0/2|0/2|
| Latency CPU trung bình, một lượt nhỏ |28.617s|8.5s|

VRAM0 vì dùng CPU. Không coi chênh lệch latency nhỏ này là benchmark hiệu năng tổng quát. Ingredient/quantity/step/timestamp/uncertainty metrics chưa đo được trên test ảnh này; giữ null. Không gọi OpenAI để benchmark vì chưa có consent gửi bộ dữ liệu/chi phí. Muốn tiếp tục training, dùng lệnh resume trong TRAINING.md; cần nhãn tốt hơn, dữ liệu đa dạng và đánh giá trước khi approve production.

## QA checkpoint

- Backend+AI **48/48 PASS** một invocation cuối; gồm legacy catalog/OpenAI mocks/auth/sync, dataset rights/leakage, actual FFmpeg decode silent/audio, timeline numeric evidence, job consent/isolation/idempotency/restart/cancel/cleanup và worker process thật với media hỏng. Không gọi provider trả phí trong lượt này.
- Android assembleDebug/assembleDebugAndroidTest, **44 JVM PASS**, lint0errors. **3/3** CookingAiContract/Rotation PASS và **6/6** ImageAiImport/OcrRotation/ImportWebRotation/ImportDuplicateUi PASS trên Pixel10a API37.2. Không chạy lại toàn75cases cũ. Rotation job mới và review không auto-save được kiểm chứng; mọi process-kill/offline/video physical-device case chưa đủ QA.
- AVD Pixel10a ban đầu chưa cài app; dùng install-r, không uninstall/clear data. Sau seed547recipes, snapshot trước/sau6test: SQLite integrity=ok, không mất/đổi hàng baseline; chỉ thêm cookbook mặc định. AVD chứa dữ liệu cũ không được mở; không đụng app official. Emulator đã tắt sau QA.
- Lượt đầu instrumentation có2FAIL: mock multipart giả định không có per-part headers và cold catalog setup timeout20s. Sửa parser fixture để kiểm đúng consent value, chờ bounded catalog IO trước khi mở mock HTTP; giữ assertions. Bộ3cases sau sửa PASS một lượt. Một lượt Gradle bị dừng khi host thiếu RAM; lượt build tuần tự sau đó PASS, không tính lượt dừng là xanh.
- Backend local được restart với adapter pilot ở biến process, không sửa backend/.env. `/health` và unauth Cooking API được smoke không gọi AI/provider. Pilot unavailable theo guard chất lượng, OpenAI/USDA keys vẫn chỉ trên server. Local outputs/logs nằm trong `.tools/` và `cooking_ai/runs/`; weights/media/database không commit.
