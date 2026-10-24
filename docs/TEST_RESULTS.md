# Kết quả kiểm thử — reCime Android parity

## Hiện hành sau launch + cấu hình key — 24/10/2026, product code c6a22de

- **Build PASS, 44/44 JVM PASS, lint 0 errors/230 warnings. Toàn bộ 75/75 instrumentation PASS, 0 failures/errors/skips trong một invocation (Gradle 8m25s; XML test time 489.846s), Medium_Phone Android17.** Không tổng hợp kết quả nhiều retry thành một lượt xanh. Backend **31/31 unittest PASS** (4.762s); CI bổ sung image-provider contracts.
- Trước lượt rộng, focused7 gồm Library5 và ImageAiImport2: lượt đầu6/7 do ActivityScenario nhận hai Activity instances lúc launch từ task đang có, không có crash app. Test mới dùng explicit NEW_TASK|CLEAR_TASK để sở hữu task, không clear dữ liệu: focused7/7 trong một lượt; full74/74 sau đó. Lượt compile trước đó có ambiguous static import, đã sửa trước đánh giá runtime. Không bỏ assertion hoặc retry tự động.
- ImageAiImportTest dùng HTTP multipart thật tới socket fixture riêng: thiếu ảnh, cancel không upload, consent, EXIF rotation/metadata stripping, delayed success/503 qua rotation, giữ edit và ảnh khi Review sang Editor. Backend tests dùng ảnh tự tạo và provider mock: missing key,8MB/40MP, ảnh sai, transparency white, orientation, no-text/refusal, schema/envelope/timeout/429, production auth. Không gọi API trả phí trong automated suite.
- **Live smoke VERIFIED:** backend development nạp `.env` do người dùng cấu hình. OpenAI structured text, image-text tiếng Việt và recipe Q&A đều HTTP200; USDA FoodData Central HTTP200/candidate-complete cho100g rice raw. Ảnh recipe tự tạo Canh đậu trả đúng200g đậu phụ,300ml nước,2bước và2khẩu phần; Q&A trả200g đậu phụ. Chỉ chứng minh các ví dụ nhỏ/kết nối/schema, không chứng nhận accuracy mọi ảnh, matching USDA, định lượng hoặc an toàn thực phẩm. Health chỉ báo cấu hình.
- Full Android account/cloud contracts chạy cùng source FastAPI trên port QA8766/database `.tools/continuation-qa-cloud.db`, remote keys rỗng. Sau test dừng đúng PID/executable/command line, trả adb reverse8765→8765 về development backend có key. Không chạm server data người dùng để chạy account fixtures.
- Trước QA lưu private archive `.tools/pre-continuation-private.tar` (gitignored), SQLite integrity=ok. Sau full74, đối chiếu mọi hàng baseline: **0 hàng cũ bị thiếu/thay đổi**,547recipes và547memberships giữ nguyên; integrity=ok. App vẫn cài sau UTP (leaveApksInstalledAfterRun đã runtime verified). Default Món nhà được tạo khi app/test dùng thư viện. Phát hiện2manual groceries và cookbook QA catalog do teardown test cũ chưa dọn: bổ sung unique fixture names/teardown ở LabUiTest và SettingsCookingUiTest; nhóm teardown liên quan5/5 PASS trước sửa dock; sau full75 kiểm tra lại groceries0, chỉ thêm cookbook mặc định Món nhà, không còn QA catalog. Không sửa assertions.
- Không có am_anr/am_crash com.example.recime trong retained event buffer sau full suite. Không thay stress testing/Doze/Android13–16. App official/draft không bị mở hoặc xóa.
- QA trực tiếp Photo Picker → ML Kit OCR trên Emulator đọc đúng dấu tiếng Việt trong ảnh Canh đậu tự tạo → consent OpenAI → Android multipart/backend/provider thật → kết quả đúng → Editor có ảnh/tên/nguyên liệu/2servings. Không lưu fixture thành recipe cá nhân. Screenshot phát hiện dock hiện lại trong Editor do restore visibility giữa Fragment views, cùng ô URL nền tối/chữ khó đọc. Checkpoint c6a22de chuyển dock sang back stack có tên, bao gồm Settings trên draft, và chỉnh nền URL trắng.
- Regression mới ModalNavigationTest giữ checks ẩn dock/margin0/draft/rotation và khôi phục dock/margin về Home. Hai lượt focused8 có6PASS/2FAIL (ModalNavigation và LabUi ở cùng đường Back), diagnostic đơn vẫn FAIL cho tới khi đối chiếu source Fragment1.8.9: Predictive Back gọi onBackStackChangeCommitted, getBackStackEntryCount còn tính transitioning entry; không gọi listener onBackStackChanged ở nhánh này. Sau xử lý committed + post sang vòng UI kế tiếp, diagnostic1/1 PASS29s. Không bỏ assertions hoặc thêm retry. Chạy lại toàn bộ75 trên checkpoint mới:75/75 PASS trong một lượt, gồm Library5/5 và ModalNavigation. Lượt74/74 trước sửa dock cũng được giữ riêng trong continuation-full-regression.log.
- Home screenshot bản mới xác minh icon settings không bị cắt. Settings/Discover bản trước và Home bản mới đã quan sát trực tiếp; screenshot c6a22de final-import-light xác nhận ô URL nền trắng/chữ đọc được; final-editor-dock-hidden không còn dock, Back trả về Home có dock. Android→provider live flow đã mô tả ở trên. Pixel/motion parity official vẫn UNKNOWN.

