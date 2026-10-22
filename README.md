# reCime — Kinetic Kitchen / Java + XML / AI local

**reCime** là ứng dụng Android độc lập với visual style Kinetic Kitchen (midnight / neon lime / coral), motion thuần Android và dữ liệu offline bằng **SQLiteOpenHelper (SQLite)**, không cần trả tiền database hoặc API. Dataset đi kèm có **547 công thức** (cộng4 món mẫu tự soạn khi cài mới), **211 công thức có ảnh thật (178 ảnh riêng biệt)** cùng nguồn, lịch sử tác giả và giấy phép. Phần lớn là công thức tiếng Việt từ Wikibooks, có cả món quốc tế; đây **không phải toàn bộ món Việt Nam**. 336 công thức còn thiếu ảnh thật. Không sử dụng mã nguồn/nhận diện/API riêng của ReciMe.

## Chạy Android Studio

1. Open thư mục `reCIme`, chọn Gradle JDK **21**, SDK **37**, Sync. Wrapper Gradle9.5.0 và AGP9.3.3 đã cấu hình; minSDK24.
2. Mở emulator hoặc kết nối điện thoại USB debugging → Run module `app`.
3. Lần mở đầu nạp dataset vào SQLite. Công thức, sửa đổi, yêu thích, lịch tuần và checklist còn sau khi khởi động lại. Database version 3 có migration giữ dữ liệu; công thức catalog đã xóa được lưu tombstone để cập nhật catalog không phục hồi chúng.
4. Thư viện, CRUD, OCR và planner không cần AI server. Lần Sync đầu cần Internet tải dependencies. APK: `app/build/outputs/apk/debug/app-debug.apk`.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

## AI miễn phí trên laptop

Xem hướng dẫn cài mới, tải model và Wi-Fi tại [backend/README.md](backend/README.md). Trên máy đã thiết lập:

```powershell
.\backend\run.ps1
# Terminal khác, khi điện thoại/emulator kết nối USB:
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8765 tcp:8765
```

Trong **Nhập → Cài đặt AI**, endpoint `http://127.0.0.1:8765`, Kiểm tra kết nối. Đây là debug build cho phép HTTP để thử local. Wi-Fi cần địa chỉ LAN laptop và bind server như backend README; không tự thay firewall. Release cần HTTPS phù hợp.

- Ảnh chữ: ML Kit OCR trên điện thoại → sửa văn bản → parser → review → lưu.
- Ảnh món: CLIP ONNX quantized khoảng89MB trên laptop → so với ảnh nguồn → chọn ứng viên/biến thể → công thức đã lưu có nguồn. Không suy ra được định lượng chuẩn của một món chỉ từ ảnh.
- Caption: model truy hồi TF-IDF tự train từ547 công thức, khoảng0.8MB. Có kiểm tra ngoài miền; chưa tích hợp LLM sinh công thức.
- TikTok: chỉ metadata/caption oEmbed chính thức khi truy cập được. Không tải video/audio hoặc vượt đăng nhập. Lượt thử live không nhận được caption; có fallback dán caption/OCR screenshot.
- Đã train model phân3 nhóm ảnh từ đầu; kiểm định45 ảnh chỉ46.67% chính xác, thấp hơn baseline53.33%. **Không dùng model này để chọn công thức**. Script/model report được giao để tiếp tục học, không công bố model chuyên gia đã đạt chuẩn.

## Thử 10 nhóm chức năng

1. **Thư viện:** ảnh thật, hero, grid, tìm không dấu, lọc cookbook và tag; dataset mở offline.
2. **CRUD:** Nhập → Viết công thức; lưu, mở chi tiết, sửa, yêu thích, xóa có xác nhận.
3. **OCR:** chọn ảnh/chụp chữ, chỉnh kết quả trước khi phân tích và lưu; nhận ảnh/text qua Sharesheet.
4. **Ảnh món:** chọn ảnh món hoặc chế độ camera ảnh món → laptop AI → xác nhận ứng viên.
5. **URL/caption:** đọc Recipe JSON-LD công khai hoặc tìm từ caption; TikTok hiện giới hạn như trên.
6. **Lịch tuần:** chọn ô sáng/trưa/tối, tìm món trong hộp chọn, đổi/bỏ món, chuyển tuần; lưu SQLite.
7. **Đi chợ:** thêm từ món/lịch; gom nguyên liệu cùng đơn vị tương thích, thêm/sửa/xóa/checkbox/chia sẻ. Mỗi lần thêm là cộng, không tự đồng bộ hoặc đặt hàng online.
8. **Khẩu phần:** tính từ lượng gốc; chưa biết số phần thì hiện mẻ công thức, khoảng định lượng giữ nguyên để review.
9. **Đơn vị:** g/kg/lạng(100g),ml/l; tsp/tbsp/cup cấu hình mặc định5/15/240ml. Không đổi g↔ml thiếu mật độ.
10. **Dinh dưỡng:** nhập tay mỗi khẩu phần kèm nguồn; thiếu dữ liệu hiển thị rõ, chưa có cơ sở dữ liệu tự tính.

## XML Preview và giao diện

