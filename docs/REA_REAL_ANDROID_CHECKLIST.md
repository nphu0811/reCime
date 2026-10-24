> Cập nhật 23/10/2026: phần dưới là baseline lịch sử. APK 6.0.2 (217) đã đọc bằng REA/JADX và Emulator đã có com.recime.app từ Play. Source master e4460c6 đã bổ sung nhiều chức năng sau audit này. Xem [findings](REA_ANDROID_6_0_2_FINDINGS.md) và [ma trận hiện tại](FEATURE_PARITY_MATRIX.md); các blocker “chưa có APK” bên dưới không còn đúng.

# REA: kiểm chứng bản Android ReciMe thật (chưa thực hiện vì chưa có APK)

**Mục tiêu:** chỉ thu thập quan sát về hành vi/UI và bằng chứng tĩnh trên **bản ReciMe được cài từ Google Play chính thức** (`com.recime.app`). Không dùng file APK từ mirror không rõ nguồn, không bypass tài khoản, DRM, SSL pinning, captcha hoặc API nội bộ; không đẩy APK/dữ liệu cá nhân lên GitHub.

## A. Điều kiện đủ để bắt đầu

1. Trên điện thoại Android hoặc emulator đã đăng nhập Google Play, cài ứng dụng: https://play.google.com/store/apps/details?id=com.recime.app
2. Trong Android Studio Windows, chọn máy ADB có app thật. Repo ngày 22/10/2026 từng ghi emulator `emulator-5554` **không có** package chính thức.
3. PowerShell mở trong thư mục project; có Node **>=22.19 / >=24.11** theo bản REA hiện tại, npm, JDK 21, Android platform-tools và một JADX headless JAR phù hợp.
4. Chỉ dùng phiên bản REA/JADX lấy từ link chính thức `morluto/rea`; đối chiếu release và `README`/Android docs trước khi chạy.

## B. Chạy REA trên Windows (lệnh mẫu cần điều chỉnh đường dẫn)

```powershell
# Khi REA đã được setup với Codex ở phiên trước, khởi động lại Codex để nạp MCP.
npx.cmd rea-agents@latest --version
npx.cmd rea-agents@latest setup

$env:REA_ADB_PATH = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $env:REA_ADB_PATH devices -l

# Đổi SERIAL theo kết quả ADB; không đoán luôn là emulator-5554.
rea inspect-adb-client
rea list-adb-devices
rea resolve-adb-packages emulator-5554 recime
rea inspect-adb-package emulator-5554 com.recime.app

# Đường dẫn EVIDENCE phải là thư mục MỚI (REA tránh ghi đè).
rea pull-adb-package emulator-5554 com.recime.app --output-directory "C:\recime-research-2026\official-apks"

# Theo lệnh REA, thu thập toàn bộ split APK; base.apk có thể không có mọi resource.
# Chỉ khi đã kiểm tra URL nguồn và JAR headless lấy từ upstream chính thức:
$env:REA_JADX_MCP_JAR = "C:\tools\jadx-headless-mcp-0.7.1-all.jar"
rea inspect-android-package "C:\recime-research-2026\official-apks\com.recime.app\base.apk"
rea search-android-classes "C:\recime-research-2026\official-apks\com.recime.app\base.apk" Activity
```

**Lưu ý:** Directory structure thực tế phụ thuộc kết quả `pull-adb-package` và hệ điều hành; đọc `local_path` trong output REA, không mặc định đường dẫn ví dụ luôn tồn tại. Nếu bản npm yêu cầu `rea` command cài global, theo README chính thức để cài hoặc dùng `npx` phù hợp.

Dấu xác thực cần ghi:
- `com.recime.app` từ `dumpsys package`, phiên bản + installation source.
- Danh sách đường dẫn `pm path` cho base/split; SHA-256 từng file do REA trả về.
- Đọc chữ ký APK bằng `apksigner verify --print-certs` của Android SDK và đối chiếu với **mẫu tham chiếu chính thống**; APK có tên/package đúng không tự chứng minh chính chủ. REA/JADX **không xác minh chữ ký**.
- Manifest, activity/intent/exported, storage/runtime declarations và chứng cứ từ method trace. Tránh gán endpoint/model/engine nội bộ nếu không thể quan sát.
- **Không sử dụng chữ ký hoặc tên package ReciMe để giả mạo app reCime**. `com.example.recime` là sản phẩm riêng.

