# reCime Kinetic Kitchen — redesign V3 (22/10/2026)

## Mục tiêu
Làm ứng dụng trông như một sản phẩm ẩm thực dành cho Gen Z, không phải màn hình demo CRUD: typography mạnh, neon lime/coral trên dark navy, card ảnh lớn, nhịp chuyển cảnh. Tham khảo cảm hứng Lollypop Designathon 2026; KHÔNG copy logo/hình ảnh/code của trang.

## Các màn đã chỉnh
- `activity_main.xml`: floating bottom dock 5 tab, navigation shadow.
- `fragment_recipes.xml`: headline editorial, search và category chips, hero lớn, GridView 2 cột.
- `item_recipe.xml`: ảnh món cao, card bo 24dp.
- `fragment_import.xml`: AI kitchen neon card, OCR/camera/URL/caption pipeline giữ nguyên.
- `fragment_board.xml`: header lime cho meal planner và groceries, điều hướng tuần, ListView.
- `item_board.xml`: tiles dark có accent.
- `fragment_detail.xml`: ảnh large, serving stepper, CTA add to cart.
- `fragment_editor.xml`: editor dark, CTA Save the Flavour.
- `fragment_ai_settings.xml`: panel AI giữ nguyên phần kết nối.
- `preview_app_home.xml`: Android Studio design preview.
- `strings.xml`: app label reCime.

## Animation (không thêm thư viện)
- `Motion.enter`: màn hình fade/slide 420ms.
- `Motion.pop`: hero/dock pop-in.
- `Motion.stagger`: item mới trong BaseAdapter, delay theo position tối đa ~245ms.
- `Motion.card`: scale down/up khi chạm, không chặn click/long click.
- `res/anim/recime_*.xml`: fragment transition native.
- Respect `Settings.Global.ANIMATOR_DURATION_SCALE` cho View animations.

## Luồng dữ liệu không đụng đến
Giữ nguyên `com.example.recime`, `myrecipelab.db` và v3 schema, SQL DAO, AI backend laptop, ML Kit OCR, public source attribution, không tạo migration database mới.

## Test & review
- [x] Giữ toàn bộ ID màn hình mà Java đang dùng.
- [x] Rà dấu đóng mở các XML layout chính bằng kiểm tra tĩnh.
- [ ] Build thực tế Android Gradle trên GitHub Actions / Android Studio.
- [ ] Run instrumentation (emulator hoặc device) cho navigation/CRUD/OCR.
- [ ] Chụp screenshot trên thiết bị nhỏ và lớn, kiểm tra bàn phím che form.
- [ ] Kiểm tra animation với Animator duration scale = 0, hệ thống Reduce Motion.
- [ ] Review contrast màu trên màn AMOLED/LCD và TalkBack.

**Lưu ý:** Các thông tin về build/ảnh demo phải là kết quả chạy thật, không được suy ra từ kiểm tra tĩnh. Không có video quay app thật trong thay đổi này; không đưa video vào Git.