Layout đã format nhiều dòng. Mở `fragment_board.xml`, `fragment_recipes.xml` hoặc `preview_app_home.xml` trong Design/Split, Build & Refresh. `tools:text`, `tools:src`, `tools:listitem` cung cấp dữ liệu thiết kế cho thành phần có text/ảnh gán động bằng Java; giữ nguyên giá trị người dùng đã thêm. `activity_main.xml` là host Fragment nên không tự hiện các màn hình động. File `preview_app_home.xml` chỉ phục vụ Preview.

Native Material Components, ripple/elevation, chuyển màn hình fade và chuyển động card; tôn trọng thiết lập tắt animation hệ thống. Không dùng GSAP web trong Android native.

## Kiểm thử và cấu trúc

```powershell
# AI server + adb reverse phải chạy cho bộ integration AI:
.\gradlew.bat connectedDebugAndroidTest
.\backend\.venv\Scripts\python.exe -m unittest backend.test_backend -v
```

- `app/src/main/java/com/example/recime/activity`: Activity; `fragment`: các màn hình Fragment.
- `sql`: SQLiteOpenHelper, DAO và repository CRUD; `model`: đối tượng dữ liệu Java (không phụ thuộc Room).
- `adapter`: RecipeAdapter (lưới món), BoardAdapter (lịch/giỏ); nhận callback, không tự gọi database hoặc điều hướng.
- `helper`: parser, quy đổi đơn vị, gom nguyên liệu, import catalog/web, UI/motion helpers.
- `AIservice`: client gọi AI laptop; model/inference Python vẫn ở `backend/`.
- `viewmodel`: LabViewModel cung cấp LiveData cho màn hình qua vòng đời; không phải entity/DAO. [Giải thích cấu trúc](docs/JAVA_STRUCTURE.md).
- `res/layout`: XML của các màn hình.
- `app/src/main/assets/catalog`: dataset, ảnh, credits; `datasets/recipe_catalog.sqlite3`: SQLite độc lập để kiểm tra/học.
- `backend`: FastAPI/ONNX/retrieval/scripts train; `tools`: thu thập và chuẩn bị dữ liệu có nguồn.
- [Trạng thái và bằng chứng](docs/IMPLEMENTATION_STATUS.md), [dataset và AI](docs/DATASET_AND_AI.md), [nghiên cứu REA](docs/REA_ANALYSIS.md), [đối chiếu10 ảnh](docs/FEATURE_MATRIX.md), [review](docs/REVIEW_V2.md).

Không ghi secrets vào repo; artifacts model, venv và cache thô bị gitignore. Quyền dữ liệu/ảnh được ghi từng mục; ảnh dùng chung nhóm món chỉ là minh họa, không chứng minh đúng biến thể. Chưa đo độ chính xác ảnh ngoài gallery, chưa có dataset đầy đủ mọi món Việt, chưa có speech/video extraction hoặc LLM chuyên gia được kiểm định.


## Kinetic Kitchen UI (redesign 2026)

Thiết kế theo hướng editorial / Gen-Z: nền đậm, lime/coral highlight, typeface sans-serif-black, ảnh món lớn, dock nổi, transition fade-slide, item GridView/ListView stagger, pop-in hero và phản hồi nhấn. Tham khảo cảm hứng trải nghiệm từ Lollypop Designathon 2026; không sao chép hình ảnh hoặc assets của website.

- App launcher: **reCime** (giữ nguyên package `com.example.recime` và tên database để nâng cấp không làm mất dữ liệu).
- Layout: `res/layout/`; màu: `res/values/colors.xml`; nền: `res/drawable/bg_*.xml`.
- Animation: `helper/Motion.java`, `res/anim/recime_*.xml`, `MainActivity.java`; chỉ native, không thêm thư viện UI animation.
- Dùng `BaseAdapter`, `GridView`, `ListView` và `SQLiteOpenHelper` đúng kiểu Android Java cơ bản.
- Khi tắt **Animator duration scale** trong Developer options, các micro-animation qua `Motion` sẽ bỏ qua (animation transaction phụ thuộc animator hệ thống).
- Xem trước `preview_app_home.xml` trong Design/Split; giao diện hoạt động thật là `MainActivity` với Fragment.

### Kiểm tra trên máy

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

GitHub Actions: `.github/workflows/android-ci.yml` build và lưu debug APK trong mục Actions → Artifacts. Bộ test Android instrumentation yêu cầu Emulator hoặc thiết bị, không được CI compile thay thế kiểm tra hiệu ứng trên máy thật. `docs/DESIGN_V3.md` ghi rõ khác biệt và checklist review.


## reCime Functional Upgrade (SQLite v4)

Nâng cấp `v4` là migration cộng thêm bảng, không đổi tên file database: cookbook thật (tạo/đổi tên/xóa, nhiều cookbook/món), nhiều món/bữa + bữa nhẹ, tự tổng hợp nguyên liệu từ meal plan (giỏ nhập tay giữ riêng), Cooking Mode, PDF/Android Print, và assistant thực qua laptop khi cấu hình API key. **Các tính năng cloud, social video/audio, nutrition tự động, grocery checkout chưa được triển khai/kiểm chứng**; không gắn nhãn "clone chính thức". Đọc `docs/EXTERNAL_INTEGRATIONS_AND_GATES.md` để biết yêu cầu chứng thực và API key. Chưa có APK ReciMe gốc để chạy REA.
