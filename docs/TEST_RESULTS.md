# Kết quả kiểm thử — reCime Android parity

Nhóm lifecycle bên dưới là lịch sử commit92d2e77; nhóm ảnh mới dùng SQLitev5, xem phần cuối.

Ngày23/10/2026, nhánh `wip/rea-recime-602`, baseline15497c9, PR#5. Không đổi SQLite version4 hoặc migration.

- Baseline CI15497c9 và CookbookMembershipTest2/2 PASS theo tiến độ được cung cấp; phiên này không phân tích lại APK.
- Build assembleDebug, assembleDebugAndroidTest, testDebugUnitTest và lintDebug: PASS trước vòng instrumentation cuối. JVM16tests; lint0errors/154warnings có sẵn.
- Instrumentation vòng đầu:12/15 PASS,3FAIL do dock item bị clip. Sửa dock wrap_content, root consume system-bar insets.
- Vòng hai:14/15 PASS; EditorLifecycleTest3/3, cookbook/DB/migration/planner PASS. Còn fail test tìm recipe vì title card bị clip khi hero chiếm không gian. Đã ẩn hero trong search results, giữ strict Espresso click.
- Vòng cuối: **15/15 PASS**,0failures/0errors, Emulator Android17. Gradle toàn lệnh BUILD SUCCESSFUL2m28s; XML report xác nhận. EditorLifecycleTest3/3, CookbookMembershipTest2/2, LabV4FeaturesTest2/2 và các test DB/migration/Planner/LabUI liên quan đều qua. Build/unit/lint trong cùng lệnh PASS.

