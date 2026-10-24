# Rà soát Wiki tự động, chạy local

Công cụ đối chiếu nguồn và ảnh bằng **base SmolVLM-256M** đã có trong cache; không dùng adapter đã học từ Wiki để tự xác nhận chính tập Wiki. Đây là **nhãn yếu do máy rà**, không phải annotation của chuyên gia hay kết quả đánh giá độ chính xác thực tế.

## Chạy thử, chạy đủ, tiếp tục

Mở PowerShell tại dự án:

```powershell
Set-Location C:\Users\admin\AndroidStudioProjects\reCIme
& .\backend\.venv\Scripts\python.exe -m cooking_ai.reviewer.automatic --limit 5
& .\backend\.venv\Scripts\python.exe -m cooking_ai.reviewer.automatic
```

Lệnh đầu thử 5 ảnh; lệnh sau chạy đủ 109. Chạy lại cùng lệnh sẽ dùng checkpoint, tiếp tục các ảnh còn thiếu. Có thể dừng bằng `Ctrl+C` rồi mở lại. Checkpoint tách theo hash manifest, catalog và code để tránh trộn kết quả khác phiên bản. Mỗi lần xuất tạo thư mục báo cáo có timestamp riêng, giữ báo cáo cũ.

Muốn chỉ dùng nguồn đã cache, không gọi API Wiki/Commons:

```powershell
& .\backend\.venv\Scripts\python.exe -m cooking_ai.reviewer.automatic --offline
```

Chế độ offline có namespace riêng. Cache thiếu hoặc lỗi được ghi rõ; không được coi là đã xác minh online. Mô hình ảnh chỉ đọc weights local đã cache; thiếu weights thì ghi lỗi/`UNCERTAIN`, không tự tải model hay gọi provider khác.

## Máy chấp nhận khi nào?

Nguồn phải vượt **cả 7 kiểm tra**: hash ảnh local, hash ảnh catalog, đúng ảnh được bài dẫn chiếu, đúng tên bài, license khớp, có attribution, giữ yêu cầu ShareAlike/chỉnh sửa. Metadata quyền không rõ hoặc nguồn mâu thuẫn đều không đủ điều kiện.

Mô hình mô tả ảnh **trước khi được cung cấp tên món**. Chỉ khi mô tả độc lập nhận ra món cụ thể, ảnh được nhận diện là món đã chế biến và bước đối chiếu tên cũng nhất quán mới đủ điều kiện chấp nhận. Trả lời “yes” khi đã thấy tên không đủ; tên chung như “soup” cũng không đủ. Đây là quy tắc thận trọng, không phải xác suất đã hiệu chuẩn.

| Trạng thái | Ý nghĩa |
| --- | --- |
| `AUTO_ACCEPTED` | Nguồn/quyền và bằng chứng ảnh nhất quán theo policy; nhãn yếu. |
| `AUTO_REJECTED` | Có bằng chứng loại, ví dụ trùng ảnh, sai hash hoặc mô hình nhất quán xác định ảnh không liên quan. |
| `UNCERTAIN` | Thiếu bằng chứng, quyền chưa rõ, model lỗi hoặc nhận dạng không đủ cụ thể; không tự đoán tên thay thế. |

## Đọc kết quả và validate

Cache API: `cooking_ai/data/private/auto_review/api_cache/`. Mỗi namespace dưới `auto_review/` có `records/`, `reports/<timestamp>/` và `latest.json`. Đọc báo cáo mới nhất:

```powershell
$latest = Get-ChildItem .\cooking_ai\data\private\auto_review -Recurse -Filter latest.json |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
$review = Get-Content -LiteralPath $latest.FullName -Raw | ConvertFrom-Json
$review | Select-Object processed,total,complete,vision_checked,counts,rights_uncertain,validation,accepted_manifest
if ($review.validation.status -eq 'PASS') {
    & .\backend\.venv\Scripts\python.exe -m cooking_ai.data.manifest $review.accepted_manifest
} else {
    Write-Output $review.validation.status
}
```

