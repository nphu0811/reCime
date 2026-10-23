# Kết quả kiểm thử — Recipe Editor và Detail

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