Evidence gitignored: `.tools/continuation-final-regression.log`, `continuation-final75-summary.json`, `final75-TEST-*.xml`, `continuation-final-data-preservation.json`, `continuation-full-regression.log`, `continuation-focused-first.log`, `continuation-instrumentation.log`, `continuation-backend-tests.log`, `continuation-live-provider-check.json`, `continuation-live-image-check.json`, `continuation-live-qa-check.json`, `continuation-data-preservation.json`, `final-home.png`, `final-import.png`, `final-image-consent.png`, `final-image-ai-result.png`, `final-image-review-editor.png`, `continuation-navigation-first.log`, `continuation-navigation-second.log`, `continuation-modal-diagnostic.log`, `continuation-modal-final.log`. Không chứa key; private backup và ảnh/log không commit.


## Snapshot trước khi người dùng launch và cấu hình key — Android code c6aa498

- Baseline: workspace sạch, fast-forward lên2513e48; CI baseline run40006473676 SUCCESS. Lượt đầu không có Emulator nên không được tính test PASS. Khởi động Medium_Phone Android17, Library ban đầu4/5 (root Activity bị chọn khi dialog Lưu đang mở). Sửa root dialog và query members background: **Library5/5 PASS trong một lượt**, không bỏ assertions/retry tự động; nhóm Library/import/OCR trước đó12/12 PASS.
- Build cuối: **assembleDebug PASS, JVM44/44 PASS, lint0errors/233warnings**. Không coi warnings đã được xử lý. Sau thay đổi tùy chọn giữ APK UTP, chạy build/JVM/lint thêm lần nữa PASS4s; không đổi Android product code sau lượt rộng cuối.
- **Instrumentation toàn bộ72/72 PASS,0failures/0errors/0skipped trong một invocation6m41s**, trên Medium_Phone Android17; XML kiểm tra sau Gradle hoàn tất. Lượt rộng trước đó69PASS/3FAIL (Grocery modal root; thông báo URL; test bấm dock khi Import đang giữ draft) được lưu riêng, không đổi thành PASS. Sửa root cho dialog controls, message URL và test Back đúng luồng; chạy lại toàn bộ72, không retry có điều kiện để làm xanh.
-72 gồm Library5; Editor lifecycle/photos5; Detail UI/integrations10; Planner DB/UI/workflow10; Grocery DB/UI9; cookbooks4; catalog2; import duplicate/web/OCR/integration/JSON-LD11; Settings/Cooking/catalog-copy3; backup/migration/account contract và các regression DAO còn lại. Có legacy Roomv2, v4→v6 và v5→v6 fixture; giữ recipe/photos/membership/meals/manual/check states, WAL/FK, restore rollback và backup idempotence.
- `OcrRotationTest`: ảnh chữ do test tự tạo, trì hoãn executor rồi rotate, chạy ML Kit thật và giữ edit sau OCR; không dùng OCR response giả. `AiLaptopIntegrationTest`: local FastAPI + CLIP/TF-IDF thật; ảnh reference có sẵn trong catalog, **không chứng minh unseen-image accuracy**. Dish lookup là lựa chọn rõ sau OCR, không tự upload khi ít chữ.
- `AccountContractTest`: Android HTTP thật tới FastAPI local/database QA riêng; register/login/Keystore/session/logout/delete, backup outbox/CAS conflict/merge và record push/pull idempotency. Không dùng tài khoản/key/cloud official hoặc dịch vụ trả phí. Cloud backup không được gọi là automatic record sync Android.
- Backend cuối: **26/26 unittest PASS** (`test_cloud`, `test_health`, `test_backend`, `test_provider_contracts`),5.6s. Có auth/isolation/cascade, pagination/tombstone/conflict/idempotency, missing keys, structured JSON errors/timeout/429, request-body/chunked limit, production provider auth, redaction password trong validation error, USDA kcal-vs-kJ/missing macros/cache và provenance. OpenAI/USDA dùng mock; **live provider NOT VERIFIED**.
- Không thấy `am_anr`/`am_crash` của com.example.recime trong event log giữ lại sau test. Không thay thế stress test hoặc chứng nhận mọi thiết bị/Doze. Functional instrumentation tắt animation bằng Gradle; motion/haptics official chưa xác minh.
- **Giới hạn môi trường:** UTP mặc định gỡ app độc lập sau connected tests; package check cuối chỉ thấy com.recime.app. Đã bổ sung leaveApksInstalledAfterRun cho tương lai nhưng chưa runtime verify. Lệnh cài debug APK bị automatic approval review chặn (“blocked by policy”); chưa có manual visual QA screenshot cuối và cần người dùng Run trong Android Studio. Không chứng nhận dữ liệu cài thật của app độc lập được giữ qua cleanup UTP; data preservation chỉ đã xác minh trong fixtures. Không mở/xóa official app/draft.
- Backend QA được dừng theo PID/executable/command line đã kiểm tra; backend development đang chạy localhost8765, `.env` mặc định, health ready547, accounts true, OpenAI/USDA false. Không in key. `.env`, DB, APK, ảnh QA và logs không commit.