Đủ lượt khi `processed=109`, `total=109`, `complete=true`; kiểm tra thêm `vision_checked` để biết bao nhiêu ảnh thực sự được model xử lý. Ba số trong `counts` phải cộng thành 109. Không có mẫu đủ điều kiện thì kết quả là **`NO_ELIGIBLE_RECORDS`**, `accepted.jsonl` rỗng; không gọi đó là PASS.

Trong thư mục báo cáo: `machine_review_report.jsonl` giữ quyết định và bằng chứng; `provenance_license_audit.jsonl` giữ nguồn/quyền; `rejected.jsonl`, `uncertain.jsonl` giữ các mẫu bị loại/chưa rõ; chỉ `accepted.jsonl` theo schema `Annotation`. Validator PASS chỉ chứng minh schema/hash/split/quyền được khai báo hợp lệ, không chứng minh nhãn đúng.

Ảnh xuất giữ license, provenance, `source_id` và split gốc. `annotation_verified=false` luôn được giữ cho kết quả tự động. Không chép nguyên liệu, định lượng hay bước nấu trong bài Wiki thành quan sát từ ảnh; nguyên liệu/bước nấu để trống, thông tin ẩn chưa xác định. Manifest Wiki, ảnh gốc và trạng thái reviewer thủ công không bị sửa. Lệnh local không gửi ảnh tới OpenAI/dịch vụ ngoài. Provider trả phí và training chỉ được chạy bằng lệnh riêng trong phạm vi đã phê duyệt, không có fallback ngầm.

## Đã kiểm tra nguồn thật

Preflight đã gọi thành công Wikibooks **109/109** và Commons **109/109** (218 phản hồi API); manifest gốc giữ nguyên hash. Bằng chứng và thời điểm nằm trong `source_preflight/` và cache API. Các số chấp nhận/loại/chưa rõ của nhận dạng ảnh phải đọc từ `latest.json` hiện hành; thành công API không đồng nghĩa ảnh đã được xác nhận đúng nhãn.

