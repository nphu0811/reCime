# Chạy và triển khai backend độc lập

1. Làm theo `SETUP_API_KEYS.md`, chạy từ root repository; mặc định bind localhost. Phần tài khoản/backup dùng database server riêng, không mở SQLite Android.
2. Sao lưu `backend/data/` bằng SQLite backup API hoặc dừng tiến trình rồi sao chép database cùng WAL/SHM. Không chỉ copy file `.db` đang ghi. Migrations nằm trong `backend/cloud.py`, bảng `schema_migrations`, chạy additive/idempotent. Chỉ hỗ trợ sqlite:///; không tuyên bố đã hỗ trợ PostgreSQL.
3. Production: cấu hình TLS reverse proxy, APP_ENV=production, không log Authorization/password/request body. Proxy giới hạn body ≤12MB, timeout phù hợp, rate limit theo IP đáng tin cậy. Không tin X-Forwarded-For tùy tiện. App có giới hạn body kể cả chunked và rate limit trong mỗi process; nhiều worker cần rate limiter dùng chung ở proxy.
4. Giới hạn quyền filesystem, mã hóa ổ lưu server, đặt chính sách retention/backup phù hợp trước khi công khai dịch vụ. Không có telemetry bên thứ ba. API DELETE /v1/account xóa user/sessions/records/operations/backup trong một transaction FK cascade; dữ liệu local của điện thoại được giữ. Bản backup vận hành của server phải có chính sách retention riêng.
5. Auth email/password độc lập: session ngẫu nhiên 256-bit, 7 ngày; token hash lưu server, client Keystore. Không triển khai SMTP verification/reset hoặc Google/OAuth. Cần bổ sung các luồng này, kiểm toán auth và bảo vệ vận hành trước khi mở đăng ký đại trà. ALLOW_REGISTRATION=false hỗ trợ chế độ riêng.

## Backup và conflict

Android SQLite v5→v6 chỉ thêm `backup_restores(backupId, importedAt)`. Không DROP/rebuild dữ liệu. Backup JSON version1 chứa SHA-256 của chuỗi content, snapshot bảng/ảnh và tùy chọn (không chứa session/key/endpoint). Restore kiểm tra phiên bản, checksum, ảnh và FK, remap IDs trong transaction, thêm bản sao cá nhân, hợp nhất cookbook, giữ manual/planned riêng, giữ checked. Cùng backupId nhập đúng một lần. Các backup khác nhau có thể tạo nhiều bản sao; người dùng chủ động chọn restore. Tùy chọn hiện tại được giữ, không tự áp dụng tùy chọn máy khác.

Restore rollback dữ liệu và ảnh staged khi thất bại; file backup gốc không đổi. Trước nâng cấp lớn hãy xuất backup tệp và giữ bản cài cũ. Không cài APK version cũ chỉ hiểu v5 lên v6; nếu cần rollback dùng bản code tương thích v6 hoặc nhập backup qua chức năng hỗ trợ, không hạ version/reset DB.

Cloud backup dùng CAS `baseRevision`, `operationId` idempotent. Outbox trong `noBackupFilesDir` được ghi AtomicFile, gắn tài khoản+origin. Mất mạng giữ bản chờ; JobScheduler gửi lại khi có mạng, hoặc bấm nút gửi lại. HTTP4xx/conflict dừng retry tự động. Sau restart app bản chờ vẫn còn; việc reboot thiết bị chưa có boot receiver, có thể bấm gửi lại để lên lịch. Khi conflict: xuất backup local nếu cần → hủy bản chờ → tải/hợp nhất cloud → tạo backup mới. Không có overwrite ngầm.

Record sync server hỗ trợ recipe/cookbook/membership/meal/grocery/preferences, pagination cursor, tombstones, revision conflicts, user isolation và operation idempotency. Chưa có Android adapter gắn mỗi mutation vào record outbox và chưa có tự động merge đa thiết bị; không coi cloud backup là record sync hoàn chỉnh. Collaboration/public recipe domain/merchant billing chưa triển khai.

## Kiểm tra

```powershell
backend/.venv/Scripts/python.exe -m unittest backend.test_cloud backend.test_health backend.test_backend backend.test_provider_contracts -v
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

`AccountContractTest` Android cần FastAPI local thật + adb reverse 8765. Chỉ dùng database QA riêng (`DATABASE_URL=sqlite:///.tools/qa-cloud.db`); test tự tạo/xóa tài khoản fixture. Không dùng server production. Các test provider dùng mock không xác minh OpenAI/USDA thực tế.

Xuất backup trước khi chạy connected tests trên thiết bị có dữ liệu cần giữ. UTP mặc định đã gỡ APK app độc lập sau các lượt QA của phiên này; sau test kiểm tra package thấy chỉ còn `com.recime.app`. Đã thêm `android.injected.androidTest.leaveApksInstalledAfterRun=true` cho các lần test sau, theo [AndroidX](https://android.googlesource.com/platform/frameworks/support/+/eb74b1a4b3527414639994574a201f125aff90f5); chưa chạy lại connected để xác minh runtime của tùy chọn này. Migration/data preservation được xác minh bằng fixtures, không coi cleanup UTP là bảo toàn dữ liệu cài thật. App official không bị thao tác hoặc xóa.

Tham chiếu nền tảng: [FastAPI security](https://fastapi.tiangolo.com/tutorial/security/), [Android alarms](https://developer.android.com/develop/background-work/services/alarms). Timer cần notification permission từ Android13; giờ chính xác cần quyền riêng Android12+. Nếu thiếu quyền chính xác, UI thông báo alarm có thể trễ; không dùng PiP hoặc foreground service giả. Timer không được khôi phục sau reboot.
