# MyRecipeLab — sổ công thức offline (Java + XML)

Ứng dụng Android độc lập để học và tiếp tục phát triển: lưu công thức, nhập văn bản/ảnh, lên lịch tuần và tạo danh sách đi chợ. Không sử dụng mã nguồn, logo, ảnh quảng cáo hay API riêng của ReciMe. Nghiên cứu có bằng chứng và giới hạn tại [docs/REA_ANALYSIS.md](docs/REA_ANALYSIS.md); đối chiếu đủ 10 ảnh tại [docs/FEATURE_MATRIX.md](docs/FEATURE_MATRIX.md).

## Mở và chạy trong Android Studio trên Windows

1. Chọn **Open** thư mục project này (`C:\Users\admin\AndroidStudioProjects\reCIme`). Tên project/ứng dụng là **MyRecipeLab**. Namespace và application ID `com.example.recime` được giữ từ project ban đầu.
2. Dùng **JDK 21**, Android SDK **37**, Gradle wrapper **9.5.0**, AGP **9.3.3** đang có trong project. Đã chỉnh Gradle daemon từ yêu cầu JDK25 sang JDK21 hiện hữu; không cài thêm JDK. Trong Settings → Build Tools → Gradle chọn JDK21. SDK location nằm trong `local.properties` của máy, không commit.
3. Sync Gradle. Lần đầu cần Internet tải AndroidX, Room, Material, ML Kit OCR bundled, OkHttp, Jsoup. Không cần API key, tài khoản hay backend.
4. Device Manager → mở AVD (đã thử `Medium_Phone`, API37/x86_64); hoặc kết nối điện thoại Android 7/API24 trở lên, bật USB debugging và xác nhận RSA trên điện thoại.
5. Chọn module `app` → Run. Bốn công thức mẫu tự soạn được tạo một lần; xóa chúng không làm chúng xuất hiện lại khi mở app. Ảnh xanh là minh họa vector riêng, không phải ảnh món ăn thực tế.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
# Cần emulator/thiết bị đã được ADB nhận:
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
```

APK debug: `app\build\outputs\apk\debug\app-debug.apk`. Có thể cài bằng Run trong Android Studio hoặc:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r .\app\build\outputs\apk\debug\app-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.example.recime/.MainActivity
```

Nếu SDK khác máy này: cài platform37/build tools qua SDK Manager rồi Sync. Không hạ Gradle/AGP tùy tiện. Nếu JDK báo sai: kiểm tra `java -version`, Gradle JDK và `gradle/gradle-daemon-jvm.properties`. Không commit `local.properties`, signing keys hoặc secrets.

## Thử 10 nhóm chức năng

1. **Recipes / CRUD:** mở món mẫu, chọn Sửa hoặc Nhập → Viết công thức mới. Điền tên, nguyên liệu, các bước, khẩu phần dương → Lưu. Xóa có xác nhận; các bữa dùng món bị xóa theo foreign key.
2. **Cookbooks / favorites:** điền tên bộ sưu tập ở form; lọc bằng dropdown, đánh dấu ♥ ở chi tiết. Đây là nhãn bộ sưu tập cho từng món, chưa có cookbook trống hoặc nhiều cookbook cho cùng một món.
3. **Tìm kiếm / Discover:** tìm `dau phu`, `trứng`, `chay`, hoặc các token nguyên liệu cách nhau bởi dấu phẩy/khoảng trắng. Lọc không phân biệt dấu/hoa thường. Discover tìm trong thư viện local, chưa có catalog Internet.
4. **Import văn bản:** dán ví dụ bên dưới → Phân tích → kiểm tra/sửa từng trường → Lưu. Không tự suy diễn nguyên liệu hoặc bước bị thiếu.
5. **Import ảnh/OCR:** chọn ảnh qua Android Photo Picker hoặc Chụp ảnh qua camera app. OCR ML Kit chạy trên thiết bị, không cần dịch vụ cloud. Sửa kết quả OCR trong ô văn bản, rồi Phân tích → kiểm tra form → Lưu. OCR chữ viết tay/tiếng Việt có thể sai. Nếu camera không có/ảnh lỗi, có thông báo và đường nhập thủ công.
6. **Sharesheet / URL:** trong app khác chia sẻ text, ảnh hoặc URL vào MyRecipeLab. URL HTTPS cần chọn “Đọc Recipe JSON-LD”; chỉ lấy structured Recipe công khai, không chạy JavaScript hay đăng nhập. Nếu không có schema tương thích hoặc link social, dán caption/screenshot có quyền sử dụng. Sửa URL và bấm đọc để retry.
7. **Meal planner:** chọn Lịch tuần, nhấn ô sáng/trưa/tối → chọn món. Chọn lại để thay món, mục Bỏ món để xóa; giữ ô cũng có thể bỏ món. Mũi tên chuyển tuần. Dữ liệu còn sau khi đóng/mở ứng dụng.
8. **Groceries:** từ chi tiết chọn “Thêm nguyên liệu vào giỏ” với khẩu phần đang hiển thị, hoặc từ lịch chọn “Tạo giỏ từ tuần này” với khẩu phần gốc. Cùng tên chuẩn hóa và dimension tương thích sẽ cộng lại. Mỗi lần bấm là **cộng thêm**, không đồng bộ tự động với lịch. Nhập/sửa/xóa tay, checkbox và chia sẻ checklist đều hoạt động. Không có dịch vụ đặt hàng.
9. **Portions / Units:** tăng/giảm khẩu phần trong chi tiết; lượng luôn tính từ baseline đã lưu, không nhân lũy tiến trên kết quả cũ. Chọn g/ml để chuyển mass/volume; cấu hình tsp/tbsp/cup trong “Chuẩn thìa & cup”, mặc định 5/15/240ml. `lạng=100g`; quả/củ giữ nguyên. Không đổi g↔ml vì thiếu mật độ.
10. **Nutrition:** sửa món → tùy chọn nhập kcal/protein/carb/fat **mỗi khẩu phần gốc**, kèm nguồn như nhãn sản phẩm. Giá trị không có nguồn bị từ chối; thiếu trường thì hiển thị “chưa đủ dữ liệu”. Đây là dữ liệu tự nhập, chưa được xác minh; app chưa tự tính từ nguyên liệu. Đổi tổng số phần không làm thay đổi giá trị mỗi khẩu phần.