Lệnh vòng cuối:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.recime.EditorLifecycleTest,com.example.recime.CookbookMembershipTest,com.example.recime.LabV4FeaturesTest,com.example.recime.LabDatabaseTest,com.example.recime.sql.LegacyRoomV2MigrationTest,com.example.recime.PlannerUiTest,com.example.recime.LabUiTest'
```

Logs cục bộ gitignored: `.tools/editor-validation.log`, `.tools/editor-device-tests.log`, `.tools/editor-final-tests.log`. Reports trong app/build; không commit APK/screenshots tài khoản/decompiled source. Test fixtures được cleanup; DB migrations chạy trên database riêng.

Phạm vi Editor: draft giữ qua library refresh/recreation; validation không mutate observed recipe; retry sau thiếu nutrition source; metadata favorite/catalog attribution giữ; pending save qua recreation không double insert. Không xác minh kill-process trong lúc commit, disk-full, full photo flow hoặc mọi thiết bị/font scale. Live backend/provider và toàn bộ import/OCR suite không nằm trong nhóm này.

## Recipe Editor + photos — baseline92d2e77

- Scope: chỉ Editor. Theme trắng/kem riêng; Cancel–Save cố định; Images/Info/Nutrition/Tags/Ingredients/Instructions. Không sửa Recipe Detail.
- Android Photo Picker (ImageOnly), cap5tổng ảnh, giới hạn picker theo số chỗ còn lại; 1slot dùng single-picker. Không thêm quyền đọc toàn bộ thư viện ảnh.
- URI được đọc sang bản sao private `files/photos/editor_*.img`, cap8MB/ảnh, kiểm tra decode, rollback toàn batch khi có lỗi. Hủy draft dọn staged copies; sau commit chỉ dọn bản sao do Editor tạo khi không còn recipe nào tham chiếu.
- SQLite5: `ALTER TABLE recipes ADD COLUMN imagesJson TEXT NOT NULL DEFAULT '[]'`; image cover cũ vẫn nguyên và tương thích các consumer. Không DROP/rebuild bảng. Fixture4→5 kiểm tra recipe/favorite/credit, cookbook membership, legacy/extras meals, checked manual groceries và tombstone; ordered photos đọc lại sau reopen.
- JVM: RecipePhotoModelTest3cases mới (legacy cover, ordered detached selection, sixth photo rejection), tổng **19/19 PASS**.
- Instrumentation: **21/21 PASS**, 0 failures/errors/skipped trên Emulator Android17; gồm Editor lifecycle/photos, migration, cookbook, planner, OCR và catalog. XML reports xác nhận. Build assembleDebug, testDebugUnitTest, lintDebug và connectedDebugAndroidTest cùng lệnh PASS (3m6s). Lint: 0 errors/162 warnings; không coi warning baseline đã được xử lý.
- Photo tests stub **result intent boundary** của picker; UI/Activity Result contracts, content URI streams, decode/private files, save/reopen/rotation là thật. Không coi đó là kiểm thử mọi UI/provider/cloud của system picker. Ảnh fixture màu do test tự tạo.
- UI screenshot kiểm tra cục bộ: app cache `qa/editor-photos.png`, không commit ảnh vào git. Native picker và pixel-perfect parity của các màn con vẫn không được chứng nhận.

Lệnh nhóm ảnh: dùng lệnh lifecycle phía trên và bổ sung `EditorPhotosTest`, `sql.PhotoV5MigrationTest`, `OcrUiTest`, `CatalogDatabaseTest` vào class filter. Log cục bộ: `.tools/editor-photos-release-check.log`.

Kiểm tra QA sau bộ cuối: cài APK debug và chạy trực tiếp `EditorPhotosTest` bằng AndroidJUnitRunner: 2/2 PASS (44.532s). Đã mở screenshot sau recreation, xác nhận toolbar/cream-white theme, 4 ảnh và sections hiển thị ổn định; ảnh fixture tự tạo, không commit.


## Recipe Detail — baseline9e94d5b

Không sửa Editor hoặc schema SQLite v5. Thêm RecipeGalleryTest3 JVM cases: thứ tự/tối đa5, khôi phục selection theo path khi Edit, clamp khi xóa ảnh và legacy cover. DetailUiTest kiểm tra gallery với pixel ảnh fixture tự tạo, rotation, Edit thật→Detail, empty/deleted state, favorite không mutate observed recipe/không ghi đè concurrent edit, cookbook, hủy/xác nhận Delete, lỗi SQL và retry sau rotation, scaled groceries và Cooking Mode. PlannerUiTest giữ kiểm tra portions→Meal Plan.

DetailIntegrationTest: PDF được tạo thật/đọc bằng PdfRenderer và ContentResolver, kiểm tra read grant (chỉ stub chooser để không gửi ra app khác); Print mở UI `com.android.printspooler` thật rồi Back, không in giấy; AI dùng HTTP503 fixture localhost, kiểm tra câu hỏi trống và lỗi backend qua rotation. Không dùng key/live provider hoặc API official. Fixtures/rows/files/config endpoint được cleanup/restore.

Vòng đầu compilation test FAIL vì import Espresso/ViewActions wildcard trùng closeSoftKeyboard; đã dùng import cụ thể. Vòng thiết bị đầu:10/11 PASS; Cookbook test không tìm được hàng ngoài màn hình của ListView. Đã sửa bằng Espresso.onData, giữ kiểm tra DB/membership/delete. Không thay code sản phẩm để bỏ qua assertion.

Vòng cuối: **BUILD SUCCESSFUL8m40s**. assembleDebug + testDebugUnitTest + lintDebug + connectedDebugAndroidTest PASS. XML xác nhận **22/22 JVM, 31/31 instrumentation PASS**,0 failures/errors/skipped; gồm Editor/migrations/cookbooks/planner/database/OCR/catalog và10 Detail cases. Lint0errors/162warnings (bằng baseline9e94d5b). Logs cục bộ gitignored: `.tools/detail-build.log`, `.tools/detail-second-tests.log`, `.tools/detail-final-tests.log`. Đã đọc XML reports sau khi Gradle hoàn tất.

QA: screenshot sau recreation được lưu tạm ở app cache `qa/detail-gallery.png`, kéo về `.tools/detail-gallery-preview.png` và mở kiểm tra. Thấy đúng ảnh fixture thứ5, chỉ số5/5, previous enabled/next disabled, title/actions không bị che; ảnh màu tự tạo và không commit git.

Lệnh vòng cuối:
```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.recime.DetailUiTest,com.example.recime.DetailIntegrationTest,com.example.recime.EditorLifecycleTest,com.example.recime.EditorPhotosTest,com.example.recime.sql.PhotoV5MigrationTest,com.example.recime.CookbookMembershipTest,com.example.recime.LabV4FeaturesTest,com.example.recime.LabDatabaseTest,com.example.recime.sql.LegacyRoomV2MigrationTest,com.example.recime.PlannerUiTest,com.example.recime.LabUiTest,com.example.recime.OcrUiTest,com.example.recime.CatalogDatabaseTest'
```


## Meal Planner — baseline3a3179f

Giữ SQLite v5; không sửa Editor/Recipe Detail/recipe images. PlannerWeekTest4 JVM cases: bảy ngày đầu tuần, giao năm/tháng, DST và next/previous, giữ input, legacy meal keys/English labels và validation. PlannerDatabaseTest kiểm tra move vào bữa đã có món, giữ legacy/destination/servings, checked derived groceries, manual items, recipe/ảnh/cookbook; SQL/FK hoặc sync failure rollback cả lịch và giỏ; clear chỉ tuần được chọn; move không reset serving override mới hơn snapshot.

PlannerWorkflowTest dùng touch thật để hold drag handle/drop Lunch; xác nhận đúng occurrence, tổng servings/ingredients và recreation. Dialog portions/move giữ input/date/type sau recreation; move qua tuần rồi remove đúng món. Week-start Sunday được giữ qua activity mới, tuần đang xem giữ qua recreation và Detail backstack; 7 headers/28 add slots. UI sử dụng dữ liệu recipe test có tên riêng, không dữ liệu giả trong sản phẩm. Prefs/recipe rows được cleanup/restore.

PhotoV5MigrationTest được mở rộng: sau migration4→5, move legacy English Lunch sang ngày khác, giữ món extras, planned amount/check state, photos/cookbook/manual groceries/tombstone và reopen. Các migration cũ tiếp tục chạy trong bộ regression.

Vòng thiết bị đầu9/10 PASS; lỗi test chọn dropdown bữa không chỉ định popup. Đã chỉ định dropdown và RootMatchers.isPlatformPopup. Retry cold launch gặp navigation chưa chuyển khỏi Home; test giữ strict click và chờ animation dock ban đầu ổn định, kiểm tra Board hiển thị trước thao tác. Không bỏ assertion DB/UI.

QA screenshot từ touch-drag run: app cache `qa/planner-week.png` kéo về `.tools/planner-week-preview.png`, đã mở kiểm tra lịch cream/white, ngày/bữa và Lunch4/Dinner2. Ảnh tự tạo/dữ liệu test, không commit. Runtime gesture này xác minh **app mình**, không chứng minh populated planner official.

Build/unit/lint PASS:26/26 JVM,0 lint errors/168 warnings (baseline162;6 warnings layout/accessibility/autofill mới). Retry dialog portions/move PASS1/1; cả8 Planner instrumentation mới PASS. Bộ rộng39cases:38 PASS/1 FAIL (LabUiTest còn chờ title cũ “Một tuần ngon lành”). Đã sửa strict assertion thành “Meal Plan”; targeted retry đúng case FAIL:1/1 PASS,0 failures/errors/skipped. Build/unit/lint + retry cùng lệnh BUILD SUCCESSFUL57s. Toàn bộ39cases đã qua theo vòng rộng + retry, không gọi đó là một lần full39/39 PASS. Logs cục bộ gitignored: `.tools/planner-build.log`, `.tools/planner-first-tests.log`, `.tools/planner-dialog-retry.log`, `.tools/planner-dialog-retry2.log`, `.tools/planner-final-tests.log`, `.tools/planner-final-retry.log`; summary vòng rộng giữ ở `.tools/planner-wide-summary.json`. Official continuous grocery auto-sync, cloud week-start và gesture parity vẫn UNKNOWN.

Lệnh bộ rộng: dùng class filter Recipe Detail phía trên, bổ sung `com.example.recime.PlannerDatabaseTest,com.example.recime.PlannerWorkflowTest`. Retry chỉ assertion title đã sửa, không đổi code sản phẩm sau bộ rộng:
```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.recime.LabUiTest#tabsHaveRealContentAndInvalidUrlHasRecoverableError'
```


## Groceries — baselinec0ffc04

Không đổi schema SQLite v5, Editor, Recipe Detail hoặc cơ chế auto-derived Planner. GroceryDomainTest5 JVM cases bổ sung (tổng31): source snapshot/servings/ingredient selection, meal-occurrence identity và tránh double-select, invalid quantity/empty/overflow, groups/checklist/source/checked và incompatible units. Không suy quy đổi khối lượng↔thể tích hoặc quantity khi unknown.

GroceryDatabaseTest5 cases trên database riêng có reopen: batch g/kg/tbsp/ml giữ manual checked và plan riêng; custom category không bị ghi đè; manual CRUD/check column-only không làm mất edit; stale planned checkbox dùng itemKey sau recompute; FK/data recipe/cookbook/photo/meal giữ; SQL trigger batch failure rollback cả merge đã thực hiện; unknown chỉ dedup cùng unit và chưa mua. Không DROP/migration mới.

GroceryUiTest4 cases dùng recipe fixtures riêng: manual validation/edit/delete/check/recreation và empty sau khi xóa; chọn nhiều recipes và bỏ từng ingredient, phần4/2→batch650g; chọn meal override4→400g/30ml; giữ source/query/portions/selection qua recreation, pending commit không double-add; cancel không ghi DB, sort qua rotation, checklist đúng checked/quantity/source. Share chooser được stub để không gửi ra app khác; nội dung Intent được kiểm tra. Fixtures/prefs được cleanup/restore.

Vòng focused17cases:14 PASS/3FAIL khi Espresso đóng IME trong batch. Thêm IME Done cho search/portions và adjust-resize; retryUI3/4 PASS, case còn lại đã qua assertions dữ liệu nhưng ActivityScenario.close timeout ở PAUSED. Đã thêm kiểm tra dialog đóng/idle trước teardown và chạy lại đúng case; retry đúng case đã PASS1/1 (2m9s), gồm pending-commit recreation và dialog đóng. Không gọi lần FAIL trước là PASS. Screenshot `.tools/groceries-preview.png` đã mở kiểm tra groups/checked/amount/source; bản gốc tạm ở cache `qa/groceries.png`, không commit. Official populated grocery UI vẫn UNKNOWN.

Build/unit/lint đã qua (31 JVM,0 lint errors/173warnings). Bộ rộng40 instrumentation:39 PASS/1 FAIL,0 errors/skipped. Duy nhất PlannerUiTest#selectMealRecreateAndBuildChecklist chạm dock trước khi animation khởi động ổn định, còn ở Home nên không thấy board_list. Đã thêm chờ700ms trước strict click, không đổi code sản phẩm hoặc bỏ assertion; retry đúng case:1/1 PASS,0 failures/errors/skipped. Build +31 JVM +lint +retry cùng lệnh BUILD SUCCESSFUL1m25s. Toàn bộ40cases đã qua theo vòng rộng39PASS và targeted retry1PASS; không gọi là một lần full40/40 PASS. logs cục bộ gitignored: `.tools/groceries-build.log`, `.tools/groceries-first-tests.log`, `.tools/groceries-ui-retry.log`, `.tools/groceries-batch-retry.log`, `.tools/groceries-final-tests.log`, `.tools/groceries-final-retry.log`; summary vòng rộng: `.tools/groceries-wide-summary.json`.

Lệnh vòng rộng (40cases):
```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.recime.GroceryDatabaseTest,com.example.recime.GroceryUiTest,com.example.recime.PlannerDatabaseTest,com.example.recime.PlannerWorkflowTest,com.example.recime.PlannerUiTest,com.example.recime.DetailUiTest,com.example.recime.EditorLifecycleTest,com.example.recime.sql.PhotoV5MigrationTest,com.example.recime.CookbookMembershipTest,com.example.recime.LabV4FeaturesTest,com.example.recime.LabDatabaseTest,com.example.recime.sql.LegacyRoomV2MigrationTest,com.example.recime.LabUiTest'
```
Retry cùng build/unit/lint, class filter: `com.example.recime.PlannerUiTest#selectMealRecreateAndBuildChecklist`. Không chạy lại APK analysis hoặc các integrations AI/Print/OCR không đổi trong nhóm này. Bộ40 có9cases Groceries mới,31regressions liên quan. Official aggregation/grouping/source reconciliation và merchant backend vẫn UNKNOWN; không có merchant checkout giả.

