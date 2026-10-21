# Trạng thái bàn giao MyRecipeLab — 22/10/2026

Đây là MVP offline chạy thật bằng Java/XML, mở trực tiếp trong Android Studio. Phân biệt trạng thái theo phạm vi kiểm thử dưới đây; không coi việc biên dịch là bằng chứng đã thử mọi tương tác.

## Kết quả thực thi

| Lệnh | Kết quả thực tế | Bằng chứng |
|---|---|---|
| `gradlew.bat assembleDebug` | PASS, APK debug tạo được | `evidence/verification-final-pass.log`, `evidence/delivery-check.log` |
| `gradlew.bat testDebugUnitTest` | PASS **9 tests**, 0 failure/error/skip | `evidence/tests/unit.xml` |
| `gradlew.bat connectedDebugAndroidTest` | PASS **8 tests**, 0 failure/error/skip, Medium_Phone Android17/API37 x86_64 | `evidence/tests/instrumented.xml`, `evidence/verification-final-pass.log` |
| `gradlew.bat lintDebug` | PASS, **0 errors**, warnings còn lại (text chưa tách strings, autofill/style, dependency updates, ExifInterface…) | `evidence/tests/lint.txt` |
| `adb install -r …app-debug.apk` + `am start -n com.example.recime/.MainActivity` | Success, app mở trên emulator | Các screenshot REA và `evidence/rea-myrecipelab-package.json` |
| REA 6.7.0 setup / doctor / ADB client/devices/package resolution | Đã gọi thực sự, Codex registration ready; ReciMe không cài trên AVD | `evidence/rea-*.json` và `REA_ANALYSIS.md` |

