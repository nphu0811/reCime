# Review bản 2 — nguồn dữ liệu, database, AI và Preview

Review độc lập trên working tree ngày 2026-10-22. Code ứng dụng được giữ chỉ đọc bởi reviewer; agent triển khai chính xử lý các phát hiện. Báo cáo này ghi rõ kiểm tra nguồn/diff và phần skill graph riêng, không coi graph là bằng chứng ứng dụng đã chạy.

## Phát hiện từ source/diff

1. **P2 — công thức catalog đã xóa có thể xuất hiện lại khi nâng catalog.** Bản đầu `CatalogImporter.install` chỉ kiểm tra `findCatalog`; xóa hàng đồng nghĩa không còn catalogKey để phân biệt món chưa từng nhập và món cố ý xóa. Marker SharedPreferences chỉ ngăn nhập lại cùng phiên bản, không ngăn nâng phiên bản. Đã báo agent chính thêm tombstone bền vững và migration/test import lại sau xóa. Đã đọc lại bản sửa: DB schema3, migration2→3 tạo catalog_tombstones; deleteRecipe ghi tombstone+xóa trong cùng transaction; importer bỏ catalogKey đã xóa. Kiểm thử migration/import mới đã PASS trong bộ12 Android tests cuối.
2. **P2 — khoảng lượng có bullet/phân số bị biến thành một số chính xác.** Bản đầu `RecipeParser.ingredient` kiểm tra khoảng trước khi bỏ bullet. `- 4–8 con tôm` đi qua guard rồi được parse số4; `1/2–1 cup sữa` cũng không khớp guard số nguyên/thập phân. Như vậy scale/groceries chỉ lấy cận đầu. Đã báo agent chính đưa chuẩn hóa trước nhận diện khoảng và kiểm tra các khoảng phân số. Đã đọc lại bản sửa: bỏ bullet/chuẩn hóa Unicode trước, guard mới hỗ trợ số nguyên, thập phân, phân số và hỗn số. Kiểm thử hồi quy mới đã PASS trong bộ10 unit tests cuối.
3. **Rủi ro mở rộng, hiện không kích hoạt:** nhánh scratch classifier trong `backend/engine.py` giả định nhiều hàng `coef_`; mô hình SGD nhị phân chỉ có một hàng nên `argmax` luôn chọn lớp0. Model hiện tại có3 lớp và `approvedForRecipeSelection=false`, nên không ảnh hưởng đường CLIP hiện dùng. Đã bổ sung nhánh dấu cho trường hợp nhị phân; model chưa đạt vẫn bị tắt.

## Kiểm tra thực thi độc lập

- Lệnh `.\\backend\\.venv\\Scripts\\python.exe -X utf8 -m unittest backend.test_backend -v`: **7/7 PASS**, unittest báo1.920s. Có kiểm tra CLIP ONNX thật với ảnh tham chiếu trong gallery; đây không phải kiểm định accuracy ảnh mới.
- Đọc catalog bằng Python:547 record,547 catalogKey riêng biệt, không trùng title,211 record có ảnh/178 file ảnh riêng biệt; không có servings<=0; không thấy markup `{{`/`[[` còn trong ingredients.
- `PRAGMA integrity_check` của `datasets/recipe_catalog.sqlite3`: **ok**.
-17 record ảnh không có imageLicenseUrl đều ghi Public domain và giữ URL mô tả Commons; không diễn giải đây là17 ảnh thiếu giấy phép. Với ảnh CC, attribution/license/source được lưu riêng với giấy phép text.
- `LocalAiClient` giới hạn ảnh nén và response; UI gắn kết quả background vào đúng view hiện tại. App dùng OCR cục bộ, AI ảnh/text qua server laptop; không thấy hardcode khóa/API trả phí trong các file đã review.
- Tài liệu phân biệt rõ547 công thức khởi đầu với toàn bộ món Việt, ảnh cùng nhóm với ảnh đúng biến thể, CLIP retrieval với LLM, test TikTok mock với lượt gọi live thiếu caption. Không đánh đồng model scratch chưa đạt với model được đưa vào chọn công thức.

## Preview

Reviewer đã xác nhận XML `fragment_board.xml` có `tools:text` cho board_title, board_subtitle, week_label, board_action, board_secondary, board_empty và tools:listitem cho RecyclerView. Đây là dữ liệu design-time, không thay gán text runtime. Reviewer chưa trực tiếp mở Android Studio Layout Editor; kết quả build/emulator và Preview trực quan phải lấy từ kiểm tra của agent chính, không suy ra từ XML hợp lệ.

## Skill

Đã đọc `understand-diff/SKILL.md` và skill hỗ trợ `understand/SKILL.md`. Graph ban đầu không tồn tại. Đã thực sự cài dependencies của plugin bằng `pnpm install --frozen-lockfile` tại `C:/Users/admin/.understand-anything/repo`, sau đó build `@understand-anything/core`; cả hai thành công. Node24.14.0; pnpm shim báo10.32.1, log workspace install dùng10.6.2.

Bundled `scan-project.mjs` đã chạy:114 file,61 file bị loại thêm; loại venv/model/raw/media/build/evidence/archive gốc. Bundled import-map và batching đã chạy (13 import edges,14 batches). Kết quả graph và diff overlay sẽ được ghi trong `.understand-anything/`; các giới hạn semantic review được ghi riêng, không tuyên bố đủ toàn bộ7 phase nếu chưa thực hiện.


### Kết quả graph/diff skill thực tế

Graph một phần đã lưu:17 file lõi được đọc ngữ nghĩa,59nodes,45edges,5layers,6tour steps. Đã chạy bundled structural extraction cả14batches và inline validator:0issues,2orphan warnings cho tài liệu; fingerprint baseline114files thành công trước khi ghi meta.json. Không tuyên bố full graph hoàn chỉnh: Java wildcard/package-local imports chưa được resolver bao phủ tốt; source/diff review bên trên là bằng chứng chính.

`understand-diff` đã đối chiếu working tree (bao gồm untracked,319file tại snapshot) với graph:57node khớp,0node bên ngoài tập thay đổi được tìm thấy qua1hop;5layer ảnh hưởng gồm Android UI/persistence/import-domain/laptop AI/documentation. Số affected0 không có nghĩa là thay đổi ít rủi ro, vì gần toàn bộ graph core đang thay đổi và import edges còn thưa. Rủi ro chính là thay đổi đồng thời dataset→Room→UI và adapter ảnh→network→backend; cần build, migration/instrumentation tests và kiểm thử server như đã ghi, không suy ra từ graph.

Artifacts: `.understand-anything/knowledge-graph.json`, `validation.json`, `partial-analysis.json`, `diff-overlay.json`, `diff-review-summary.json`. Graph là snapshot trong lúc code được sửa; nên rebuild sau khi hoàn tất thay đổi. Có thể dùng `/understand-anything:understand-dashboard` để xem overlay; phiên này không tự mở dashboard.


## Kết quả xác minh cuối của agent chính

`assembleDebug testDebugUnitTest connectedDebugAndroidTest lintDebug` PASS:10 unit +12 Android instrumentation,0failed/skip; lint0errors/140warnings. 7 backend tests cuối cũng PASS. Bằng chứng: evidence/v2-all-tests-final.log, evidence/tests-v2/, evidence/backend-tests-final.log. Test ảnh AI đã đồng bộ executor trước kiểm tra chi tiết; test planner phân biệt text của EditText và item danh sách, dialog đúng root. Cả2 lỗi P2 có test hồi quy pass.
