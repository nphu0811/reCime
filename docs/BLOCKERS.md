# Blockers và phần chưa hoàn thành

Cập nhật23/10/2026, WIP PR#5. Không còn blocker APK/JADX hoặc đăng nhập Emulator.

- Editor official hiện có màn Something went wrong/Try again; form đã có screenshot/XML phiên trước. Chưa xác minh save→detail. Không suy nguyên nhân server hoặc bypass.
- Photo selection trong Editor độc lập, section UI/sheets và draft khi process bị OS kill trong lúc commit chưa hoàn tất: chưa kiểm thử/hoàn thiện.
- Recipe Detail/Cooking/export authenticated runtime, planner drag/move/start-day, grocery source grouping và chọn nguyên liệu từ plan: còn thiếu đối chiếu/triển khai. Auto-derived giỏ app mình là khác biệt, chưa chứng minh tương đương official.
- Auth/cloud sync/collaboration/public recipe links cần backend và cấu hình provider riêng được phép; chưa có implementation đầy đủ. Không dùng API/token của official.
- Social video/audio extraction cần nguồn/API được phép; TikTok hiện chỉ oEmbed + retrieval catalog, không được gọi là recipe import hoàn chỉnh.
- Live USDA/OpenAI cần key backend `FDC_API_KEY`/`OPENAI_API_KEY`; không gửi key trong chat, không nhúng APK. Mock tests không xác minh live service.
- Không merge master khi nhóm test liên quan chưa PASS. Không công bố parity1:1; xem ma trận từng chức năng.
