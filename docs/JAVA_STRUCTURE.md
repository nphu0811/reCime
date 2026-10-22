# Cấu trúc Java

Chia package theo chức năng học tập/phát triển đã thống nhất. Namespace/applicationId vẫn là `com.example.recime`; Activity launcher nay là `.activity.MainActivity`. Database private vẫn `myrecipelab.db`, SQLite database version 3 và nội dung bảng không thay đổi.

```text
com.example.recime/
├── activity/
│   ├── MainActivity.java
├── adapter/
│   ├── BoardAdapter.java
│   ├── RecipeAdapter.java
├── AIservice/
│   ├── LocalAiClient.java
├── fragment/
│   ├── AiSettingsFragment.java
│   ├── BoardFragment.java
│   ├── DetailFragment.java
│   ├── EditorFragment.java
│   ├── ImportFragment.java
│   ├── RecipesFragment.java
├── helper/
│   ├── CatalogImporter.java
│   ├── ExtractionAdapter.java
│   ├── GroceryAggregator.java
│   ├── Motion.java
│   ├── PublicWebExtractor.java
│   ├── RecipeParser.java
│   ├── Ui.java
│   ├── Units.java
├── model/
│   ├── BoardRow.java
│   ├── CatalogTombstone.java
│   ├── Grocery.java
│   ├── Ingredient.java
│   ├── Meal.java
│   ├── ParsedRecipe.java
│   ├── Recipe.java
├── sql/
│   ├── LabDao.java
│   ├── LabDatabase.java
│   ├── RecipeRepository.java
├── viewmodel/
│   ├── LabViewModel.java

```

| Folder | Trách nhiệm |
|---|---|
| activity | Activity khởi chạy, host Fragment, nhận ACTION_SEND và điều hướng |
| fragment | Giao diện từng màn hình, xử lý sự kiện, hiển thị LiveData |
| sql | LabDatabase, LabDao và RecipeRepository; truy vấn, CRUD, transaction, executor lưu trữ |
| model | Recipe, Meal, Grocery, CatalogTombstone là đối tượng dữ liệu lưu vào SQLite; Ingredient, ParsedRecipe, BoardRow là đối tượng dữ liệu không phải bảng |
| adapter | RecipeAdapter extends BaseAdapter cho GridView; BoardAdapter extends BaseAdapter cho ListView. Chỉ bind view, chuyển sự kiện ra callback; ListView chọn món dùng ArrayAdapter chuẩn Android |
| helper | Parser, đơn vị, gộp nguyên liệu, import dữ liệu, đọc web và trợ giúp UI/animation |
| AIservice | LocalAiClient gọi API laptop. Model ảnh/truy hồi và inference thật vẫn thuộc backend Python, không chuyển file Python vào source Java |
| viewmodel | LabViewModel cung cấp recipes/meals/groceries dưới dạng LiveData, dùng Repository để lấy dữ liệu |

## ViewModel khác Model, DAO và Bundle

- Model là dữ liệu một món/một bữa/một nguyên liệu.
- DAO định nghĩa thao tác đọc/ghi database; repository điều phối các thao tác đó.
- Bundle đóng gói dữ liệu nhỏ để truyền (ví dụ recipeId) hoặc lưu/khôi phục trạng thái.
- ViewModel cung cấp trạng thái/dữ liệu cho màn hình và tồn tại qua việc Activity được tạo lại do thay đổi cấu hình. Nó không tự lưu bền vững qua process death; dữ liệu bền vững của app thuộc SQLite.

Vì vậy giữ `viewmodel` riêng, không gộp vào model hay sql. Các adapter được tách thành class riêng, Fragment quyết định điều hướng, xác nhận xóa và gọi repository.

## Sau refactor

Đã cập nhật import toàn bộ main/unit/instrumentation, AndroidManifest và tools:context trong XML; script tạo Preview cũng trỏ tới package mới. Schema Room lịch sử được giữ trong `app/schemas/com.example.recime.sql.LabDatabase/`, giữ các schema lịch sử1/2/3. Không thay đổi bảng hay identity hash schema3. Các chỉnh sửa theme đang có của người dùng được giữ nguyên.

Bằng chứng build/unit/lint lần đầu: `evidence/package-refactor-build.log`. Lượt Android đầu phát hiện adapter giữ tham chiếu danh sách cũ sau khi filter gán danh sách mới (`evidence/package-refactor-device.log`). Đã sửa `shown` thành danh sách final, clear/add nội dung trước khi notify. Test UI hiện có kiểm tra tìm món vừa tạo đã bắt lỗi này; không sửa test để bỏ qua lỗi.

Xác minh toàn bộ sau sửa: `evidence/package-refactor-final.log`. Kiểm tra package/adapter boundary tại `evidence/package-refactor-review.json`; schema tại `evidence/package-refactor-schema.json`.


Kết quả cuối: assembleDebug/testDebugUnitTest/connectedDebugAndroidTest/lintDebug PASS;10 unit và12 Android tests,0failure/error/skip; lint0errors. XML test nằm tại evidence/package-refactor-tests/.
