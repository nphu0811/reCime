# Ma trận đối chiếu ReciMe Android 6.0.2 (217)

## Cập nhật hiện hành — 24/10/2026, từ 2513e48

Các bảng/section cũ bên dưới là lịch sử theo checkpoint. Trạng thái mới nhất: app độc lập SQLite v6 (v5→6 chỉ thêm backup restore ledger), theme trắng/kem, import web/text/OCR giữ rotation, social caption fallback rõ ràng, Discover lưu bản sao cá nhân có nguồn, Cooking checklist/timer, PDF background có ảnh/nguồn, Settings/Welcome/Guest.

FastAPI hiện có email/password/session/profile/account delete, cloud backup CAS/outbox/restore và API record sync có conflict/idempotency/tombstones. Android đã nối account + cloud backup; **chưa nối automatic record sync**. OpenAI Q&A + structured text extraction và USDA cần server keys; không tạo câu trả lời hoặc nutrition giả. Discover backend có search/pagination, offline catalog vẫn độc lập.

| Nhóm | Triển khai độc lập hiện tại | Giới hạn/parity official |
|---|---|---|
| Regression Library/ANR | Baseline Library 5/5 một lượt; giữ WAL/catalog optimization, member query background | Không coi sửa test root là sửa server/official |
| Import | Servings/author/license metadata, HTTPS redirects/body/time/DNS limits, duplicate review; OCR thực qua rotation; Share giữ caption+URL | OCR Việt thực tế đa dạng/process-kill chưa đủ QA; không tự lấy ảnh website |
| Social | Provider statuses + fallback; TikTok caption công khai, không tự mở catalog như kết quả trích video | Không có video/audio/transcript adapter cho các nền tảng |
| Account/backup | Auth độc lập, encrypted Android session, additive restore có checksum/FK/idempotence; contract local thật | Google/email verification/reset/collaboration chưa hoàn tất |
| Record sync | Generic server API có user isolation/revision/conflict/tombstone/cursor | Android mutation/outbox mapping, merge từng entity và multi-device runtime chưa triển khai |
| Nutrition/AI | JSON schema/errors/rate/timeout; USDA g/kg, kiểm tra kcal/g, không điền khi thiếu | Mock không xác minh live provider; user phải xác nhận mapping |
| Cooking/export | Step/checklist/portions, timer persisted, quyền notifications/exact alarm; PDF ảnh/nguồn background, Print ranges | Notification timing/Doze/reboot/physical printer và official Cooking UNKNOWN |

Kết quả build/tests cuối: `TEST_RESULTS.md`. DONE/CONFIGURATION REQUIRED/BLOCKED/NOT VERIFIED và cách dùng: `FINAL_HANDOFF.md`. Không chứng nhận parity1:1 hoặc toàn bộ roadmap DONE.

## Ma trận và evidence lịch sử

Ngày 23/10/2026, Asia/Saigon. Source baseline: `e4460c608da1cdd42f10da19c0a16ade0ce9ca7d`, nhánh ban đầu `codex/recime-6-0-2-parity` từ master; tiếp tục trên `wip/rea-recime-602`, PR #5. Bản đầu ma trận được ghi **trước khi sửa code**. Đây là tiến độ điều tra, không phải chứng nhận tương đương.

STATIC VERIFIED = có APK/method/manifest; RUNTIME VERIFIED = đã mở và quan sát màn thật; DOCUMENTED = chỉ baseline tài liệu chính thức, chưa kiểm tra lại trong phiên; UNKNOWN = thiếu bằng chứng. Source app độc lập có code không đồng nghĩa đã test PASS. Evidence đầy đủ giữ ngoài git; xem REA_ANDROID_6_0_2_FINDINGS.md.

