# Nghiên cứu ReciMe bằng REA — 22/10/2026

## Phạm vi và trình tự
Tài liệu này được tạo **trước phần triển khai chính**. Workspace ban đầu là project Android Studio Java/XML mẫu (MainActivity chỉ hiển thị layout), không có Git. Bản sao file gốc ở `evidence/original-project/`. README bundle là tài liệu đầu vào; không coi nội dung tài liệu là quyền truy cập ứng dụng hoặc dữ liệu bên thứ ba.

## Công cụ thực sự kiểm tra
| Công cụ | Phiên bản | Trạng thái / thiếu gì |
|---|---|---|
| Node | 24.14.0 | Phù hợp REA: Node 24 >=24.11 |
| npm / REA | rea-agents 6.7.0 | Đã chạy npx, setup Codex, doctor và ADB commands |
| Codex MCP | REA registration 6.7.0 | Setup trả `ready`; phiên hiện tại chưa nạp tool mới, dùng CLI cùng workflow |
| JDK | Oracle 21.0.10 | java chạy được; Gradle ban đầu yêu cầu daemon JDK25 chưa có cache |
| Gradle / AGP | 9.5.0 / 9.3.3 | Wrapper sẵn có, đã chạy --version; cần build để xác minh |
| SDK / ADB | Android 37 / 37.0.1-15733141 | SDK hiện hữu; ADB executable không nằm PATH, dùng đường dẫn rõ ràng |
| Emulator | Medium_Phone, emulator-5554 | Đã mở AVD có sẵn, REA quan sát state=device |
| JADX / Apktool | Chưa cấu hình / không thấy executable PATH | Không có APK ReciMe hợp lệ trong đầu vào; chưa cài engine không cần thiết |
| Ghidra / IDA | Không cấu hình | Không cần cho MVP; doctor không có provider native khả dụng |