Nguồn chính thức: [MediaWiki parse theo revision](https://www.mediawiki.org/wiki/API:Parsing_wikitext), [CommonsMetadata](https://www.mediawiki.org/wiki/Extension:CommonsMetadata), [yêu cầu tái sử dụng từng file Commons](https://commons.wikimedia.org/wiki/Commons:Reusing_content_outside_Wikimedia). Quyền được đối chiếu từng file; không suy ra quyền từ việc ảnh nằm trên Wikimedia.

## Lượt OpenAI đã được phê duyệt

Bạn đã cho phép gửi 109 ảnh Wiki công khai, tối đa 218 requests và 1 USD trước thuế. Đã chạy đủ: **11 AUTO_ACCEPTED / 1 AUTO_REJECTED / 97 UNCERTAIN**, nguồn/quyền không có lỗi trong các kiểm tra hiện có. `gpt-4.1-mini` mô tả mù nhãn rồi mới đối chiếu tên; mọi nhãn vẫn là nhãn yếu. Các nhãn tự động không được tính là xác minh của con người; 2 xác nhận thủ công sẵn có được giữ riêng, không sửa hoặc dùng để biến nhãn máy thành nhãn chuyên gia.

Đã dùng 218 requests, không có request lỗi. Quy đổi usage khoảng **0,094154 USD trước thuế** theo [giá model chính thức](https://developers.openai.com/api/docs/models/gpt-4.1-mini); có thể khác hóa đơn do cache/thuế. Ngân sách/request cap được giữ trong SQLite chung `openai-budget.sqlite3`; timeout cũng giữ reservation và không retry trả phí tự động. Chạy lại chỉ dùng kết quả đã cache; không được đổi receipt/ledger để vượt phê duyệt. Không commit approval, ledger, ảnh hoặc phản hồi thô.

```powershell
# Chỉ dành cho workspace đã có approval record hợp lệ; lần này đã chạy xong.
& .\backend\.venv\Scripts\python.exe -m cooking_ai.reviewer.paid_automatic --consent
$review = Get-Content .\cooking_ai\data\private\auto_review\openai-latest.json -Raw | ConvertFrom-Json
& .\backend\.venv\Scripts\python.exe -m cooking_ai.data.manifest $review.accepted_manifest
```

`openai-latest.json` trỏ tới báo cáo cuối, chứa counts, đường dẫn manifest, budget. 11 mẫu đã PASS validator; giữ source/split/license/hash. 97 mẫu chưa rõ và 1 mẫu bị loại không vào training. Toàn bộ 11 ảnh được chấp nhận thuộc train; không được đổi split chỉ để tạo benchmark.

Để chuẩn bị pilot hỗn hợp, dùng `cooking_ai.scripts.auto_training_manifest --summary .../openai-latest.json --source-manifest .../wiki/manifest.jsonl --output <thư mục accepted>/mixed-candidates.jsonl`. Công cụ chỉ thêm **text** của manifest gốc (evidence user_text), không thêm ảnh bị loại. Chọn subset bằng `training_subset --max-length 2048` để khớp config pilot; không truncate ảnh/labels. Validation/test text chỉ đo trích xuất/JSON trên nguồn held-out, không phải chất lượng nhận ảnh.

## Training và kiểm tra đã chạy thật

Đã tạo mixed candidates từ 11 ảnh accepted + 505 bản ghi text nguồn; chỉ chọn pilot 12 nguồn độc lập: **train 7 ảnh + 1 text, validation 2 text, test 2 text**. Không đưa ảnh rejected/uncertain vào train. Không có ảnh accepted held-out, nên không có số đo độ chính xác nhận ảnh.

`cooking_ai/runs/wiki-auto-reviewed-pilot-2048/` chứa LoRA SmolVLM-256M CPU **9 optimizer steps**, đã save tại step 8 rồi resume step 9, optimizer/RNG/processor/metrics và adapter hash có thay đổi thật. Validation loss 0,427743 trên 2 text. Load trained checkpoint và inference ảnh đã chạy thật nhưng **JSON không hợp lệ**. Lần thử đầu dừng sau 1 step vì subset 4096/config 2048 không khớp; không có checkpoint của lần đó, metrics vẫn giữ nguyên. Đã sửa `training_subset --max-length 2048`, lọc nguyên mẫu thay vì truncate.

So sánh cùng 2 text test nguồn held-out, cùng prompt/revision/token cap1024: **base / LoRA cũ 25-step / LoRA mới 9-step đều JSON 0/2, exact dish 0/2**. Đây là proxy yếu, không phải benchmark chuyên gia; không có bằng chứng cải thiện chất lượng. Không bật model mới làm mặc định. `real_accuracy_evidence=false`.

Báo cáo máy đọc được: `cooking_ai/data/private/auto_review/training-summary.json` (checkpoint, manifest pilot, hash, metrics, evaluation và giới hạn). Các `*-predictions.jsonl`, `*-evaluation.json` nằm trong thư mục báo cáo cuối; dữ liệu/weights giữ private và gitignored. Model sản phẩm và checkpoint cũ được giữ nguyên.

Qwen3-VL-4B vẫn chỉ training-ready: GPU thực tế RTX4050 6141MiB, cấu hình yêu cầu ít nhất 16GiB free CUDA; khuyến nghị GPU24GB+/RAM32GB+/storage50GB+. Máy hiện còn khoảng 2GiB RAM và 10GiB đĩa trống. Ngoài GPU còn thiếu ảnh held-out và nhãn ingredient/video đáng tin cậy; 11 nhãn yếu không đủ chứng minh AI nấu ăn chuyên biệt. Launcher/config có sẵn theo TRAINING.md; chưa thuê GPU hoặc hosting.
