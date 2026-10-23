# Blockers và phần chưa hoàn thành

Cập nhật23/10/2026, WIP PR#5. Không còn blocker APK/JADX hoặc đăng nhập Emulator.

- Editor official hiện có màn Something went wrong/Try again; form đã có screenshot/XML phiên trước. Chưa xác minh save→detail. Không suy nguyên nhân server hoặc bypass.
- Editor đã bổ sung Photo Picker và section UI độc lập, nhóm ảnh đã PASS build/unit/lint và 21 instrumentation tests. Nội dung/sheet màn con Info/Nutrition/Tags official vẫn UNKNOWN; không tuyên bố pixel parity. Process kill giữa lúc copy/commit và full backup/restore đa thiết bị chưa kiểm chứng.
- Recipe Detail/Cooking/export authenticated runtime, planner drag/move/start-day, grocery source grouping và chọn nguyên liệu từ plan: còn thiếu đối chiếu/triển khai. Auto-derived giỏ app mình là khác biệt, chưa chứng minh tương đương official.
- Auth/cloud sync/collaboration/public recipe links cần backend và cấu hình provider riêng được phép; chưa có implementation đầy đủ. Không dùng API/token của official.
- Social video/audio extraction cần nguồn/API được phép; TikTok hiện chỉ oEmbed + retrieval catalog, không được gọi là recipe import hoàn chỉnh.
- Live USDA/OpenAI cần key backend `FDC_API_KEY`/`OPENAI_API_KEY`; không gửi key trong chat, không nhúng APK. Mock tests không xác minh live service.
- Không merge master khi nhóm test liên quan chưa PASS. Không công bố parity1:1; xem ma trận từng chức năng.

Nhóm ảnh bổ sung migration v5 additive; không đổi dữ liệu/bảng v4 cũ. System-picker cloud providers, process kill giữa copy/commit và full backup/restore vẫn chưa kiểm chứng. Không thiếu API key cho chức năng ảnh local.


Recipe Detail (tiếp từ9e94d5b): official bị chặn ở Editor lỗi và draft chưa lưu; không bỏ draft để ép navigation. Detail runtime/gallery/animation official vẫn UNKNOWN. Nhóm Detail độc lập PASS build/unit/lint,22 JVM và31 instrumentation tests; không thay schema/Editor đã hoàn thành. AI live cần backend/key riêng; fixture HTTP chỉ xác minh xử lý503 và rotation. Print preview không chứng minh in vật lý.
