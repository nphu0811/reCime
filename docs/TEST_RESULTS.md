# Kết quả kiểm thử — Recipe Editor

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
