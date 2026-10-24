# Cooking AI: dữ liệu và training thật

Đọc trạng thái thực tế/test tại `COOKING_AI_HANDOFF.md`. Không gọi pilot là model nấu ăn đạt chất lượng production.

## Dữ liệu đang dùng

Không cần xuất SQLite cá nhân: catalog nguồn của SQLite đã nằm tại `app/src/main/assets/catalog/recipes.json`, ảnh và `photo_credits.json`. Exporter dùng bản public này, giữ SQLite và ảnh người dùng nguyên vẹn.

Nguồn: 547 công thức Wiki. Export được **505 mẫu trích văn bản + 109 ảnh thật**, 485 nhóm nguồn; split 493/62/59. Hash manifest: `9c474c2c0cd0917cee8d07b114df10885ac86d9936a4af05b1dafb29e5e73a10`. Bản ghi giữ revision permalink, tác giả, giấy phép và thay đổi; ảnh có giấy phép riêng. [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) yêu cầu attribution và share-alike; giữ `LICENSE.md` và attribution khi phân phối bản dẫn xuất. Không tự coi mọi ảnh Wiki có cùng giấy phép. Ví dụ giấy phép ảnh được kiểm tra trên [trang gốc Commons](https://commons.wikimedia.org/wiki/File:Aioli_mit_Oliven.jpg).

Ảnh minh họa nhóm món bị loại; ảnh còn lại có **nhãn tên theo bài nguồn, chưa được xác minh thủ công**. Không gán nguyên liệu, lượng hoặc bước trong bài thành bằng chứng nhìn thấy từ ảnh. Ảnh chỉ dạy tên theo nguồn và trả unknown; văn bản dạy trích facts có trích dẫn. Đây là weak supervision, không phải annotation chuyên gia. Những dataset tìm thấy nhưng không rõ quyền ảnh hoặc nhãn do AI tạo không được nhập.

Tất cả frame/công thức cùng nguồn, cùng ảnh/hash hoặc tiêu đề được gom trước khi chia; validator chặn source/hash qua nhiều split. Exact duplicate được loại, near duplicate khác crop vẫn cần rà soát. Không dùng test để train.

```powershell
backend/.venv/Scripts/python.exe -m cooking_ai.scripts.from_catalog
backend/.venv/Scripts/python.exe -m cooking_ai.data.manifest cooking_ai/data/private/wiki/manifest.jsonl
backend/.venv/Scripts/python.exe -m cooking_ai.scripts.training_subset --manifest cooking_ai/data/private/wiki/manifest.jsonl --output cooking_ai/data/private/wiki/pilot.jsonl
```

Mixed pilot đã chuẩn bị24 train /4validation /6test nhưng training gặp native c10.dll access violation trên Windows; không có checkpoint nên không được tính PASS. Pilot thực tế dùng **8 ảnh train /2validation /2test**,12nguồn độc lập, không fixture. Đã train24steps rồi resume lên25steps; không đủ chứng minh chất lượng. Prompt ngắn v2 được train lại từ base và hash/ghi nguyên văn trong metadata, không chỉ thay prompt rồi gọi fine-tuning. Data/weights chỉ ở local và được gitignore.

## Môi trường và lệnh

[SmolVLM-256M-Instruct](https://huggingface.co/HuggingFaceTB/SmolVLM-256M-Instruct) Apache-2.0, revision `7e3e67edbbed1bf9888184d9df282b700a323964`; dùng AutoProcessor/chat template và AutoModelForImageTextToText. Transformers 4.57.6, PEFT 0.18.1, torch 2.6.0, torchvision 0.21.0. CPU wheel đã dùng cho lượt xác minh này. Không cần HF login với model public hiện tại.

```powershell
# Dùng môi trường riêng nếu chưa có; không ghi đè backend/.env.
python -m venv cooking_ai/.venv
cooking_ai/.venv/Scripts/python.exe -m pip install torch==2.6.0 torchvision==0.21.0 --index-url https://download.pytorch.org/whl/cpu
cooking_ai/.venv/Scripts/python.exe -m pip install -r cooking_ai/requirements-training.txt
# Các lệnh sau có thể dùng backend/.venv/Scripts/python.exe nếu đã cài dependencies.
backend/.venv/Scripts/python.exe -m cooking_ai.scripts.training_subset --manifest cooking_ai/data/private/wiki/manifest.jsonl --output cooking_ai/data/private/wiki/image-pilot.jsonl --image-only --train 8 --validation 2 --test 2
backend/.venv/Scripts/python.exe -m cooking_ai.training.train --config cooking_ai/configs/smolvlm-pilot.json --manifest cooking_ai/data/private/wiki/image-pilot.jsonl --output cooking_ai/runs/wiki-image-v2 --steps 24 --cpu
# --steps là tổng số step mong muốn, không phải số step cộng thêm.
backend/.venv/Scripts/python.exe -m cooking_ai.training.train --config cooking_ai/configs/smolvlm-pilot.json --manifest cooking_ai/data/private/wiki/image-pilot.jsonl --output cooking_ai/runs/wiki-image-v2 --resume cooking_ai/runs/wiki-image-v2 --steps 48 --cpu
```

Training có assistant-only label mask, image processor thật, forward/backward, AdamW, gradient clipping, finite checks và hash trainable weights trước/sau; validation không update weights. Không truncate âm thầm image tokens/labels. Output gồm `adapter_model.safetensors`, adapter/processor config, `optimizer.pt` (optimizer/scaler/RNG), `run.json` (revision, manifest hash, loss, steps, adapter hash, version), metrics. Resume yêu cầu đúng manifest/config. Chỉ load optimizer/checkpoint do bạn tạo và tin cậy.

```powershell
backend/.venv/Scripts/python.exe -m cooking_ai.scripts.infer --adapter cooking_ai/runs/wiki-image-v2 --image PATH_TO_TEST_IMAGE
backend/.venv/Scripts/python.exe -m cooking_ai.scripts.predict --manifest cooking_ai/data/private/wiki/image-pilot.jsonl --adapter cooking_ai/runs/wiki-image-v2 --base --output cooking_ai/runs/wiki-image-v2/base-predictions.jsonl
backend/.venv/Scripts/python.exe -m cooking_ai.scripts.predict --manifest cooking_ai/data/private/wiki/image-pilot.jsonl --adapter cooking_ai/runs/wiki-image-v2 --output cooking_ai/runs/wiki-image-v2/tuned-predictions.jsonl
backend/.venv/Scripts/python.exe -m cooking_ai.evaluation.evaluate --manifest cooking_ai/data/private/wiki/image-pilot.jsonl --predictions cooking_ai/runs/wiki-image-v2/tuned-predictions.jsonl --output cooking_ai/runs/wiki-image-v2/evaluation.json
```

Metrics: JSON validity, exact dish, ingredient P/R/F1, quantity/unit exact, step lexical recall, timestamp start MAE, ingredient/quantity hallucination, unknown recall, Vietnamese exact dish, latency/VRAM. Null = chưa đo/không có mẫu tương ứng. Lexical step match không thay thế đánh giá ngữ nghĩa/an toàn. `annotation_verified=false` luôn ngăn báo đây là accuracy đã được chuyên gia xác minh. Không gọi OpenAI khi benchmark này chạy.

## Bổ sung dữ liệu đúng quyền

**Rà 109 ảnh Wiki có sẵn:** dùng [Dataset Reviewer](COOKING_AI_DATASET_REVIEWER.md), không cần tự viết annotation JSON hoặc chạy exporter lại. Công cụ lưu nháp riêng, giữ split gốc và chỉ export ảnh được bạn xác nhận cả nhãn lẫn quyền. Chưa có ảnh nào được người dùng xác minh khi bàn giao công cụ. Các bước JSON dưới đây dành cho dữ liệu bổ sung khác.

Mẫu: `cooking_ai/data/sample_annotation.json`; ghi mỗi record một dòng vào `cooking_ai/data/private/real/annotations.jsonl`, media tại `.../real/media/`. Import tự tính hash và source split:

```powershell
backend/.venv/Scripts/python.exe -m cooking_ai.scripts.import_dataset cooking_ai/data/private/real/annotations.jsonl --root cooking_ai/data/private/real --output cooking_ai/data/private/real/manifest.jsonl
```

Bắt đầu với ít nhất 100 ảnh độc lập của 20 món (có thể rà 109 ảnh sẵn có trước), thêm 20 MP4/MOV thật nếu đánh giá video. JPEG/PNG/WebP; video tối đa 10MB/180s. Giữ một `source_id` cho toàn video và mọi frame, kể cả bản cắt/chỉnh. Ghi `permission_to_train`, license, tác giả và bằng chứng đồng ý thật; chưa có quyền thì để false (validator từ chối). Không đưa dữ liệu riêng tư lên cloud khi chưa đồng ý.

Nguyên liệu/động tác quan sát trực tiếp mới được gán visual; định lượng không nhìn/đọc/nghe được để null. Lời nói/chữ phải trích đúng và gắn start/end cho video. `annotation_verified=true` chỉ sau khi người kiểm tra đối chiếu facts và timestamps. Phần suy đoán vào `suggested_recipe`, không vào observed facts. Đánh giá held-out nguồn thật; không coi fixture hoặc nhãn AI là bằng chứng hiểu món ăn.

```powershell
backend/.venv/Scripts/python.exe -m cooking_ai.scripts.preprocess_video YOUR_VIDEO.mp4 --output cooking_ai/data/private/video-01
```

Exporter video chỉ tạo frames/timeline để review; không tự cấp quyền hoặc sinh ground truth. [FFmpeg](https://ffmpeg.org/legal.html) tùy build là LGPL/GPL; binary không được bundle. [faster-whisper](https://github.com/SYSTRAN/faster-whisper) MIT; [Tesseract](https://github.com/tesseract-ocr/tesseract) Apache-2.0. Cài model/packs và kiểm tra bản phân phối theo `COOKING_AI_HUMAN_ACTIONS.md`.

## Qwen / GPU lớn

[Qwen3-VL-4B-Instruct](https://huggingface.co/Qwen/Qwen3-VL-4B-Instruct) Apache-2.0; config QLoRA đã chuẩn bị, chưa train/benchmark trên GPU này. Code dùng processor/chat template từ revision được chốt lúc bắt đầu run, không remote Python code. Chưa dùng Unsloth vì chưa xác minh tương thích.

Máy kiểm tra: RTX4050 6141MiB VRAM (~5346MiB free lúc đầu), RAM 12GB (~1.6GB free), đĩa trống ban đầu 13.6GB. Ước lượng Qwen BF16 riêng weights ~8GB, thêm activations/vision/optimizer; QLoRA giảm base xuống khoảng 2–3GB nhưng tổng phụ thuộc ảnh/context và có thể 10–18GB. Đây là ước lượng, không phải VRAM benchmark. Config từ chối dưới 16GiB free CUDA; đề xuất GPU24GB+, RAM32GB+, storage50GB+ cho Qwen.

So sánh: CPU local không thuê GPU, đã chạy được SmolVLM pilot nhưng chậm; Runpod A5000 24GB niêm yết $0.27/h hoặc A40 48GB $0.59/h khi kiểm tra 24/10/2026, chưa gồm storage/thuế/availability ([giá chính thức](https://www.runpod.io/pricing)). Không có tài nguyên trả phí nào được tạo. Chỉ sau khi bạn phê duyệt host/chi phí: chạy `bash cooking_ai/scripts/train_cloud.sh /data/manifest.jsonl /data /data/run`. Script chỉ chạy bên trong máy đã có, không thuê máy. Full Qwen compatibility và GPU training còn cần chạy thực tế.