| Chức năng | Hành vi ReciMe gốc | Hành vi ứng dụng hiện tại | Bằng chứng xác minh | Phần còn thiếu | Sai khác UI/UX | Hướng triển khai | Trạng thái kiểm thử |
|---|---|---|---|---|---|---|---|
| Kiến trúc/UI | Activity tạo Compose root; Hilt; màn login có ComposeView | Java/XML, Fragment, ViewModel, BaseAdapter, SQLite v5 (additive từ v4) | STATIC VERIFIED: ReciMeActivity.onCreate; RUNTIME VERIFIED: login.xml; MainActivity/LabDatabase source | Chưa trace toàn bộ graph | Native XML khác Compose, chưa đo màn sau login | Giữ Java/XML theo yêu cầu | APK đọc thành công; parity chưa PASS |
| Navigation | RUNTIME VERIFIED dock Recipes/Meal Plan/Groceries/Discover và + import | 4 dock tabs, Import/AI qua +; SEND text/image giữ Import; saved root state | home.png/xml, main-import-options.png/xml; MainActivity | Public deep links/account routes cần backend | Cream/white; timing/haptics UNKNOWN | Java Fragment, không animate hit target dock | Xem nhóm Home cuối tài liệu |
| Cookbook | RUNTIME VERIFIED create/open/empty/menu Select recipes/Edit title/Delete | Grid 2 cột/count; CRUD + chọn/bỏ personal recipes, many-to-many | cookbook-success/empty/options.png/xml; CookbookDialog/LabDao | Cover/icon/access/collaboration UNKNOWN; Món nhà mặc định vẫn bảo vệ khỏi xóa | UI độc lập trắng/kem; không pixel-parity | Transaction giữ recipe và membership khác, v5 không đổi | Xem nhóm Home cuối tài liệu |
| Recipe list/search/favorites | Có cookbook/search VM, semantic filter chưa xác minh | Search normalized AND token, ingredient comma AND, favorite | APK inventory; RecipesFragment.filter, DetailFragment | Đã sửa matcher whitespace; chưa picker/tag taxonomy | Khác grouping chưa đo | Matcher độc lập + regression | JVM IngredientFilterTest 6/6 PASS |
| Chi tiết công thức | Nutrition state, paywall actions Cook With ReciMe, PDF | Servings, units, favorite, source, edit/delete, plan/cart, PDF | STATIC VERIFIED: post-nutrition/post-cooking/post-export; DetailFragment | Chưa cooking UI official, ratings/checkable ingredients | Chưa đo authenticated UI | Giữ phần đã có, không suy từ tên | UNKNOWN parity |
| Social import | ShareActivity Compose; share-import VM; chưa trace fetch pipeline | TikTok oEmbed → retrieval catalog; social khác bị từ chối rõ | STATIC VERIFIED share-onCreate; backend/app.py, PublicWebExtractor | Không chuyển social video thành recipe | Backend khác; cần provider hợp lệ | Không gọi API private hoặc tạo recipe giả | Live social chưa chạy |
| Website/ảnh/text import | Text/image → recipeRepository → NetworkResponse; HTML resolve redirects rồi repository | JSON-LD public web; ML Kit Latin OCR; parser text | STATIC VERIFIED import-text/image/html; ImportFragment | Server extraction official UNKNOWN; app chưa persistent jobs/duplicate flow | Chưa màn official | Triển khai độc lập theo dữ liệu được phép | Có tests, chưa chạy |
| Edit/review/save | RUNTIME VERIFIED form Title/Images/Info/Nutrition/Tags/Ingredients/Instructions, Cancel/Save; lỗi retry quan sát | Editor validate snapshot → async SQLite; giữ draft khi refresh và rotation; save state fragment ViewModel | STATIC VERIFIED editor-save-body/import-save-body; runtime editor-filled.png, editor-current.png; EditorLifecycleTest | Đã có Photo Picker/section layout độc lập; còn full duplicate-import UX, màn con official UNKNOWN | App XML form dài; official sections/sheets | Bảo toàn draft/validation trước, giữ Java/XML | Build/lint +16 JVM +15 instrumentation PASS (Editor3/3) |
| Meal Planner | STATIC VERIFIED: 4 loại bữa; moveMealItemToDay theo ID; start day preference fallback MONDAY | Nhóm ngày/4 bữa, nhiều món và portions, move/dialog/native drag, local week-start, clear; detail → plan | Findings mục9; BoardFragment, LabDao, PlannerWeek | Runtime populated planner/gesture và cloud settings official UNKNOWN | Layout trắng/kem độc lập, chưa pixel parity | Giữ schema v5; thao tác occurrence nguyên tử, không thay món ở đích | Nhóm mới xem bảng Meal Planner cuối tài liệu |
| Grocery/planner sync | STATIC VERIFIED: chọn meals/ingredients/servings → batch add; chưa chứng minh auto-sync | Derived plan_groceries giữ riêng; manual CRUD/batch source selection/portions/ingredients/check/sort/share | Findings10; LabDao, GroceryBatchDialog | Persistent recipe linkage/RECIPE và timestamp grouping; chưa checkout | Populated official UI UNKNOWN; local source groups khác taxonomy official | Giữ auto-derived local, không nhận là official; xem nhóm Groceries cuối | Xem nhóm Groceries cuối tài liệu |
| Cooking/PiP | PiP dùng **video resource hướng dẫn theo platform**, autoEnter, timer400ms; chưa chứng minh recipe cooking PiP | CookingFragment steps/keep awake; không PiP | STATIC VERIFIED pip-onCreate/pip-params | Trigger/caller PiP và cooking UI cần trace | Không được gán PiP cho cooking khi chưa có bằng chứng | Tránh xây sai tính năng PiP | Runtime PiP UNKNOWN |
| Tài khoản/sync | User service có FirebaseUser; login Google/email quan sát trực tiếp | Local SQLite/prefs, không tài khoản/cloud | RUNTIME VERIFIED login; user-service inventory | Auth/server/sync/conflicts/collaboration | Không login/settings account UI | Cần backend/provider được phép; không dùng token official | Đã đăng nhập Emulator; cross-device sync chưa test |
| Nutrition/AI | Nutrition Loading/throttle coroutine; Ask VM inventory | USDA qua server, optional OpenAI assistant; thiếu key trả lỗi | STATIC VERIFIED post-nutrition; backend/nutrition.py/app.py | Live API chưa test; đã sửa thông báo phí theo capability | Chưa chat multi-turn/stream | Ghi capabilities/phí trung thực; không nhúng key | Mock/live cần tách |
| Chia sẻ/xuất/deep link | Main xử lý VIEW host singular/join.recime.app; PDF action có Superwall | PDF/Print/FileProvider; incoming SEND; không public links | STATIC VERIFIED main-intent/post-export; RecipeExport/Manifest | Public sharing cần backend; print page ranges chưa xác minh | Chưa official export UI | Không sử dụng domain/chữ ký official | UNKNOWN runtime parity |
| Onboarding/loading/errors/subscription | Welcome video + Get started/Log in; login Google/email; paywall actions code | Không onboarding/paywall; loading/error import/editor | RUNTIME VERIFIED launch/login; STATIC VERIFIED post-cooking/export | Full onboarding/quotas/error states sau login | Cream/blue/black official vs midnight/lime app | Đã đăng nhập; không sao chép video/brand | Hai màn captured; trạng thái còn lại UNKNOWN |
| Motion/gestures/haptics | Compose runtime; animation/gestures cụ thể chưa quan sát | Native Motion enter/stagger/pop/press, animator scale | RUNTIME VERIFIED ComposeView; Motion.java | Cần recording/gesture tests, haptics chưa có bằng chứng | Chưa được đo timing/font/spacing sau login | Đo trước chỉnh motion | UNKNOWN |