## Home / Cookbooks / Discover / Bottom Navigation — sau b6a2204

- Debug build **PASS**, **32 JVM tests PASS**; lint **0 errors / 182 warnings**.
- Broad49: **48 PASS / 1 FAIL / 0 errors / 0 skipped**. Ca lỗi: PlannerWorkflowTest#portionsAndMoveDialogsRetainDraftThenRemoveRecomputesOnlyPlannedItems (root activity thiếu focus khi mở menu Bỏ món).
- Sau sửa test input/root, chạy **toàn bộ PlannerWorkflowTest: 3/3 PASS**, kèm assembleDebug/testDebugUnitTest/lintDebug. Đủ **49 ca riêng biệt đã có PASS** qua hai lượt; **không có một invocation49/49 xanh**, không retry tự động. Chỉ class Workflow thay đổi giữa hai lượt cuối; product code và các nhóm khác giữ nguyên.
- Trong broad: LibraryUi4/4 (scope personal/catalog, grid/open/Back, search/ingredient filter, tab+rotation, Cookbook create/validation/draft/select/unselect/rename/delete, +Import, URL draft, Photo Picker cancel và lỗi SEND image); CookbookSelection2/2 (transaction/membership khác/catalog/legacy image/reopen v5 và invalid selection rollback).
- GroceryDatabase5/5 + GroceryUi4/4 PASS trong broad. Các ca Editor lifecycle, Detail, Planner DB/UI, cookbook memberships, SQLite migrations và catalog preservation còn lại đã PASS; OcrUiTest PASS luồng **Share ảnh tự tạo tiếng Anh → OCR → review/rotation → SQLite + private photo**. Không nhận OCR mọi ngôn ngữ/ảnh thực tế hoặc live AI/provider đã được kiểm thử.