Evidence local gitignored: `.tools/total-baseline-library-device.log`, `total-checkpoint1.log`, `total-backup-contract.log`, `total-first-wide-summary.json`, `total-final-regression.log`, `total-final-wide-summary.json`, `total-backend-final.log`, `total-handoff-build.log`, `total-events-after-tests.log`. Lỗi focused trước đó (PDF test gọi getIntents trên main và Settings assert chưa scroll, AI root) đã sửa test đúng boundary, giữ checks output/permission/DB.

```powershell
# FastAPI QA local + adb reverse8765 phải sẵn sàng cho toàn bộ connected suite.
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest
backend/.venv/Scripts/python.exe -m unittest backend.test_cloud backend.test_health backend.test_backend backend.test_provider_contracts -v
```

## Lịch sử kiểm thử trước phiên tổng lực

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

## Import reliability — GitHub-only, sau 18c8b31
- Cập nhật PublicWebExtractor: schema.org full type/nested JSON-LD; thêm PublicWebExtractorTypeTest (JVM2), PublicWebExtractorJsonLdTest (Android3).
- ImportDuplicateChecker thuần Java nhận diện nguồn HTTPS trùng hoặc ba trường recipe nội dung trùng; thêm ImportDuplicateCheckerTest (JVM6). ImportFragment kiểm tra ở executor SQLite, hiển thị dialog mở bản cũ / nhập bản sao / hủy; không tự insert hoặc update. Thêm ImportDuplicateUiTest (Android2).
- Đây là bộ test mới, **không được tính PASS Android** cho tới khi chạy trên Emulator. GitHub CI kiểm tra compile, JVM, lint; xem kết quả workflow tương ứng commit mới.
- Lệnh kiểm tra Emulator: `./gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.recime.helper.PublicWebExtractorJsonLdTest,com.example.recime.ImportDuplicateUiTest"` (PowerShell).

## Website Import rotation — after 551500f
- Added fragment-scoped ImportWebState. A pending public-web extraction survives normal configuration changes and delivers its result to the recreated Import view. Async work does not capture Activity/Fragment/View and is cancelled on final close.
- The UI consumes each completed response once using a saved result id, so rotating after modifying the imported text does not overwrite the user's edits. URL and caption editing are disabled during fetch to keep URL/source consistent.
- New Android test ImportWebRotationTest uses an in-memory delayed ExtractionAdapter to verify pending/progress/result through rotation, plus preservation of subsequent manual edits. This is synthetic, not a live website or system process-kill test.
- GitHub CI covers compilation, unit tests and lint; this new Android instrumentation needs an Emulator run before declaring PASS.
# Cooking AI checkpoint — 24/10/2026

Backend+AI48/48, Android44JVM và9instrumentation liên quan Import/CookingAI PASS; build/lint PASS. Training LoRA25steps thật nhưng held-out JSON0/2, không production-ready. Phạm vi, các lượt FAIL đã sửa và proof: `COOKING_AI_HANDOFF.md`, `cooking_ai/evaluation/results/`. Các kết quả cũ dưới đây giữ làm lịch sử; không gọi chúng là regression mới.