Không dùng báo cáo cũ để nói cookbook/meal multi-entry/cooking/PDF/USDA/assistant đều thiếu: master đã có. Không kết luận thuật toán AI/server, database cloud hoặc chiến lược đồng bộ từ tên class/request models.

## Nhóm triển khai được chọn sau đối chiếu

1. Sửa ingredient filter whitespace/phrase với unit regression.
2. Bảo toàn số phần khi thêm món đầu slot từ detail bằng bảng meal_extras v4 sẵn có; không đổi schema/không xóa legacy meals.
3. Bổ sung bỏ công thức khỏi cookbook (giữ recipe/memberships khác) sau bằng chứng removeRecipe gốc; UI draft chọn nhiều có Lưu/Hủy.
4. Sửa mô tả chi phí API và cảnh báo xóa đúng với dữ liệu derived; ngừng tự chèn bốn công thức demo trên cài mới, giữ dữ liệu hiện hữu/catalog có nguồn.

Đây là nhóm sửa độc lập nhỏ, chưa giải quyết toàn bộ parity. Auto-derived grocery hiện hữu được ghi là sai khác cần runtime quyết định; không đổi luồng chưa đủ bằng chứng.

## Tiếp tục từ PR #5 — 23/10/2026

Baseline `15497c9`: CI PASS và CookbookMembershipTest 2/2 PASS theo tiến độ người dùng; không coi đó là PASS cho test Editor mới. Nhóm Editor lifecycle không đổi schema v4: load bản gốc một lần mỗi view, detached snapshot trước validation, save outcome trong fragment ViewModel để không bám view cũ khi recreation. Không khẳng định cơ chế draft này giống server/app gốc; đây là sửa lỗi của implementation độc lập.