Nguyên nhân flaky được điều tra:
1. Focused13 đầu:12 PASS/1 FAIL, tap Discover để lại Meal Plan. Các lượt compile lỗi imports/test brace đã sửa trước khi đánh giá runtime.
2. Hai lượt broad49 bị dừng sau8 ca hoàn tất (7 PASS/1 FAIL mỗi lượt): Grocery click hàng mở dialog **Xóa** của long-click. Animation=0 riêng lẻ không sửa được lỗi; secure long_press_timeout=400 được giữ nguyên.
3. Đọc dependency Espresso3.7.0 cục bộ bằng javap: Tap.sendSingleTap gọi MotionEvents.sendDown (loop UI, có cảnh báo oversleep thành long press) trước sendUp. UiGestures.click dùng GeneralClickAction và UiController.injectMotionEventSequence phát DOWN/UP10ms một lần; input đi qua Android thật, không View.performClick, không retry; injection lỗi làm test FAIL. Áp dụng cho8 UI classes gồm Workflow; longClick/native drag vẫn là gesture thật.
4. Investigation3: navigation stress + Photo Picker/error2 PASS; CRUD đi qua edit/check/rotation/long-click-delete rồi lộ giả định empty toàn cục sai vì có2 grocery nền. Test nay snapshot toàn bộ manual/planned fields trước CRUD, assert giữ nguyên sau xóa fixture và assert empty/count/checked theo snapshot. **Không clear DB hoặc xóa dữ liệu nền để làm xanh test.**
5. Workflow root fix: bỏ wait700ms không còn cần cho dock, dùng helper touch và inRoot(isDialog()) cho menu/positive buttons. Giữ mọi assertion move/servings/ingredients và native drag. Nhóm sửa đã PASS3/3.

