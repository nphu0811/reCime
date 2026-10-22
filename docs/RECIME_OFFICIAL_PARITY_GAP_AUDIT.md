> Cập nhật 23/10/2026: phần dưới là baseline lịch sử. APK 6.0.2 (217) đã đọc bằng REA/JADX và Emulator đã có com.recime.app từ Play. Source master e4460c6 đã bổ sung nhiều chức năng sau audit này. Xem [findings](REA_ANDROID_6_0_2_FINDINGS.md) và [ma trận hiện tại](FEATURE_PARITY_MATRIX.md); các blocker “chưa có APK” bên dưới không còn đúng.

# Đối chiếu ReciMe Android thật ↔ reCime (audit 22/10/2026)

> **Kết luận trạng thái:** Đã kiểm tra Google Play/Help Center chính thức và source `master` của `nphu0811/reCime`. **CHƯA phân tích APK chính thức bằng REA/JADX, CHƯA quan sát runtime ReciMe Android.** Không được ghi "đã reverse engineer APK" hoặc "clone 1:1" trên cơ sở nguồn hiện có. Đối tượng Android chính thức: `com.recime.app`; app độc lập của chúng ta: `com.example.recime`.

## 0. Nhận diện nguồn và mức độ bằng chứng

- **S1 – Official Play Store:** https://play.google.com/store/apps/details?id=com.recime.app
- **S2 – Help Center tổng quan:** https://recime.app/help/en/articles/11594896-que-es-recime
- **S3 – Import từ social và fallback:** https://www.recime.app/help/en/articles/14773584-why-didn-t-my-recipe-import-correctly
- **S4 – Meal Plan:** https://recime.app/help/en/articles/14999930-how-to-use-your-meal-plan
- **S5 – Grocery:** https://recime.app/help/de/articles/16546825-how-to-use-groceries (nội dung tiếng Anh)
- **S6 – Cookbook:** https://recime.app/help/en/articles/13571088-how-do-i-create-a-cookbook-on-recime
- **S7 – Search:** https://recime.app/help/en/articles/13571129-how-can-i-search-recipes-on-recime
- **S8 – Ask ReciMe:** https://www.recime.app/help/en/articles/14999407-how-to-use-ask-recime
- **S9 – Nutrition:** https://recime.app/help/en/articles/11626106-does-recime-provide-nutrition-information
- **S10 – Export/print:** https://help.recime.app/features/3ukvHdGyGWdzhJ4yS4wfCm/can-i-print-or-export-my-recipes/rxivkCogywmfTtjPaAGAyc
- **S11 – Grocery checkout:** https://recime.app/help/en/articles/13123523-how-to-use-grocery-checkout
- **S12 – Translate:** https://recime.app/help/en/articles/13571173-can-i-translate-recipes-in-recime
- **S13 – ReciMe Plus:** https://recime.app/help/en/articles/11630603-what-are-the-benefits-of-recime-plus
- **S14 – REA Android:** https://github.com/morluto/rea/blob/main/docs/android-analysis.md
- **S15 – REA ADB:** https://github.com/morluto/rea/blob/main/docs/adb-device-analysis.md
- **S16 – App Store iOS:** https://apps.apple.com/us/app/id1593779280

Ảnh `IMG_4022.jpeg` người dùng gửi là **màn hình listing iOS/App Store**, không phải bản ghi chạy Android hoặc tệp APK. Ảnh iOS có thể khác UI Android hiện tại. `docs/REA_ANALYSIS.md` của chính repo ghi trước đó REA không thấy `com.recime.app` trong emulator và không có APK hợp lệ để dịch ngược.

Các mức: **CÓ** = mã nguồn hiện có; **MỘT PHẦN** = logic gần đúng nhưng thiếu điều kiện; **THIẾU** = không có triển khai tương ứng tìm thấy trong main source; **CHƯA XÁC MINH** = cần APK/black-box; **VỚI RECI ME** = chức năng ReciMe do nguồn chính thức công bố chứ không phải quan sát runtime.

## 1. Ma trận luồng/tính năng — có bằng chứng cụ thể