Lỗi phát hiện bởi instrumentation: dock đặt height68dp làm item Material cao80dp bị clip; root và child có thể áp dụng system-bar insets hai lần. Đã đổi dock wrap_content và root consume insets sau khi áp dụng. Giữ taxonomy5tab hiện có, chưa coi là UI parity dock4tab original.

Search UI: instrumentation cho thấy hero182dp chiếm vùng đọc card khi tìm món; đã ẩn hero lúc có search/ingredient query và giữ strict click assertions. Không nới điều kiện click để che lỗi layout.

## Nhóm tiếp theo — chỉ Recipe Editor (baseline92d2e77)

Đã dùng lại ảnh `editor-filled.png` và UI map, không chạy lại REA/APK. Editor chuyển riêng sang trắng/kem, Cancel–Save cố định, Title/Images/Info/Nutrition/Tags/Ingredients/Instructions. Panel Info/Nutrition/Tags dùng native expandable controls độc lập: nội dung/transition màn con official vẫn UNKNOWN. Cỡ chữ/màu cụ thể là xấp xỉ độc lập, chưa chứng nhận pixel parity.

Photo Picker Android chọn tối đa5ảnh theo yêu cầu người dùng; add/preview/remove/replace, bản sao private8MB/ảnh, draft rotation và ordered JSON trong SQLite. Không suy cap5 của official từ chữ “5 left” (có thể là quota import). SQLite v5 thêm duy nhất imagesJson, giữ image làm cover tương thích và không DROP dữ liệu v4. Tests cũ dùng DATABASE_VERSION; có fixture v4→v5 riêng.

Build/unit/lint PASS; 19/19 JVM và 21/21 instrumentation PASS (0 failures/errors); xem TEST_RESULTS.md. Chưa chuyển sang Recipe Detail.


## Recipe Detail — tiếp từ9e94d5b