Evidence/logs gitignored: .tools/library-broad-summary.json, .tools/library-final-summary.json, .tools/library-verified-tests.log, .tools/library-workflow-final.log; .tools/library-animation-failure.log và .tools/library-no-animation-failure.log giữ hierarchy lỗi. Ảnh own-app .tools/library-final-preview.png đã xem: nền cream/white,2cột/count và active dock/section đúng, không clipping ở cấu hình Emulator hiện tại; không commit ảnh/log/APK.

Lệnh broad: gradlew.bat assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest với class filter17 nhóm trong .tools/library-final-classes.json; các nhóm: GroceryDatabase/GroceryUi, PlannerDatabase/PlannerWorkflow/PlannerUi, DetailUi, EditorLifecycle, PhotoV5Migration, CookbookMembership, LabV4Features/LabDatabase/LegacyRoomV2Migration/LabUi, LibraryUi/CookbookSelection, OcrUi/CatalogDatabase.
Lệnh nhóm sau sửa: gradlew.bat assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.recime.PlannerWorkflowTest.

Animation settings đã khôi phục đúng snapshot trước thử nghiệm: animator_duration_scale=null (default), transition_animation_scale=1.0, window_animation_scale=1.0. Không đổi long_press_timeout400, không reboot/clear official app/draft. Functional tests dùng animationsDisabled; motion timing/haptics/official populated UI, live AI/merchant/Print/backend integration không được chứng nhận trong nhóm này. Không đổi schema SQLite v5.