| ID | Luồng/tính năng ReciMe công bố | Code reCime hiện tại | Đánh giá | Ưu tiên | Bằng chứng |
|---|---|---|---|---|---|
| F01 | Import Instagram posts/reels → caption → audio → source web | `ImportFragment.fetch` chỉ đặc biệt xử lý TikTok; `PublicWebExtractor.validate` từ chối URL social | THIẾU | P0 | S3; ImportFragment.java; PublicWebExtractor.java |
| F02 | Import TikTok qua Share → recipe đầy đủ khi dữ liệu truy cập được | `/import/tiktok` chỉ official oEmbed metadata/caption, nếu trả được; không có video/audio extraction | MỘT PHẦN | P0 | S3; backend/app.py |
| F03 | Import Facebook/Youtube/Pinterest công khai | Không thấy adapter nhập từ video của các nền tảng này; chỉ URL Recipe JSON-LD trang web ngoài | THIẾU | P0 | S1; PublicWebExtractor.java |
| F04 | Web Recipe public link → ingredients, steps | Có parser Recipe JSON-LD, HTTPS, chống địa chỉ nội bộ | MỘT PHẦN | P0 | S1; PublicWebExtractor.java |
| F05 | Import qua Sharesheet của ứng dụng khác | Có ACTION_SEND text/plain, image/* → `ImportFragment` | CÓ (phạm vi text/ảnh) | P0 | MainActivity.java / Manifest |
| F06 | Scan printed cookbook / handwritten notes | ML Kit OCR ảnh, camera/Photo Picker, sửa text trước lưu; chưa có đánh giá chữ viết tay thực | MỘT PHẦN | P1 | S1; ImportFragment.java |
| F07 | Copy/paste hoặc tự nhập recipe → form review | Có `raw`, parser và Editor | CÓ | P0 | ImportFragment.java; EditorFragment.java |
| F08 | Cookbook entity CRUD: tạo, đổi tên, xóa, ảnh cover | Chỉ `Recipe.cookbook: String`, Spinner nhóm; chưa có table/fragment cookbook entity độc lập | THIẾU | P0 | S6; Recipe.java; LabDatabase.java; RecipesFragment.java |
| F09 | Mỗi cookbook hiển thị card ảnh + số recipe + drill down | App hiển thị `GridView` recipe, không có cookbook overview dạng collection | THIẾU | P1 | S6; fragment_recipes.xml |
| F10 | Tìm theo recipe title, exact ingredient picker, multi-tag filter | Search q là contains() title/tags/ingredients, category chip dựa keyword tên; không có ingredient picker và bộ lọc tag động | MỘT PHẦN | P0 | S7; RecipesFragment.filter() |
| F11 | Favorites / edit/delete recipe | Có favorite, editor, xóa bảo toàn tombstone | CÓ | P1 | DetailFragment.java; LabDatabase.deleteRecipe |
| F12 | Meal Plan Breakfast/Lunch/Dinner/**Snack**, nhiều món/cùng ngày; từ trang recipe hoặc planner | `render()` hardcode 3 loại Sáng/Trưa/Tối, `meals.slot` PRIMARY KEY; không có add-from-detail | MỘT PHẦN | P0 | S4; BoardFragment.java; LabDatabase.java |
| F13 | Meal planner drag/drop, remove all, configurable start day | App có prev/next week, chọn/xóa; không có drag/drop, bulk clear, chọn tuần bắt đầu | MỘT PHẦN | P1 | S4; BoardFragment.java |
| F14 | Grocery tự động phản ánh kế hoạch hiện tại | `planGroceries()` yêu cầu bấm và cộng vào bảng; bấm lại cộng lặp, xóa bữa không tự cập nhật | THIẾU auto sync | P0 | S4/S5; BoardFragment.planGroceries() |
| F15 | Grocery sort aisle/recipe/A-Z/recent, checked items phía dưới, carousel món liên quan | Có category và checkbox, nhưng ListView một chiều; thiếu thay đổi sort, recipe linkage/carousel và checked section | MỘT PHẦN | P1 | S5; BoardFragment.render() |
| F16 | Grocery add từ recipe, nhập tay, gộp phù hợp | Có `RecipeRepository.addIngredients`, `GroceryAggregator`, CRUD; không có nguồn liên kết bền vững | MỘT PHẦN | P1 | S5; RecipeRepository.java |
| F17 | Grocery checkout/đối tác mua hàng | Không có provider và order flow | THIẾU | P2 | S11; BoardFragment.java |
| F18 | Scale servings | Có tăng/giảm, nhân lượng gốc bằng `Ingredient.scale` | CÓ (cần test biên) | P1 | DetailFragment.java |
| F19 | Convert units imperial ↔ metric | Có một phần g/kg, ml/l, thìa/cup; cần rà oz/lb và đa ngôn ngữ | MỘT PHẦN | P1 | S2; Units.java |
| F20 | Calculate nutrition kcal, protein, carbs, fat | App chỉ cho nhập nutrition thủ công kèm nguồn, không tự ước lượng | THIẾU | P1 | S9; EditorFragment.java |
| F21 | Cooking Mode từng bước, keep screen awake, hands-free | Detail chỉ in steps thành text; không có màn cooking mode hoặc keep-awake | THIẾU | P1 | S13; DetailFragment.java |
| F22 | Ask ReciMe contextual assistant in each recipe | AI laptop chỉ search retrieval/CLIP; không có hội thoại hỏi trong recipe detail | THIẾU | P1 | S8; LocalAiClient.java |
| F23 | Export recipe PDF / print / share URL | App chia sẻ shopping list dạng text; không có in/PDF/URL recipe public | THIẾU | P1 | S10; DetailFragment.java |
| F24 | Cloud account + sync across iOS/Android/web | DB private `myrecipelab.db`; không có đăng nhập/sync service | THIẾU | P1 | S2; LabDatabase.java |
| F25 | Hợp tác cookbook/multi-user | Không có authentication, permission, invitation | THIẾU | P2 | S2; database |
| F26 | Auto translate recipes khi import | Parser VI/EN nhưng không có translator hoặc tùy chọn ngôn ngữ | THIẾU | P2 | S12; ImportFragment.java |
| F27 | Nutrition / cooking import UX error recovery & sources | Có review/error và attribution; chưa có progress/result state machine, duplicate handling đầy đủ | MỘT PHẦN | P1 | S3; ImportFragment.java |
| F28 | Subscription/paywall/import quotas | App độc lập không có gói thương mại; không cần sao chép paywall khi nghiên cứu UX | KHÁC THEO THIẾT KẾ | P3 | S13 |
| F29 | Ảnh chất lượng cao cho mọi công thức | Dataset 547, 211 có ảnh thật (178 unique), còn 336 thiếu | MỘT PHẦN | P1 | docs/DATASET_AND_AI.md |
| F30 | AI món từ ảnh có đánh giá ngoại miền | CLIP ONNX local từ ảnh catalog, chưa benchmark ảnh người dùng ngoài tập nguồn | MỘT PHẦN / CHƯA ĐO | P1 | backend/engine.py |
| F31 | Offline CRUD / persistence | Có SQLiteOpenHelper v3, DAO, test Room migration v1/v2/v3 | CÓ | P1 | LabDatabase.java; androidTest |
| F32 | Accessibility, adaptive UI, haptic/animation ngang ReciMe | Có motion native nhưng không có recording/layout tree APK ReciMe; parity không thể đo | CHƯA XÁC MINH | P1 | DESIGN_V3.md + yêu cầu REA APK |

**Quan trọng:** Không gán tên framework, tên class, endpoints, thuật toán nội bộ, database, crypto của ReciMe. Các nguồn public chỉ xác nhận tính năng marketing/tài liệu hướng dẫn.

## 2. Đối chiếu UI/UX: quan sát được và giới hạn

| Mảng | ReciMe từ ảnh iOS/help chính thức | reCime từ mã XML | Chênh lệch và hướng sửa |
|---|---|---|---|
| Information architecture | Cookbook-centric, kế hoạch bữa ăn, groceries, smart import | Dock 5 tab HOME / AI LAB / PLAN / CART / EXPLORE | Navigation taxonomy khác, thiếu màn Cookbooks làm đối tượng hạng nhất |
| Visual foundation | Ảnh promo nhiều cream/white/orange, ảnh món + cookbook card | Midnight gradient + lime/coral, hero editorial, floating dock | Không giống style mẫu; riêng biệt về thương hiệu. Cần ảnh chạy Android thật trước khi chọn thiết kế chuẩn |
| Home/cookbooks | Các cookbook có ảnh cover và count | Home là featured recipe + recipe GridView 2 cột | Thiếu phân cấp library → cookbook → recipes |
| Discover | Search title/ingredient exact/tags | Search text contains + category regex heuristic | Không có picker chọn nhiều nguyên liệu chuẩn hóa; dễ false positive |
| Recipe detail | Theo tài liệu có servings, unit switch, nutrition, print/share, Ask AI, cooking mode | Detail có portions, unit, ingredients/steps, nutrition tay, add grocery | Thiếu các action nâng cao, highlight quantity, cooking assistant |
| Meal Plan | Theo tài liệu nhiều món/ngày, Breakfast/Lunch/Dinner/Snack, drag & drop | 21 hàng ListView tĩnh/tuần; thao tác bằng dialog | Cấu trúc dữ liệu và tương tác chưa ngang |
| Grocery | Phân mục aisle, recipe sources, sort, auto sync, checked bottom | ListView đơn, checkbox, category text, add từ plan bằng nút | Thiếu grouping/linked source và reactive sync |
| Social import | ReciMe chia sẻ từ social, xử lý caption/audio/site | Text/link/ảnh → OCR/JSON-LD/TikTok oEmbed | Khác pipeline nghiệp vụ (đây là chênh lệch P0 lớn nhất) |
| Animation | Không có APK/video Android chạy thật nên không suy đoán transitions | Motion.enter/stagger/pop/card + native fragment animation | Không có cơ sở nói animation giống ReciMe; cần screen record cả hai và đo |

**Chú ý:** Hình iOS là tài liệu minh họa, KHÔNG xác nhận Android dùng đúng màu/size/gesture. UI phải được kiểm tra trực tiếp trên cùng thiết bị và cùng trạng thái dữ liệu.

## 3. Luồng chuẩn cần đối chiếu theo hành trình người dùng

**H1. Import social:** Share URL ReciMe → đọc caption (nếu công khai) → thử audio nếu khả dụng và được phép → trích xuất công thức → edit/review → chọn cookbook → save → mở detail. Test các biến thể: caption đầy đủ, không caption, video không công khai, không mạng, clip không có recipe, trùng URL. Không dùng API riêng/DRM/secrets.

**H2. Library:** Mở Cookbook tab → tất cả / cookbook tùy chỉnh → tạo/đổi tên → thêm hoặc bỏ recipe khỏi cookbook → tìm theo title, ingredient picker, tag.

**H3. Recipe detail:** Mở recipe → tăng số phần, đổi unit, hiển thị ingredients/steps → Cooking Mode step-by-step → AI trợ giúp nếu được thiết kế → nutrition có nguồn → share/PDF/print → add vào planner/grocery.

**H4. Planner:** Chọn ngày → Breakfast/Lunch/Dinner/Snack → nhiều món → đổi vị trí/clear week → grocery derived từ trạng thái món của tuần; xóa bữa phải cập nhật grocery mà không mất item nhập tay.

**H5. Grocery:** Nhóm aisle/recipe/A-Z/recent → kiểm tra các món cần mua → checked section phía dưới → chỉnh số lượng & nguồn → share, khả năng order partner nếu địa phương hỗ trợ.

**H6. Account & sync:** Login trên 2 thiết bị → tạo recipe/cookbook/meal/grocery trên thiết bị A → B cập nhật; offline edit → sau nối mạng giải quyết conflict và tránh mất dữ liệu; không được giả lập sync bằng local DB.

## 4. Kiến trúc cần thay nếu mục tiêu đạt tương đương chức năng

1. **P0 - Social import pipeline:** `ImportJob` và state machine `PENDING→FETCHING→PARSED→REVIEW→SAVED/FAILED`, adapter công khai/được phép theo platform, fallback caption/text/OCR; request retry idempotent, error code, không suy diễn lượng.
2. **P0 - Cookbook entity:** Bảng `cookbooks` (id/name/cover/sort) và `cookbook_recipes` many-to-many hoặc quan hệ được chọn; migration v4, backup trước test.
3. **P0 - Planner many-to-many:** `meal_entries(id, day, mealType, recipeId, position, servings)` thay `meals(slot PK)`, migration bảo toàn bữa cũ, hỗ trợ snack/multiple meal.
4. **P0 - Reactive groceries:** `planned_ingredients` derived from meal_entries và grocery_manual_items độc lập; view kết hợp tính lại khi plan đổi; không cộng lặp khi bấm.
5. **P0 - Ingredient search:** index nguyên liệu chuẩn hóa, picker chips, lọc AND/OR có ngữ nghĩa kiểm thử (không dùng contains chuỗi ngẫu nhiên).
6. **P1 - Cooking Mode:** từng bước / next-prev, keep-screen-on chỉ trong cooking mode, user option accessibility; không làm biến mất thao tác Android Back.
7. **P1 - Nutrition:** dataset dinh dưỡng có nguồn/đơn vị, chuẩn hóa nguyên liệu, confidence/missing labels; không gắn dữ liệu AI ước đoán như số chính xác.
8. **P1 - Share/Export:** PDF/Android PrintManager, intents/FileProvider hợp lệ, explicit permission, export giữ nguồn.
9. **P1 - Account/sync:** kiến trúc mới có auth, storage API, mã hóa trong truyền tải, sync queue/revisions/conflict; cần server có quyền và audit privacy.
10. **P1/P2 - Assistant & localization:** trợ lý nội dung theo recipe với kiểm tra nguồn, dịch khi import, hỗ trợ screen sizes, offline fallback.

Phần dùng Java/XML + BaseAdapter có thể giữ. Không bắt buộc RecyclerView/Compose chỉ để giống giao diện. Khi phát triển phải đảm bảo DB migration không phá 547 món và dữ liệu tự nhập.

## 5. Tiêu chí xác nhận hoàn thành (verification gate)

Một feature **chỉ** được đánh PASS khi:
- Có source hoặc thao tác thật từ app ReciMe chính thức chứng minh expected behavior;
- Chạy cùng use case trong app độc lập, thành công với input normal + error + offline + restart;
- Unit/instrumentation tests xanh và ghi kèm SHA commit + log;
- Ảnh/video chạy thật và kết quả review UI có thời điểm, OS và màn hình tương đương (nếu yêu cầu parity UI);
- Không sao chép trái phép brand assets, khóa riêng, API độc quyền; app được trình bày trung thực là sản phẩm độc lập.

Không có phép thử nào có thể xác nhận `com.example.recime` **là** bản chính thức `com.recime.app`: việc xác thực APK phát hành yêu cầu chứng thư ký số/đường phân phối được kiểm chứng. Chức năng 1:1 cũng không tương đương bytecode/chữ ký.

## 6. Kết quả hiện tại / blocker

- **Baseline reCime:** `master` commit `2a83d401`, GitHub Actions run `39801911064` kết quả **SUCCESS** cho assembleDebug + unit + lint; chưa bao gồm instrumentation trên Android thật cho bản redesign.
- **REA/JADX APK gốc:** BLOCKED — hiện không có artifact APK ReciMe Android thật, không có môi trường REA/JADX được kết nối trong cuộc trò chuyện, không có session runtime ReciMe.
- Lịch sử `docs/REA_ANALYSIS.md` từng chạy REA CLI vào emulator nhưng kết luận `com.recime.app` **không được cài** và không có APK.
- **Không công bố hoàn tất clone 1:1** và **không tuyên bố đã verify real APK**.

Tiếp theo cần bản ứng dụng **được cài hợp lệ từ Play Store** trên Android/emulator để thu thập bằng chứng runtime và APK đúng nguồn; xem `docs/REA_REAL_ANDROID_CHECKLIST.md`. Không upload APK bên thứ ba lên git công khai.