| Chức năng | Hành vi ReciMe gốc | Hành vi ứng dụng hiện tại | Bằng chứng xác minh | Phần còn thiếu | Sai khác UI/UX | Hướng triển khai | Trạng thái kiểm thử |
|---|---|---|---|---|---|---|---|
| Gallery Detail | UNKNOWN runtime | Tối đa5 ảnh, cover legacy, trước/sau, empty; giữ selection khi Edit/rotation | Recipe.photoPaths, RecipeGallery, DetailFragment | Chưa chứng minh gesture/gallery official | Layout Detail cũ + gallery độc lập | Không đoán UI official | PASS trong bộ31 instrumentation; official UNKNOWN |
| Edit/Favorite/Delete | Detail official chưa truy cập được | Refresh recipe sau Edit; favorite chỉ UPDATE cột riêng; delete chỉ rời màn khi DB thành công, lỗi có thể retry; dọn ảnh owned không còn tham chiếu | DetailFragment, LabDao.setFavorite, DetailActionState | Process death khi mutation chưa kiểm chứng | Chưa đo official | Không đổi schema v5 | PASS trong bộ31 instrumentation; official UNKNOWN |
| Cookbook/Plan/Groceries/Cooking | Evidence APK cũ, runtime Detail UNKNOWN | Giữ actions thật; plan capture id/portions trước dialog; ingredients theo portions | Source + tests liên quan | Cooking/PiP parity official chưa xác nhận | Không thay toàn layout theo suy đoán | Giữ hành vi độc lập đã có | PASS trong bộ31 instrumentation; official UNKNOWN |
| PDF/Print/AI | Static findings cũ; runtime Detail UNKNOWN | PDF local + FileProvider grant; system print; AI gọi backend riêng, validate câu hỏi, giữ kết quả qua rotation, hiển thị lỗi thật | RecipeExport, LocalAiClient, DetailFragment | AI provider/key live và in giấy chưa kiểm chứng | Chưa đo official | Không dùng private API/key official | PASS trong bộ31 instrumentation; official UNKNOWN |


## Meal Planner — tiếp từ3a3179f

| Chức năng | Hành vi ReciMe gốc | Hành vi ứng dụng hiện tại | Bằng chứng xác minh | Phần còn thiếu | Sai khác UI/UX | Hướng triển khai | Trạng thái kiểm thử |
|---|---|---|---|---|---|---|---|
| Lịch tuần/4 bữa | RUNTIME VERIFIED: tuần/previous-next/empty; STATIC VERIFIED: BREAKFAST/LUNCH/DINNER/SNACK | 7 header ngày, 4 nhóm Breakfast/Lunch/Dinner/Snack, nhiều món/bữa, thao tác riêng mỗi occurrence | REA findings mục9; BoardFragment/BoardAdapter | Runtime lịch có món official chưa quan sát | White/cream độc lập; không chứng nhận pixel parity | Giữ ListView/BaseAdapter và keys lịch cũ | PASS trong39cases (38 rộng +1 retry); official UNKNOWN |
| Chuyển món | STATIC VERIFIED: startDrag/completeDrop/moveMealItemToDay theo ID | Dialog ngày/bữa và native drag handle→ngày/bữa, edge scroll; cập nhật nguyên tử, không thay món ở đích | Evidence meal-drag/drop/move đã lưu; LabDao.editMeal/moveMeal | Gesture/timing/rollback server official UNKNOWN | Gesture độc lập theo Android View | Không gọi lại REA, không đoán server | PASS trong39cases (38 rộng +1 retry); official UNKNOWN |
| Ngày bắt đầu tuần | STATIC VERIFIED: user preference fallback MONDAY; cloud save UNKNOWN | Lưu cục bộ1–7 trong planner preferences; giữ tuần khi rotation/backstack | Findings mục9; PlannerWeek, BoardFragment | Cloud preference sync chưa triển khai | Dialog native độc lập | Không đổi database dates | PASS trong39cases (38 rộng +1 retry); official UNKNOWN |
| Khẩu phần/legacy/migration | Runtime official UNKNOWN | Portion từng món; đổi món giữ override; legacy meals chuyển sang extras chỉ khi sửa/move, giữ destination, ảnh/cookbook | LabDao transactions + migration tests | Process kill giữa thao tác chưa kiểm chứng | Không tuyên bố parity chi tiết | Không đổi schema SQLite v5 | PASS trong39cases (38 rộng +1 retry); official UNKNOWN |
| Groceries khi move/delete | **UNKNOWN: official continuous auto-sync**; APK chứng minh chọn nguyên liệu gửi batch | Derived groceries của app mình tính lại trong cùng transaction; manual giữ riêng; checked giữ khi itemKey còn | REA findings mục10; syncPlanGroceries + DB tests | Batch local đã thêm ở nhóm Groceries; server protocol vẫn UNKNOWN | Hành vi local khác biệt đã có từ baseline | Không nhận auto-sync là hành vi official | PASS trong39cases (38 rộng +1 retry); official UNKNOWN |


