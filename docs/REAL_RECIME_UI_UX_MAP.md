# Runtime UI map — ReciMe 6.0.2 (217)

23/10/2026, Emulator emulator-5554, 1080×2400, density420, Android17. Ảnh/XML giữ ngoài git tại `C:/Users/admin/Desktop/ReciMe-Official-6.0.2/research-20261023/`; không sao chép tài nguyên/ảnh tài khoản vào APK hay GitHub. Tên `recime-home.png` nếu được cung cấp phải đọc theo nội dung: đó là Editor; file đó không tồn tại lúc kiểm tra tiếp từ PR#5.

| Màn/luồng | Quan sát RUNTIME VERIFIED | Evidence cục bộ | Giới hạn |
|---|---|---|---|
| Welcome/Login | Get started/Log in; Google/email; ComposeView | launch.png/xml, login.png/xml | Video minh họa không chứng minh library thật |
| Home | Dock Recipes/Meal Plan/Groceries/Discover; Cookbooks/All Recipes; quota5 left; FAB + | home.png/xml | Account chỉ dùng kiểm thử, không xuất dữ liệu |
| Cookbook | Tạo QA60220261023 thành công, card0 Recipes; mở empty No recipes yet; menu Select recipes/Edit title/Add icon/Edit access/Delete cookbook | cookbook-success.png/xml, cookbook-empty.png/xml, cookbook-options.png/xml | Chưa kiểm thử collaboration hoặc mọi action menu |
| Add recipe | Social/photo/text/web/write from scratch/search by creator | main-import-options.png/xml | Chưa nhập từng provider |
| Recipe Editor | Title, Images/Add image, Info2 serves, Nutrition, Tags, Ingredients, Instructions; Cancel/Save; heading/normal | editor-retry.png/xml, editor-filled.png/xml | Save bị IME toolbar che trong phiên trước; không coi bấm tọa độ là đã lưu thành công |
| Editor error | Something went wrong / Try again / Cancel | editor-current.png/xml (lần tiếp tục), write-recipe.png/xml | Chưa xác minh nguyên nhân. ADB có network validated không chứng minh server API khỏe |
| Meal Plan | Tuần19–25Oct2026, previous/next, empty Nothing planned yet, Add from recipes/Magic Meal Plan | current-screen.png/xml | Chưa add/drag runtime |
| Groceries | 0items, empty No groceries yet, FAB thêm | groceries-empty.png/xml | Chưa chứng minh continuous meal auto-sync |
| Discover | Search, popular searches, recipe of day, share/save | discover.png/xml | Chưa chứng minh save/detail/cooking thành công |

Bố cục quan sát: nền cream/white, chữ đen, accent xanh; collection card2cột, menu/sheet bo góc, dock4tab. Baseline trước nhóm Editor/Detail/Planner là midnight/lime, dock5tab và form Editor dài; các cập nhật cục bộ được ghi ở section bên dưới, chưa chứng nhận parity1:1. Font file/dp chính xác, motion timing và haptics runtime chưa đo. Static transition500ms được ghi riêng trong findings.

## Editor độc lập sau92d2e77

Chỉ Editor dùng theme trắng/kem; toolbar Cancel–Save luôn ở trên. Dock ẩn trong Editor và khôi phục khi thoát. Sections theo ảnh đã quan sát; nội dung Info/Nutrition/Tags triển khai bằng panel mở rộng riêng, hành vi màn con official UNKNOWN. Images có thumbnail, preview, Thay/Xóa; giới hạn5 là yêu cầu triển khai, không phải suy diễn quota của official. Không sử dụng logo, ảnh, font hay icon từ APK.


## Recipe Detail — tiếp từ9e94d5b

Runtime official: đã mở `com.recime.app/com.recime.ReciMeActivity`, thấy Editor “Something went wrong / Try again / Cancel”. Cancel mở “Would you like to save your changes? / No / Save edits”; chỉ đóng dialog bằng Back, giữ draft. Không mở được Recipe Detail trong lần quan sát này, không lưu/xóa draft tài khoản. Evidence cục bộ ngoài git: `research-20261023/detail-attempt-editor-error.png`; XML tạm trong `.tools/official-detail-current.xml`. Layout, gallery, gesture và transition của **Detail official: UNKNOWN**. Không phân tích lại APK.

Implementation độc lập: giữ layout Detail hiện có, thêm ảnh lớn, trước/sau, chỉ số1–5 và empty “Chưa có ảnh”. Cover legacy lấy qua `Recipe.photoPaths()`. Giữ ảnh theo đường dẫn khi danh sách đổi, clamp khi ảnh bị xóa; selection/portions qua recreation. Đây là cách triển khai theo yêu cầu, chưa được gọi là UI parity official. Không dùng ảnh/tài nguyên APK.


## Meal Planner — tiếp từ3a3179f

Dùng lại evidence Meal Plan empty `current-screen.png/xml` và findings9: tuần/previous-next được quan sát runtime; bốn meal types, drag callback và preference đầu tuần được static verified. Không phân tích lại APK hoặc bỏ draft của official để ép navigation. Lịch official có món, gesture drag thực tế, screen settings và chuyển nhóm bữa runtime vẫn UNKNOWN.

UI độc lập mới: white/cream, hàng điều hướng tuần, Today, local week-start dialog, header từng ngày và bốn loại bữa. Tap một món mở actions: công thức/đổi món/khẩu phần/chuyển/bỏ. Giữ tay nắm để native drag; thả lên add/meal row đổi ngày và bữa, thả header ngày giữ loại bữa, ngoài list hủy; mép list hỗ trợ scroll. Dialog chuyển/khẩu phần giữ draft qua recreation. Không khẳng định gesture này pixel/behavior parity của official. Dock/navigation khác vẫn giữ baseline.
