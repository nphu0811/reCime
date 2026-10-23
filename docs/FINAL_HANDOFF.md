# Bàn giao reCime — 24/10/2026

Ứng dụng độc lập `com.example.recime`, Java/XML/Fragment/SQLite. Nhánh `wip/rea-recime-602`, tiếp tục Draft PR#5; không merge master, không force-push. Baseline được fast-forward từ c208ea0 lên 2513e48; workspace ban đầu sạch. Giữ WAL/background cookbook/catalog optimization của f91ca9d. Không thao tác lên app/tài khoản/draft official.

| Trạng thái | Nhóm | Phạm vi thực tế |
|---|---|---|
| DONE | Library/Cookbooks/Planner/Groceries | Giữ và regression các luồng đã có; sửa test root dialog, query member cookbook ngoài main; cookbook trống giữ options sau refresh/rotation; không xóa recipe khi xóa cookbook |
| DONE | Import local/web/share | HTTPS JSON-LD bounded/redirect/DNS guards; text/servings/attribution; duplicate review; picker/camera/OCR; OCR pending qua rotation; giữ caption+URL và sửa nháp; không tự upload ảnh ít chữ sang AI |
| DONE | Discover | Catalog offline có nguồn; lưu bản sao cá nhân giữ attribution/license/ảnh; tùy chọn tìm catalog backend với pagination, lỗi/retry và Back về offline |
| DONE | UI/Settings/Guest | Theme trắng/kem cho các màn chính, dock4tab và +; welcome/Guest, settings/account, đơn vị, privacy; password không lưu qua rotation; modal Import/Cooking tránh mất trạng thái do đổi root tab |
| DONE | Cooking/Export | Step/progress/checklist theo portions, timer lưu deadline local qua rotation/background; permission-aware alarm/notification; PDF có ảnh hợp lệ và nguồn, tạo background; Print/Share Sheet/FileProvider |
| DONE | Email/password backend | Database riêng + additive migration server v1; scrypt/hash session, expiration, profile, logout/account delete, user isolation; Android session mã hóa Keystore |
| DONE | Backup tệp/cloud | SQLite5→6 chỉ thêm ledger chống nhập lặp; checksum/ảnh/FK/transaction; restore bổ sung, remap IDs, không reset; cloud CAS/idempotency, outbox durable và network retry; contract local thật |
| CONFIGURATION REQUIRED | OpenAI/USDA | API key chỉ trên backend; schema-grounded AI text extraction/Q&A, USDA unit/source/uncertainty + confirm. Thiếu key trả lỗi thật. Chưa xác minh live provider |
| CONFIGURATION REQUIRED | Backend trên thiết bị thật/production | Khởi chạy backend, cấu hình origin/HTTPS, tạo tài khoản riêng. Không cần key trả phí cho email/password và backup |
| BLOCKED | Google login/reset email/collaboration | Chưa có adapter/flow hoàn chỉnh; không chỉ chờ thêm key. Cần implementation, provider setup và QA |
| BLOCKED | Automatic record sync | Server record API có revisions/tombstones/idempotency/conflict/pagination; Android chưa nối mọi mutation vào entity outbox và conflict UI. Cloud backup không thay thế record sync đa thiết bị |
| BLOCKED | Video/social/audio/public links/merchant | TikTok chỉ metadata/caption công khai; Instagram/YouTube/Facebook/Pinterest yêu cầu nội dung người dùng có quyền cung cấp. Chưa có video/transcript adapter, domain riêng, collaboration hoặc commerce contract |
| NOT VERIFIED | Official parity | Chỉ dùng STATIC/RUNTIME VERIFIED trong nghiên cứu cũ; populated Detail/Cooking/planner official, motion/haptics/pixel parity vẫn UNKNOWN. Không decompile lại toàn APK |
| NOT VERIFIED | Thiết bị/provider khác | OCR tiếng Việt đa dạng, process kill ở mọi điểm, Android13–16, alarm chính xác/notification delivery trong Doze, reboot, physical print, live Google/OpenAI/USDA và multi-device record merge |

Kiểm thử: debug build PASS,44JVM PASS,72/72 instrumentation PASS trong một lượt,26backend PASS,lint0errors/233warnings. Chi tiết cả các lượt lỗi đã sửa và giới hạn ở `TEST_RESULTS.md`; SHA commit xem Git history. Không gọi toàn bộ roadmap là DONE. APK nằm cục bộ ở `app/build/outputs/apk/debug/app-debug.apk`, không commit build artifacts.

Instrumentation UTP đã gỡ app độc lập sau test. Cài lại debug APK bị automatic approval review chặn với lý do chung “blocked by policy”; vì vậy chưa chụp/đọc screenshot cuối trên bản mới, và app cần được người dùng bấm Run trong Android Studio. Đã thêm tùy chọn giữ APK sau các lần test tương lai (chưa runtime verify). Không chứng nhận dữ liệu Emulator độc lập còn nguyên qua cleanup UTP; các test migration/FK/restore vẫn PASS trên database fixtures riêng. `com.recime.app` vẫn được cài, không bị mở/xóa dữ liệu trong phiên này.

## Dùng ngay

Mở app → Guest, thư viện/catalog/Import/Planner/Groceries hoạt động độc lập. ⚙ Recipes mở settings. Backend local: làm theo `SETUP_API_KEYS.md`; SQLite server mặc định `backend/data/recime.db`. Với USB/emulator, adb reverse8765 rồi dùng `http://127.0.0.1:8765` ở debug. OpenAI/FDC để trống vẫn dùng được các nhóm local, auth và backup. `/health` báo cấu hình, không chứng nhận dịch vụ từ xa.

Backup chứa dữ liệu/ảnh cá nhân. Xuất tệp trước khi tùy chỉnh migration; restore thêm bản sao và hợp nhất, không replace. Các backup khác nhau có thể tạo bản sao thêm; preferences hiện tại được giữ. Conflict cloud: xuất tệp local → hủy pending → tải/hợp nhất cloud → lưu snapshot mới. Logout offline không thu hồi được session server ngay.

Các hành động cần người dùng: điền key tùy chọn vào `.env` nếu muốn OpenAI/USDA; khởi động/duy trì backend và HTTPS nếu dùng cloud; tự cấp notification/exact-alarm khi dùng timer; tự tạo OAuth/email/domain/merchant provider nếu triển khai các phần còn thiếu. Không cần gửi key hoặc đăng nhập tài khoản ReciMe official.