## Groceries — tiếp từc0ffc04

| Chức năng | Hành vi ReciMe gốc | Hành vi ứng dụng hiện tại | Bằng chứng xác minh | Phần còn thiếu | Sai khác UI/UX | Hướng triển khai | Trạng thái kiểm thử |
|---|---|---|---|---|---|---|---|
| Danh sách/empty/grouping | RUNTIME VERIFIED:0 items/No groceries yet/Add; STATIC VERIFIED: group builder + AISLE/RECIPE/ALPHABETICAL/TIMESTAMP | White/cream, nhóm hàng/nguồn/trạng thái, số lượng/đơn vị, checkbox/gạch dòng đã mua, empty hướng dẫn | Findings10, UI map, GroceryPresentation/BoardAdapter | Recipe-specific và timestamp grouping official chưa đạt; classification server UNKNOWN | Layout độc lập, không pixel parity | Giữ ListView/BaseAdapter; không đoán thuật toán aisle | PASS:31 JVM;40cases qua39 rộng +1 retry; official UNKNOWN |
| Chọn công thức/bữa/ingredients batch | STATIC VERIFIED: meal IDs, ingredients, serving override/ratio rồi gửi batch repository | Chọn nhiều recipe/meal occurrences, khẩu phần và checkbox từng nguyên liệu; transaction thêm bản sao độc lập | Findings10 + GroceryBatchDialog/GroceryBatchDraft/LabDao | Chưa gọi server official hoặc đồng bộ cloud | Native dialog hai bước độc lập | Không thay source recipe/meal/ảnh/cookbook | PASS:31 JVM;40cases qua39 rộng +1 retry; official UNKNOWN |
| Manual CRUD/checked | STATIC VERIFIED: stage input/dedup, checked IDs | Manual add/edit/delete, validation, giữ draft qua rotation; check chỉ UPDATE cột; plan check dùng stable itemKey | GroceryEditorDialog, setGroceryChecked | Manual multi-line parser/runtime official chưa đối chiếu | Form và menu độc lập | Giữ v5 và checked khi sửa tên/số lượng | PASS:31 JVM;40cases qua39 rộng +1 retry; official UNKNOWN |
| Aggregation/source/Planner | Exact aggregation/reconciliation server UNKNOWN; continuous sync chưa chứng minh | Merge tên/đơn vị tương thích trong mục independent chưa mua; không merge vào bought/planned; unknown không đoán lượng; rollback khi batch lỗi | GroceryAggregator + DAO transactions | Không có persistent attribution per-recipe cho batch trong v5 | Bản sao batch được ghi rõ thuộc mục tự thêm; không tự đi theo plan | Giữ auto-derived hiện tại riêng; không đổi schema | PASS:31 JVM;40cases qua39 rộng +1 retry; official UNKNOWN |
| Sort/share/checkout | Static sort enum + sharing; merchant backend UNKNOWN | Sort nhóm/A-Z/nguồn/chưa mua; lưu sort; checklist đúng nhóm/amount/checked/source; không checkout | GroceryPresentation, BoardFragment | RECIPE/TIMESTAMP grouping và merchant integration chưa có | Không nhận sort taxonomy local tương đương official | Không thêm merchant giả | PASS:31 JVM;40cases qua39 rộng +1 retry; official UNKNOWN |