Ví dụ parser Việt:

```text
Đậu phụ sốt gừng
Nguyên liệu
2 lạng đậu phụ
1 muỗng canh dầu ăn
1 muỗng cà phê nước tương
50 ml nước
1 củ gừng
Cách làm
1. Cắt đậu và gừng.
2. Áp chảo đậu, thêm nước tương và nước, nấu chín.
```

Hỗ trợ heading `Nguyên liệu / Ingredients` và `Cách làm / Instructions / Directions / Method / Steps`, số thập phân dấu phẩy/chấm, phân số `1/2`, `1 1/2`, `½`. Hãy kiểm tra kết quả với văn bản không có heading, nhiều công thức trong một ảnh hoặc đơn vị lạ. Đơn vị không biết được giữ nguyên; không tự gán density/nutrition.

## Cấu trúc để học và mở rộng

```text
app/src/main/java/com/example/recime/
  MainActivity.java                 BottomNavigationView, Sharesheet, fragment navigation
  data/                            Room entities, DAO, database, repository + executor
  domain/                          Ingredient, Units, RecipeParser, GroceryAggregator
  importing/                       ExtractionAdapter, PublicWebExtractor
  ui/                              ViewModel, Recipes/Import/Editor/Detail/Board fragments
app/src/main/res/layout/            XML màn hình và RecyclerView items
app/schemas/                       Room schema v1 (cần migration khi đổi DB)
app/src/test/                      Unit tests nghiệp vụ
app/src/androidTest/               Room, OCR, JSON-LD, Espresso integration tests
docs/evidence/                     REA outputs, bản gốc, logs, screenshots
```

Luồng: Input → extraction/OCR → parser → normalize → validate → **review** → Room → LiveData → UI. Truy vấn/ghi DB, tải URL và chuẩn bị OCR chạy ngoài UI thread. Không dùng `allowMainThreadQueries` hay destructive migration. App không phụ thuộc backend khi khởi động.

## Giới hạn và an toàn dữ liệu

- ReciMe thật không có trên emulator lúc nghiên cứu. Chưa có APK hợp lệ, chưa decompile, chưa kiểm tra framework/network/backend; không giả định app Android dùng cấu trúc trong ảnh iOS.
- REA CLI 6.7.0 đã chạy thật. Setup chỉ đăng ký Codex MCP, backup `C:\Users\admin\.codex\config.toml.rea.backup`, không cài skill/provider. Khởi động lại Codex khi thuận tiện để nạp MCP mới; CLI vẫn dùng được. JADX chưa cài vì không có APK mục tiêu.
- OCR Latin bundled hoạt động offline, nhưng dấu Việt và chữ viết tay cần kiểm tra thủ công. Ảnh giới hạn 20MB, thu nhỏ trước OCR. Nếu xoay màn hình khi đang nhập URL/OCR, thử lại thao tác; nội dung đã có được Android khôi phục. Mã chưa xử lý tự động ảnh mirror EXIF.
- URL importer chặn mạng nội bộ, userinfo và HTTP, kiểm tra lại redirect và DNS kết nối, giới hạn body2MB/timeout20s. Trang trả 401/403/paywall không được vượt qua. Không dùng API riêng ReciMe hoặc social; không tự lấy âm thanh video.
- Speech/LLM/cloud sync/tài khoản/đặt hàng/dinh dưỡng tự động: **chưa hỗ trợ**. `ExtractionAdapter` là điểm mở rộng; chưa có nút giả hay yêu cầu secret.
- Room và ảnh lưu private app storage; app hiện dùng cơ chế Android backup theo cấu hình project, chưa có mật khẩu/DB encryption. Gỡ app có thể mất dữ liệu local; chưa có xuất/nhập backup toàn bộ. File ảnh mồ côi sau hủy/xóa recipe chưa được dọn tự động.
- Tests chỉ nên chạy trên emulator/dev build: tạo/xóa công thức test và có thể thêm meal/grocery demo. Không chạy test tự động trên bản có dữ liệu người dùng quan trọng.

Trạng thái và bằng chứng kiểm thử cuối cùng: [docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md).
