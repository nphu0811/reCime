# Blockers và phần chưa hoàn thành

Cập nhật23/10/2026, WIP PR#5. Không còn blocker APK/JADX hoặc đăng nhập Emulator.

- Lần quan sát trước Editor official có màn Something went wrong/Try again; form đã có screenshot/XML phiên trước. Chưa xác minh save→detail. Không suy nguyên nhân server hoặc bypass.
- Editor đã bổ sung Photo Picker và section UI độc lập, nhóm ảnh đã PASS build/unit/lint và 21 instrumentation tests. Nội dung/sheet màn con Info/Nutrition/Tags official vẫn UNKNOWN; không tuyên bố pixel parity. Process kill giữa lúc copy/commit và full backup/restore đa thiết bị chưa kiểm chứng.
- Runtime official của Detail/Cooking/export và populated Planner/gesture/start-day settings còn thiếu đối chiếu. Move/drag/local week-start của app mình đã triển khai trong nhóm Planner; batch chọn nguyên liệu từ recipe/plan đã triển khai local ở nhóm Groceries; RECIPE/TIMESTAMP grouping và attribution per-recipe vẫn chưa đầy đủ. Auto-derived giỏ app mình là khác biệt, chưa chứng minh tương đương official.
- Auth/cloud sync/collaboration/public recipe links cần backend và cấu hình provider riêng được phép; chưa có implementation đầy đủ. Không dùng API/token của official.
- Social video/audio extraction cần nguồn/API được phép; TikTok hiện chỉ oEmbed + retrieval catalog, không được gọi là recipe import hoàn chỉnh.
- Live USDA/OpenAI cần key backend `FDC_API_KEY`/`OPENAI_API_KEY`; không gửi key trong chat, không nhúng APK. Mock tests không xác minh live service.
- Không merge master khi nhóm test liên quan chưa PASS. Không công bố parity1:1; xem ma trận từng chức năng.

Nhóm ảnh bổ sung migration v5 additive; không đổi dữ liệu/bảng v4 cũ. System-picker cloud providers, process kill giữa copy/commit và full backup/restore vẫn chưa kiểm chứng. Không thiếu API key cho chức năng ảnh local.


Recipe Detail (tiếp từ9e94d5b): official bị chặn ở Editor lỗi và draft chưa lưu; không bỏ draft để ép navigation. Detail runtime/gallery/animation official vẫn UNKNOWN. Nhóm Detail độc lập PASS build/unit/lint,22 JVM và31 instrumentation tests; không thay schema/Editor đã hoàn thành. AI live cần backend/key riêng; fixture HTTP chỉ xác minh xử lý503 và rotation. Print preview không chứng minh in vật lý.


Meal Planner (tiếp3a3179f): đã kiểm thử nhóm tuần/4 bữa/move/drag/week-start/portions:26 JVM PASS,39instrumentation cases qua vòng rộng38PASS và retry1PASS, không đổi schema v5. Dùng evidence APK đã có, không phân tích lại. Official populated planner/drag gesture/cloud week-start vẫn UNKNOWN; continuous grocery auto-sync của official chưa được chứng minh. App mình giữ cơ chế derived groceries local, không coi là parity official. Recipe/Editor/ảnh không thuộc nhóm sửa này.


Groceries (tiếpc0ffc04): nhóm local UI/CRUD/batch selection/servings/source separation đã qua build/unit/lint:31 JVM PASS;40instrumentation cases qua39PASS vòng rộng và1PASS retry. Continuous auto-derived của app mình được giữ nguyên, không gọi là hành vi official. Batch là bản sao independent/manual; chưa có persistent per-recipe attribution hoặc timestamp grouping trong v5. Official exact aggregation/aisle/source reconciliation/populated UI vẫn UNKNOWN. Merchant checkout chưa triển khai; cần provider/API/quyền tích hợp và backend riêng nếu làm sau, hiện không có nút checkout giả. Không thiếu key cho nhóm local này.

## Home / Cookbooks / Discover — sau b6a2204
- Dynamic Discover/ranking/creator search/account quota và collaboration/access cần backend/provider độc lập có quyền truy cập; không có contract/key trong APK để suy ra. Không hiển thị dữ liệu động/quota giả.
- Catalog local hiện có được giữ riêng theo catalogKey và có attribution; favorite/edit catalog không tự tạo bản sao personal. Luồng lưu catalog thành bản sao cá nhân có giải quyết provenance chưa thuộc nhóm này.
- Cookbook icon/access và full official populated UI, search ranking, animation/haptics chưa xác minh: UNKNOWN. Dùng evidence runtime cũ; không đụng draft Editor official.
- Không thay schema SQLite v5; không cần API key cho Home, cookbook CRUD, search/filter và local Discover.
\nCookbook mặc định Món nhà tiếp tục được DAO bảo vệ khỏi xóa như baseline; cookbook người dùng tạo có CRUD. OcrUiTest đã PASS Share ảnh tự tạo tiếng Anh → review → SQLite; OCR ảnh thực tế đa dạng, video/provider và live AI vẫn cần kiểm thử riêng.\n
Nhóm Home/cookbook/local Discover đã qua các kiểm tra cần thiết; không cần key/tài khoản bổ sung. Test đã phân biệt tap/long-press bằng input sequence thật và khôi phục animation settings Emulator. Lượt broad còn1 lỗi test root Planner, nhóm Workflow sau sửa PASS3/3; không nhận là một lượt49/49 xanh.

## Import / JSON-LD / duplicate review — 24/10/2026
- PublicWebExtractor đã nhận type Recipe ngắn và Schema.org URL, tìm trong @graph/mainEntity/itemListElement, hỗ trợ recipeIngredient dạng chuỗi; giữ HTTPS-only, private-address guards và giới hạn redirects.
- Import text/website/OCR sẽ kiểm tra trùng với recipe cá nhân trước khi mở Editor (trùng URL nguồn HTTPS hoặc title+ingredients+instructions chuẩn hóa). Người dùng tự chọn mở bản đã lưu/nhập bản sao/hủy; không tự ghi đè. Dữ liệu catalog offline không được tính là bản copy cá nhân.
- Android instrumentation mới gồm PublicWebExtractorJsonLdTest và ImportDuplicateUiTest. Chưa chạy trên Emulator trong phiên GitHub-only; CI build/unit/lint không thay thế connectedDebugAndroidTest. Cần QA UI thật và chạy test trên thiết bị trước khi merge.
- Social video/audio, nguồn ngoài cần auth, server-based import, OCR đa ngôn ngữ, process-death và duplicate detection sau khi người dùng sửa draft ở Editor vẫn chưa được xác minh hoàn toàn.