## C. Black-box flow test bằng bản phát hành thật

Người dùng thao tác hợp lệ trong app; REA hỗ trợ chụp màn hình, logcat, window focus, không giả vờ tự click Android:
```powershell
rea inspect-adb-window emulator-5554
rea capture-adb-screen emulator-5554 --output-directory "C:\recime-research-2026\shots-01"
rea read-adb-logcat emulator-5554 --count 200
```

Cho mỗi case:
- **S01 / Home:** app first-run, onboarding, login, Home, category, bottom nav, trường hợp có và không có data.
- **S02 / Cookbook:** create, rename, delete, add/remove recipe, covers, count, empty states.
- **S03 / Social Import:** TikTok/Instagram/Facebook/Youtube/Pinterest PUBLIC recipe; caption / no caption / inaccessible / duplicate / retry; đo các status loading/error. Chỉ test nguồn bạn có quyền sử dụng.
- **S04 / Website + scan:** public Recipe JSON-LD, URL không hợp lệ, screenshot chữ rõ, ảnh handwritten thử nghiệm.
- **S05 / Detail:** 1/2/4 servings, metric/imperial, Nutrition, Ask AI, từng bước cooking, share/export.
- **S06 / Search:** ingredient exact match (chicken thigh vs drumstick), tags, search title.
- **S07 / Planner:** 2 món trong 1 ngày/slot, Snack, drag, clear week, start day Monday/Sunday.
- **S08 / Groceries:** auto sync, xóa meal, đổi servings, manual items, sort, checked bottom, no duplicate.
- **S09 / Account/Offline:** app restart, offline edit, cùng tài khoản nhiều thiết bị nếu tài khoản test cho phép.
- **S10 / UI Motion:** 60fps không phải kỳ vọng cứng; quay màn 2 app cùng resolution/DPI, đo cảm nhận và thao tác, font scale/low-end device, Reduce Motion.

Record mỗi case thành: `case_id, timestamp, app_version, Android_build, locale, device_resolution, steps, expected, observed, evidence_path, confidence, outcome` với `PASS / FAIL / BLOCKED / UNKNOWN`.

## D. Quy tắc so sánh

- **STATIC:** manifest/class/method từ REA/JADX chỉ cho biết có tài nguyên/code; không tự chứng minh server/luồng đã chạy.
- **RUNTIME:** screenshot/video/log chạy app thật ghi màn hình, nhưng không đủ để suy đoán kiến trúc backend.
- **DOCS:** trang help chính thức mô tả tính năng được quảng bá; có thể khác version, quốc gia hay gói thuê bao.
- **REIMPLEMENTATION:** app reCime tự phát triển bằng Java/XML/SQLite và backend riêng; có thể làm tương đương về chức năng nhưng không phải bản chính thức.
- **IDENTITY:** “genuine ReciMe” phụ thuộc chuỗi phân phối + chữ ký; việc giống 1:1 UI không đủ.

## E. Điều kiện dừng/báo cáo

Chỉ thông báo **“đã xác minh đạt parity cho phiên bản X và bộ use-case Y”** nếu:
1. Đã thu được artifact APK chính thức và metadata + hash, kiểm tra tính xác thực hợp lý.
2. Đã chạy toàn bộ case trong phạm vi `Y` trên **app ReciMe** và **reCime** thật, cùng môi trường.
3. Tất cả case đó có dữ liệu so sánh, ảnh/video đối chứng, và build/test source của reCime PASS.
4. Không còn blocker ẩn quan trọng như social import chỉ mock, sync chỉ fake UI, nutrition lấy số đoán.

**Trạng thái hiện tại:** BLOCKED ở bước (1), chưa có APK/REA runtime chính thức trong workspace ChatGPT. Không được ghi completed.

Nguồn: https://github.com/morluto/rea và https://github.com/morluto/rea/blob/main/docs/adb-device-analysis.md