## Home / Cookbooks / Discover / Dock — sau b6a2204
| Chức năng | ReciMe gốc | Hiện tại | Bằng chứng | Còn thiếu | UI/UX | Hướng triển khai | Kiểm thử |
|---|---|---|---|---|---|---|---|
| Dock / Import | RUNTIME VERIFIED: Recipes, Meal Plan, Groceries, Discover; + mở lựa chọn nhập | 4 tab; + viết hoặc Import link/ảnh/text/AI; incoming SEND giữ luồng Import | UI map: home.png/xml, main-import-options.png/xml; MainActivity | Deep-link/cloud không thuộc nhóm này | Dock đứng yên khi đổi tab, lưu trạng thái mỗi root; UI timing official UNKNOWN | Java Fragment/back stack; không menu AI giả | LOCAL PASS; xem TEST_RESULTS |
| Home | Cookbooks / All Recipes, card 2 cột, count, + | Nền cream/white; 2 chế độ grid; search, personal-only recipes, favorite/ingredient filters | home.png/xml, cookbook-success.png/xml; RecipesFragment/LibraryScope | Quota/account UI cần backend; không hiển thị số quota giả | Card độc lập; không sao chép brand/font/ảnh; pixel parity UNKNOWN | Giữ catalog rows nguyên v5, phân scope lúc đọc | LOCAL PASS; xem TEST_RESULTS |
| Cookbooks | Empty/open/menu Select recipes/Edit title/Delete quan sát; hiệu ứng từng action chưa runtime verified | Create/validation/duplicate error; open/rename/delete; batch select/unselect personal memberships; không xóa recipe | cookbook-options.png/xml; CookbookDialog, LabDao.selectCookbookRecipes | Icon/access/collaboration official UNKNOWN | Dialog draft + selection/async-save giữ rotation; rename cập nhật heading | Transaction chỉ thay membership của cookbook đích, giữ các membership khác | LOCAL PASS; xem TEST_RESULTS |
| Discover | Search/popular/recipe of day/share/save thấy runtime; thành công detail/save chưa xác minh | Catalog offline có nguồn, search + ingredient/favorite filter, Detail; tách personal | discover.png/xml; catalog assets/LibraryScope/RecipeAdapter | Dynamic recommendations, creator search, feed server UNKNOWN/blocked | Ghi catalog offline; không dựng trending/recipe-of-day giả | Dùng dataset hiện hữu và attribution; không đoán API official | LOCAL PASS; xem TEST_RESULTS |

Build +32 JVM PASS; lint0 lỗi/182 warnings. Instrumentation49 ca riêng biệt đã PASS qua các lượt: broad48/49, PlannerWorkflow sửa harness3/3. Không phải một invocation49/49 xanh. LibraryUi4/4, CookbookSelection2/2 và GroceryUi4/4 PASS trong lượt broad; không retry tự động. Official UI/server parity vẫn UNKNOWN ngoài evidence đã có.

## Import reliability — nhóm sau 18c8b31
| Nhóm | Chứng cứ ReciMe chính thức | App độc lập sau thay đổi | Giới hạn | Kiểm thử |
|---|---|---|---|---|
| Website Recipe JSON-LD | STATIC VERIFIED: URL/HTML qua repository; server parsing UNKNOWN | Parser schema.org/Recipe, nested @graph/mainEntity, ingredient dạng mảng/chuỗi; HTTPS public, chặn private hosts | Không có hỗ trợ mọi trang/video, không dùng endpoint official | JVM type/host + Android JSON-LD tests; chờ Emulator |
| Cảnh báo import trùng | UNKNOWN original duplicate handling | Trước Editor, phát hiện same HTTPS source hoặc title+ingredients+steps, bỏ qua catalog; chọn mở/nhập bản sao/hủy, không overwrite | Người dùng có thể chủ động lưu bản sao; không suy hành vi official | JVM DuplicateChecker + Android ImportDuplicateUiTest; chờ Emulator |