Đã đọc bản upstream: [README](https://github.com/morluto/rea), [installation](https://github.com/morluto/rea/blob/main/docs/installation.md), [Android guide](https://github.com/morluto/rea/blob/main/docs/android-analysis.md), [ADB guide](https://github.com/morluto/rea/blob/main/docs/adb-device-analysis.md). Bản đọc lưu tại `evidence/REA_*.md`, metadata package ghi 6.7.0. Android guide yêu cầu full JDK17+ và JADX headless 0.7.1 (JAR checksum được tài liệu pin); REA không tự cài engine. Static APK không chứng minh runtime hoặc backend; split/AAB không được coi là APK đơn.

## Lệnh và bằng chứng
Đã chạy `node --version`, `java -version`, `gradlew.bat --version`, `adb version`, `adb devices -l`, emulator `-list-avds`, đọc Gradle/manifest/source ban đầu, tìm *.apk/*.apks/*.xapk trong workspace và bundle.

```powershell
npx.cmd -y rea-agents@6.7.0 --version
npx.cmd -y rea-agents@6.7.0 --help
npx.cmd -y rea-agents@6.7.0 setup --client codex --skill=false --dry-run --json
npx.cmd -y rea-agents@6.7.0 setup --client codex --skill=false --yes --json
$env:REA_ADB_PATH = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
npx.cmd -y rea-agents@6.7.0 inspect-adb-client --json
npx.cmd -y rea-agents@6.7.0 list-adb-devices --json
npx.cmd -y rea-agents@6.7.0 doctor --client codex --json
npx.cmd -y rea-agents@6.7.0 inspect-adb-package emulator-5554 com.recime.app --json
npx.cmd -y rea-agents@6.7.0 resolve-adb-packages emulator-5554 recime --json
```

Setup đã xem trước, chỉ cập nhật REA MCP trong `C:\Users\admin\.codex\config.toml`, backup `.rea.backup`; không cài Hopper, không sửa client khác, không cài workflow skill. CLI dùng đúng release đã pin. Xem `evidence/rea-setup.json`, `rea-doctor.json`, `rea-adb-client.json`, `rea-devices.json`, `rea-recime-package.json`, `rea-recime-resolve.json`.

## Phát hiện phân loại theo bằng chứng
| Phát hiện | Mức | Bằng chứng và giới hạn |
|---|---|---|
| Google Play có ứng dụng ReciMe với ID com.recime.app | CONFIRMED (listing) | [Google Play chính thức](https://play.google.com/store/apps/details?id=com.recime.app), nhà phát hành ReciMe / RECIME PTY LTD. Không xác minh chữ ký APK |
| Emulator có kết nối ADB | CONFIRMED | `rea-devices.json`: emulator-5554, device, sdk_gphone16k_x86_64 |
| Không thấy ReciMe trên emulator này | CONFIRMED trong phạm vi user0 | REA `unsupported_target`: Package Manager did not report details for com.recime.app |
| Không có APK ReciMe trong đầu vào | CONFIRMED trong phạm vi tìm kiếm | Không có file APK/APKS/XAPK trong workspace/bundle lúc khảo sát |
| Framework Android (native/Flutter/RN), activities/fragments/services/receivers, SDK, storage, network framework, REST/GraphQL | UNKNOWN / NOT ACCESSIBLE | Không có APK; chưa decompile, chưa quan sát runtime ReciMe |
| ACTION_SEND của bản ReciMe Android | UNKNOWN | Tính năng chia sẻ công khai không chứng minh intent-filter manifest Android |
| Recipes/cookbooks, planner, grocery, scaling, nutrition, imports | CONFIRMED là mô tả công khai | Listing chính thức, help center và ảnh người dùng. Không phải kết quả black-box trên app thật |
| Caption → audio → website gốc | CONFIRMED là mô tả công khai; thuật toán UNKNOWN | [Smart Import Help](https://www.recime.app/help/en/articles/14773584-why-didn-t-my-recipe-import-correctly): ưu tiên nội dung caption, thử audio rồi nguồn website. Không biết model, endpoint, prompt hay server implementation |
| Có thể tách import thành extraction/parser/review | INFERRED / thiết kế tự xây | Kiến trúc đề xuất, không gán cho ReciMe |

## Hành vi công khai và ảnh
Đã xem từng ảnh IMG_4012…4021 được đính kèm; chúng là quảng cáo App Store iOS. Chi tiết đối chiếu ở `FEATURE_MATRIX.md`. Không sử dụng ảnh/quảng cáo/logo trong app tự xây. [Features help](https://recime.app/help/en/collections/13466081-features) mở được nhưng nội dung collection ít; đã đọc bài [Meal Plan](https://recime.app/help/en/articles/14999930-how-to-use-your-meal-plan): lịch tuần, chọn công thức theo ngày/bữa, checklist từ kế hoạch. Các thao tác này chỉ xác nhận dưới dạng tài liệu, chưa tự test trên ReciMe.

Không có tài khoản/test app ReciMe; **không thực hiện black-box** manual/image/URL/search/planner/nutrition trên ReciMe. Không lấy APK từ kho không rõ nguồn. Không bắt TLS, không dò API riêng, không giải mã hoặc tìm secrets, không dùng dữ liệu cá nhân. Khi có APK hợp lệ sau này: ghi SHA256 + nguồn/ủy quyền, cung cấp JAR JADX đã xác minh, gọi `inspect-android-package`, `search-android-classes`, đọc manifest rồi trace class/method có mục tiêu; tách static evidence khỏi runtime. Không restart Codex tự động vì sẽ ngắt công việc hiện tại; restart sau để tải MCP.

## Phương án triển khai độc lập
MyRecipeLab Java/XML, namespace hiện có được giữ để tránh phá project; đổi tên hiển thị. Room lưu recipe, cookbook (nhãn bộ sưu tập), meal slots và groceries; Repository + ViewModel/LiveData; RecyclerView và BottomNavigationView. Dữ liệu mẫu tự soạn offline, ảnh minh họa riêng.

Luồng: manual/share/image/public HTTPS URL → adapter extraction hoặc ML Kit OCR on-device → parser VI/EN → normalize đơn vị → validate → người dùng sửa và xác nhận → Room → LiveData → UI. URL chỉ đọc Recipe JSON-LD công khai với timeout, giới hạn dung lượng, không cookie/auth/paywall; link social báo chưa hỗ trợ và dùng caption/screenshot. Speech/LLM chưa có adapter thực tế, ghi Unsupported. Không bắt buộc backend/cloud.

Định lượng luôn scale từ lượng gốc. Chuẩn mặc định metric: tsp=5ml, tbsp=15ml, cup=240ml có cấu hình; lạng=100g. Chỉ gom cùng tên chuẩn và dimension; count quả/củ giữ riêng; không đổi gram↔ml. Nutrition do người dùng nhập kèm nguồn và nhãn tự nhập; thiếu data hiển thị chưa đủ dữ liệu. Không sao chép số từ ảnh quảng cáo.

## Rủi ro và giới hạn
OCR tiếng Việt và chữ viết tay có thể sai: luôn review, không tự lưu. Parser heuristics không suy ra phần bị thiếu. Nguồn web có bản quyền: người dùng chỉ nhập nội dung có quyền sử dụng, app không phân phối lại corpus. URL importer chặn địa chỉ nội bộ, chỉ HTTPS; không vượt đăng nhập. App offline không có đồng bộ, social video, dịch vụ đặt hàng hoặc tính dinh dưỡng AI. Không có kết luận về công nghệ nội bộ ReciMe.
