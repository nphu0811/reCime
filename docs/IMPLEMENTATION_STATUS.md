# Trạng thái MyRecipeLab — 22/10/2026

## Bằng chứng lần nâng cấp này

- `gradlew.bat assembleDebug testDebugUnitTest lintDebug`: **PASS**,10 unit tests; lint0errors/140warnings (`evidence/v2-build-unit-lint-final.log`, `evidence/v2-all-tests-final.log`).
- Android instrumentation: **12/12 PASS**,0failure/error/skip trên Medium_Phone API37; `evidence/v2-all-tests-final.log`, `evidence/tests-v2/instrumented.xml`.
- `backend/.venv/Scripts/python.exe -m unittest backend.test_backend -v`: **7/7 PASS** (`evidence/backend-tests-final.log`). Bao gồm ONNX thật với ảnh tham chiếu, không phải accuracy ảnh mới.
- SQLite độc lập:547 hàng, `integrity_check=ok`; JSON547 catalogKey duy nhất;211 có ảnh/178 file. Audit độc lập trong `REVIEW_V2.md`.
-11 layout XML parse hợp lệ;6 control động trong board có tools:text. Chưa trực tiếp xác minh Android Studio Layout Editor bằng tương tác IDE.

## Tính năng

| Tính năng | Trạng thái | Code / bằng chứng / giới hạn |
|---|---|---|
| Catalog mở547 món và nguồn/giấy phép | Implemented & tested | assets/catalog; CatalogImporter, CatalogDatabaseTest; không đại diện mọi món Việt, có món quốc tế |
| Ảnh thật | Partial |211 món/178 ảnh, Glide;336 món thiếu ảnh thật, có ảnh minh họa chung nhóm được ghi nhãn |
| Room CRUD/yêu thích/lưu offline | Implemented & tested | LabDatabase/Dao/Repository; schema3/migrations1→2→3; tombstone ngăn phục hồi món đã xóa khi cập nhật |
| Recipes/Discover, cookbook/search | Implemented & tested | RecipesFragment; nhãn1 cookbook/món, không có cookbook rỗng/multiple membership |
| Java/XML redesign | Implemented & tested | Material ripple/elevation, Motion, theme; native runtime/build; Preview attrs kiểm tra XML, chưa quan sát IDE |
| Planner tuần | Implemented & tested | BoardFragment, Meal, PlannerUiTest; hộp chọn có tìm kiếm |
| Grocery CRUD/checkbox/gom nguyên liệu | Implemented & tested | BoardFragment, GroceryAggregator, domain/instrumentation tests; không đặt hàng |
| Manual import/parser Việt/Anh | Implemented & tested | ImportFragment, RecipeParser; giữ khoảng lượng nguyên văn để review |
| Photo Picker/camera/OCR | Implemented & tested | ML Kit bundled; instrumentation OCR có từ V1; camera vật lý/OCR chữ viết tay chưa kiểm định |
| Public URL Recipe JSON-LD | Implemented & tested | PublicRecipeImporter, tests; không hỗ trợ mọi site/JS/auth |
| Share ảnh/text | Implemented & tested | MainActivity ACTION_SEND; test ảnh món→AI→chi tiết công thức |
| Laptop AI settings/client/server | Implemented & tested | LocalAiClient, AiSettingsFragment, backend/app.py; actual Android→127.0.0.1 via adb reverse |
| Nhận diện ảnh món | Partial | CLIP ONNX/gallery178 ảnh, chọn ứng viên có nguồn; chưa kiểm định ảnh ngoài gallery, không đảm bảo định lượng đúng chỉ từ ảnh |
| Recipe-only TF-IDF tự train | Implemented & tested | backend/engine.py + train_recipe_model.py;547 món/13,098 features; retrieval chứ không phải generative LLM |
| Model ảnh train từ đầu | Partial | train_vision_scratch.py;133train/45validation,46.67% accuracy <53.33% majority; không bật để chọn công thức |
| TikTok metadata/caption | Partial | official oEmbed adapter + mock/host tests; live ví dụ không có caption, fallback dán caption/screenshot |
| TikTok video/audio extraction | Unsupported | Không có adapter tải hoặc speech; không có nút giả |
| Nutrition tự động từ nguyên liệu | Unsupported | Chưa có dữ liệu dinh dưỡng đáng tin cậy; không bịa số |
| Nutrition nhập tay có nguồn | Implemented & tested | Editor/Detail; thiếu dữ liệu có nhãn rõ |
| Portions/units | Implemented & tested | Domain tests; lạng100g, volume chuẩn tùy chỉnh; không đổi mass↔volume thiếu mật độ |
| LLM chuyên công thức/fine-tune | Unsupported | Chưa có LLM fine-tune được kiểm định; không yêu cầu API key/cloud |
| Sync đa thiết bị/tài khoản | Unsupported | DB local; chưa có backend đồng bộ dữ liệu cá nhân |

## Review và các lần sửa

Dùng skill understand-diff cùng skill hỗ trợ understand: dependency/plugin build + scan/graph/diff chạy thật, phạm vi graph17 file chính/59nodes được ghi rõ, không tuyên bố semantic coverage toàn bộ repo. Độc lập tìm2 lỗi P2 (catalog resurrect và khoảng lượng); đã sửa, có hồi quy. Binary scratch branch được xử lý defensively nhưng model không đạt vẫn bị tắt. Xem REVIEW_V2.md.

Planner từng gặp3 lượt lỗi (item ngoài màn hình, ambiguous closeSoftKeyboard ở test, root không focus). Đã tạm gác theo yêu cầu để sửa Preview/review dữ liệu, sau đó quay lại thêm root dialog matcher và kiểm thử. Lượt tổng thể tiếp theo gặp emulator mất package/activity service; khởi động lạnh emulator, không sửa/xóa dữ liệu người dùng để che lỗi. Kết quả cuối ở đầu trang; các log thất bại giữ để kiểm tra.

## Tiếp theo

Thu thập nguồn mở có phép cho336 món thiếu ảnh; bổ sung tập ảnh mới tách độc lập theo món/nguồn để đo nhận diện; cải thiện dataset tiếng Việt có biên tập; chỉ đưa model mới vào quyết định sau đánh giá đáng tin cậy. Nutrition cần bộ dữ liệu nguồn rõ, chưa dùng ảnh để khẳng định an toàn thực phẩm/dị ứng.

V1 docs được lưu tại archive/. Nghiên cứu REA cũ là bằng chứng lịch sử; không có APK ReciMe hợp lệ để xác minh internal implementation. Bản nâng cấp hiện tại là triển khai độc lập.


## Ảnh chụp runtime cuối

Đã cài lại APK sau test, mở và chụp thật bằng ADB; đã xem trực tiếp ảnh home/import/AI settings. File tại evidence/screenshots-v2/: home.png, import.png, ai-settings.png, planner.png, detail.png. Đây không phải ảnh Android Studio Preview.