Sau khi định dạng mã bằng google-java-format1.28.0 đã chạy lại assembleDebug + unit tests + lint: PASS (`delivery-check.log`). Bộ 8 test thiết bị pass trước bước định dạng; không thay đổi logic sau lượt pass này. Formatter lấy từ [release chính thức](https://github.com/google/google-java-format/releases/tag/v1.28.0), SHA256 `32342e7c1b4600f80df3471da46aee8012d3e1445d5ea1be1fb71289b07cc735`, chỉ chạy local, không là dependency app.

Các lượt lỗi được giữ để minh bạch: lỗi import R trùng ML Kit (`build.log`), thay đổi intent action làm ActivityScenario không khớp (`verification-first-failure.log`), matcher test trùng ô search/title và lint constant (`verification-second-failure.log`). Đã sửa và chạy lại đến PASS, không bỏ qua test hoặc tắt lint errors.

## Ma trận tính năng

Đường dẫn dưới đây tính từ `app/src/main/java/com/example/recime/`, trừ khi ghi rõ.

| Tính năng | Trạng thái | Mã / bằng chứng / giới hạn |
|---|---|---|
| Java + XML, AndroidX/Material, BottomNavigation + fragments | Implemented & tested | `MainActivity.java`, `ui/*Fragment.java`, `res/layout/*`; LabUiTest và screenshots |
| Grid ảnh và 4 món mẫu offline | Implemented & tested | RecipesFragment, RecipeRepository.seed, food_art.xml; sample illustration riêng, không dùng ảnh/logo ReciMe |
| Tạo/đọc công thức, form review, source và các bước | Implemented & tested | EditorFragment, DetailFragment, Recipe entity; LabUiTest manualImport… + OcrUiTest |
| Sửa/xóa công thức + cascade meal | Implemented & tested (data); UI implemented not tested | LabDatabaseTest xác minh update/delete/FK; UI có form Sửa và dialog Xóa, chưa có test riêng nút sửa/xóa |
| Yêu thích | Implemented not tested | DetailFragment favorite flag, RecipesFragment filter; lưu Room, chưa có assertion UI riêng |
| Cookbook | Partial | Nhãn bộ sưu tập nhập/sửa theo món và dropdown lọc. Chưa có cookbook trống, nhiều cookbook/món, màn quản lý/đổi tên hàng loạt |
| Tìm tên/tag/nguyên liệu không phân biệt dấu | Implemented & tested | Units.key + RecipesFragment.filter; search test UI và unit test normalization |
| Discover | Implemented & tested (local) | Tìm trong thư viện offline; LabUiTest tabs; chưa có dịch vụ tìm web hoặc recommendation |
| Nhập thủ công / dán text VI/EN | Implemented & tested | RecipeParser + Editor/Import; 9 unit tests gồm g/kg/ml/l/tsp/tbsp/cup/quả/củ/lạng và phân số |
| ACTION_SEND text và ảnh | Implemented & tested | MainActivity + manifest; LabUiTest/OcrUiTest gửi intent thật vào activity |
| ACTION_SEND URL | Implemented not tested end-to-end | Route URI/text tới ImportFragment URL box; chưa gọi website thật xuyên suốt từ Sharesheet |
| Photo Picker | Implemented not tested qua UI hệ thống | ImportFragment PickVisualMedia; fallback do AndroidX; shared-image path được test riêng |
| Camera intent/FileProvider | Implemented not tested bằng camera thật | TakePicture + FileProvider; lỗi không có camera được xử lý. Test dùng file tự tạo, không giả vờ đã chụp camera |
| OCR bundled ML Kit → sửa text → parser → review → Room + ảnh private | Implemented & tested | ImportIntegrationTest đọc ảnh synthetic; OcrUiTest kiểm tra toàn luồng và giữ draft qua recreate; chưa đo accuracy tiếng Việt/viết tay |
| URL HTTPS Recipe JSON-LD | Partial | PublicWebExtractor: OkHttp + Jsoup + JSON-LD, graph/HowToSection support, timeout20s/body2MB/redirect3 lần. WebRecipeTest test fixture tự tạo; chặn private/social đã test. **Chưa thử fetch thành công website ngoài trên thiết bị** |
| Lỗi URL và thử lại | Implemented & tested | LabUiTest invalid-url xác nhận thông báo và nút retry enabled |
| Social caption/screenshot fallback | Implemented & tested cho text/ảnh | Chặn social URL với thông báo; không tự trích xuất video/caption từ nền tảng |
| Video/audio/speech/LLM | Unsupported | ExtractionAdapter là interface mở rộng; chưa có adapter, không nút giả |
| Meal plan tuần 3 bữa/ngày, trước/sau tuần, thay/bỏ món | Implemented & tested cho chọn/lưu/đổi tuần | BoardFragment + Meal; PlannerUiTest chọn món/recreate/tạo groceries; data cascade tested. Chưa test UI riêng mọi nhánh xóa/thay món |
| Giỏ từ công thức theo portions | Implemented & tested | DetailFragment.scaled + Repository.addIngredients; LabUiTest và domain tests |
| Giỏ từ meal plan | Implemented & tested | BoardFragment.planGroceries; PlannerUiTest. Snapshot cộng thêm có xác nhận, chưa tự sync ngược khi sửa lịch |
| Gom ingredient compatible và nhóm hàng | Implemented & tested | GroceryAggregator.merge/category; unit tests khác dimension/count; không gộp lượng không biết và không đoán đồng nghĩa |
| Checkbox, thêm/sửa/xóa tay groceries | Implemented & tested (data); UI implemented not tested | LabDatabaseTest grocery persistence/update/delete; BoardFragment dialog/check/long click chưa có test UI riêng |
| Chia sẻ checklist | Implemented not tested với ứng dụng nhận | Android ACTION_SEND chooser, không đặt hàng thực tế |
| Portions và chuyển unit cùng dimension | Implemented & tested | Units, Ingredient.scale, DetailFragment; unit tests baseline, density refusal, custom cup; UI test từ200g→300g |
| Cấu hình tsp/tbsp/cup | Implemented & tested cho converter; settings UI not tested | SharedPreferences, dialog validate positive finite values; default5/15/240ml; lạng100g |
| Nutrition tự nhập, nguồn, thiếu data | Partial | Recipe nullable macros + Editor validation + Detail display. Đã build/visual layout, chưa test UI nutrition; **không có tính tự động/ước tính từ ingredient dataset** |
| Room + Repository + ViewModel/LiveData | Implemented & tested | LabDatabaseTest reopen + CRUD/cascade; UI lifecycle recreate tests; không main-thread DB |
| Loading/error/empty/retry | Implemented & tested một phần | Empty grid/grocery screenshots; invalid URL UI; OCR success; camera denied/corrupt image/offline retry chưa có test riêng |
| Backend bắt buộc | Unsupported / không cần | App mở và CRUD offline, không có backend, không cần key |
| Cloud sync, auth, ordering, full backup | Unsupported | Chưa triển khai, không có nút giả; Android hệ thống backup giữ cấu hình từ template |

## Test nghiệp vụ cụ thể

`RecipeDomainTest`: portionsAlwaysScaleOriginalAndDoNotMutate; metricAndConfiguredVolumeConversions; refusesMassVolumeWithoutDensity; refusesDifferentCountUnits; refusesInvalidPortions; vietnameseAndEnglishUnitsAndFractions; parserPreservesUnknownQuantitiesAndSeparatesSteps; groceryAggregationNormalizesCompatibleDimensionsOnly; searchNormalizesVietnameseAccents.

8 test thiết bị nằm trong LabDatabaseTest (1), ImportIntegrationTest (2), LabUiTest (2), OcrUiTest (1), PlannerUiTest (1), importing.WebRecipeTest (1). Không tính test cộng2+2 hoặc context template; chúng đã được bỏ, bản gốc còn trong evidence/original-project.

## Screenshots thực tế

REA `capture-adb-screen` trên app tự xây đã cài trên emulator; không phải mockup/render HTML. Các file1080×2400 và SHA256 ở `evidence/screenshot-*.json`:

- `evidence/screenshots/recipes/screen.png`: grid4 món mẫu, tìm kiếm/cookbook/favorite và bottom nav.
- `evidence/screenshots/detail/screen.png`: chi tiết, khẩu phần, nguyên liệu và chuyển giỏ.
- `evidence/screenshots/import/screen.png`: manual/photo/camera/URL/text và review.
- `evidence/screenshots/planner/screen.png`: tuần19–25/10/2026,3 bữa/ngày (ảnh trạng thái trống).
- `evidence/screenshots/groceries/screen.png`: empty state và nhập/chia sẻ checklist.

Đã mở từng screenshot kiểm tra chữ/nút/bố cục không tràn màn hình1080×2400. Chưa xác minh đầy đủ máy nhỏ, tablet, font scale lớn hoặc mọi phiên bản Android24–36.

## Việc tiếp theo

Ưu tiên test camera/Photo Picker trên máy thật, test live web Recipe JSON-LD có phép sử dụng, test UI nutrition/settings/CRUD nâng cao. Sau đó thêm quản lý cookbook nhiều-nhiều, source-aware grocery sync không cộng trùng, xuất/khôi phục backup và dọn ảnh mồ côi. Nếu bổ sung dữ liệu nutrition cần có nguồn/license, portion weights và coverage rõ; nếu thiếu vẫn giữ null. Chỉ triển khai social/audio qua nguồn được phép và adapter có thật. Khi có APK ReciMe hợp lệ mới bổ sung phân tích static/runtime REA, không hồi tố các suy luận hiện tại thành CONFIRMED.
