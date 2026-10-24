# ReciMe Android 6.0.2 — kết quả REA/JADX

Ngày khảo sát: 23/10/2026. Đối tượng APK `com.recime.app`, version 6.0.2, versionCode 217. Ứng dụng tái triển khai giữ package riêng `com.example.recime`.

## Phạm vi và cách đọc bằng chứng

- **STATIC VERIFIED**: đã đọc method body hoặc DEX/smali, có đường gọi hoặc reference cụ thể. Inventory chỉ chứng minh member tồn tại, không chứng minh tính năng hoạt động.
- **RUNTIME VERIFIED**: phải có thao tác/screenshot của ứng dụng chính thức; ghi riêng trong `REAL_RECIME_UI_UX_MAP.md`. Báo cáo static này không thay thế kiểm chứng runtime.
- **DOCUMENTED**: tài liệu chính thức mô tả; không nâng lên static/runtime nếu chưa có chứng cứ tương ứng.
- **UNKNOWN**: chưa đủ bằng chứng; không có nghĩa tính năng vắng mặt.

Base APK SHA-256: `8de0a42162aa3c50901269d69dd04c89c67f76e3fdfaf6f859c6fa9f95da19a4`. REA 6.7.0, JADX headless 0.7.1, JDK 21.0.10. Inventory đọc được 20.229 classes và 2.722 resources. Evidence JSON gốc giữ ngoài Git ở `C:/Users/admin/Desktop/ReciMe-Official-6.0.2/research-20261023/`; bảng cuối ghi tên file, class/method và evidence ID. Không đưa APK, decompiled source, tài nguyên độc quyền hoặc dữ liệu tài khoản vào repository.

REA AndroidAnalysisService/JadxProvider giữ phiên APK đã load và được gọi qua service local để tránh load lặp; đây vẫn là phân tích của REA/JADX. Java output có warning, coroutine hoặc shared synthetic lambda được kiểm tra theo branch. Khi Java decompile lỗi, dùng smali và chỉ kết luận những lệnh/call đã đọc rõ.

## 1. Kiến trúc

**STATIC VERIFIED:** `ReciMeActivity.onCreate` thiết lập localization, notification launcher, initial intent/deep-link state và Compose content thông qua `pf7`. Chuỗi `pf7.invoke → i84.o → cg7.invoke` tạo root ViewModels, lifecycle-aware navigation controller, user/deep-link effects và subscription state UI. Đây là đường UI Compose thực chạy từ Activity, không chỉ do tìm thấy thư viện Compose trong APK. Không kết luận mọi màn đều Compose hoặc không có Fragment chỉ từ phạm vi này.

**UNKNOWN:** kiến trúc backend, cơ sở dữ liệu server, hàng đợi đồng bộ/conflict và mô hình AI phía server.

## 2. Activity và navigation

**STATIC VERIFIED:** `cg7.invoke → bc6.invoke` branch 3 gọi host `o84.c` với start route `splash`; `bg7.invoke` branch 3 đăng ký destinations và callbacks. Routes đã đọc gồm `base`, `sign_in`, `sign_up`, `forgot_password`, `discover`, `meal-plan`, `groceries`, `more_menu`, `cookbook/{cookbookId}`, `browser`, `import`, `recipe/{postId}`, `post_editor/{postId}`, `search_recipes`, `settings`.

Route base nhận flags mở cookbook sheet, recipe modal, invite share, social-media modal và cookbook invitation ID/role. Một số destination có nested navigation host; không coi danh sách trên là exhaustive toàn ứng dụng. Các Activity chính/package/manifest được giữ trong `package.json`, `main-class.json`, `share-class.json`, `pip-class.json`.

## 3. Hệ thống UI

**STATIC VERIFIED:** root graph đăng ký Compose destination lambdas, lấy arguments từ navigation bundle, truyền ID/callback/ViewModel vào hàm màn hình. Recipe, editor, cookbook, settings không cần một Android Activity riêng cho từng màn. App độc lập đang dùng Java/XML Fragment; có thể giữ kiến trúc này khi tái tạo hành vi, không cần sao chép framework gốc.

**UNKNOWN:** toàn bộ layout tree, kích thước, font, gesture và accessibility của các màn chưa mở. Runtime screenshots phải được đối chiếu cùng trạng thái dữ liệu.

## 4. Cookbooks

**STATIC VERIFIED:** `createCookbook → dz5.invokeSuspend` gọi repository với title, phân nhánh `NetworkResponse.Success`, thêm cookbook trả về vào draft selection và callback kết quả; thất bại generic error. `deletePostsFromCookbook → hz5.invokeSuspend` gửi cookbook ID và recipe IDs, cập nhật posts khi thành công, hiển thị thông báo số recipe đã bỏ; đây là bỏ recipe khỏi cookbook, không phải bằng chứng xóa recipe toàn cục.

Các member role, cover/icon, rename và delete có inventory nhưng chỉ kết luận đầy đủ sau khi đọc body và/hoặc runtime. Không suy permission policy phía server từ tên role.

## 5. Recipe list, search, filters, favorites

**STATIC VERIFIED:** cookbook `applyIngredients` cập nhật list ingredients trong `RMRecipeFilterState` rồi áp dụng filter. Filter state kiểm tra tags, ingredients, totalTime, cooked, rating và sort khác DEFAULT để xác định request cần thiết. Coroutine cookbook gửi ID/search/filter state tới repository rồi nhận post IDs, cập nhật loading và fallback rỗng.

Global search `searchedRecipes` hủy searchJob trước, khởi chạy coroutine đọc filter state và gọi recipe repository. Trace có direct callers từ applyIngredients/applyTags/clear filters/cooked/rating/total time/sort. Java coroutine search có biểu thức null không đáng tin; không suy bug ứng dụng từ biểu thức decompile đó.

**UNKNOWN:** ngữ nghĩa AND/OR ingredient phía server, ranking, debounce, indexing, favorites và quan hệ favorites với pinned/cooked/rating. Không coi các khái niệm này tương đương.

## 6. Recipe detail

**STATIC VERIFIED:** root registry có `recipe/{postId}`; `pw6.invoke` gọi thật các action Ask ReciMe, edit tracking, Cook with ReciMe, conversion, subscribe và export PDF theo branch. Method body nutrition/export/cooking bên dưới xác định loading hoặc subscription gate; sự có mặt của button/action không chứng minh kết quả nội dung.

**UNKNOWN:** đầy đủ thao tác ingredient check, recipe timer, video playback, serving controls và offline detail của bản gốc cho tới khi có body/runtime tương ứng.

## 7. Import social, web, ảnh và text

**STATIC VERIFIED:** `RMInAppImportScreenViewModel.fetchRecipeFromText` gửi list text tới repository và publish NetworkResponse vào delegate. Image import gửi image/string và list context qua repository; không đủ kết luận OCR chạy trên thiết bị hay server. HTML import resolve redirect, fallback original URL nếu thất bại, gửi URL/HTML tới repository và publish response.

Share extension `fetchRecipeFromTextOrUrl` extract URL từ shared text, launch coroutine; `fetchRecipe` lưu URL và khởi chạy fetch cùng URL/HTML. Platform inventory không chứng minh thuật toán audio/video của từng provider. **UNKNOWN:** private provider access, caption/audio extraction, transcript models, credentials, server parsing pipeline, coverage TikTok/Instagram/Facebook/YouTube thực tế.

## 8. Review, editor và save

**STATIC VERIFIED:** import-save coroutine chỉ lưu khi recipe load thành công; lấy optional nutrition và selected cookbooks, gọi delegate save; lỗi được publish dưới dạng NetworkResponse.Error. Editor-save coroutine đọc post result, content/info/nutrition ViewModels và dispatch save/update; smali có nhánh recipe mới/cũ. Không sao chép logic gốc để triển khai.

**UNKNOWN:** validation đầy đủ, duplicate handling, retry idempotency, persistence của draft và server transaction semantics.

## 9. Meal Planner

**STATIC VERIFIED:** MealType enum có BREAKFAST/LUNCH/DINNER/SNACK. `startDrag` publish dragging/item ID; `completeDrop` gọi `moveMealItemToDay`, rồi stopDrag. Move cập nhật ngày của item theo ID trong response thành công, refresh grouping rồi launch coroutine mang original response. Chưa xác minh đầy đủ rollback coroutine.

`getUserFirstDayOfWeek` đọc user preferences, fallback MONDAY. Settings updateFirstDayOfWeek ghi analytics thay đổi và khởi chạy update coroutine. **UNKNOWN:** kết quả cloud save, tất cả edge cases và parity gesture khi chưa runtime.

## 10. Groceries và quan hệ với Meal Planner

**STATIC VERIFIED:** `prepareGroceryListFromMealPlan` chọn meal IDs có postId. `addSelectedIngredientsToGrocery → qi6.invokeSuspend` đọc grocery ID, selected ingredient IDs, recipe ingredients, serving overrides; tính serving ratio và unit display; gửi batch recipes/ingredients tới grocery repository. Success clear selection/callback và thông báo; failure có log/toast.

**Không đủ bằng chứng continuous auto-sync:** đường đã đọc là chuẩn bị/chọn/thêm ingredient. Không kết luận xóa meal tự động xóa grocery hoặc tất cả meals mọi ngày luôn được derive. App độc lập đang derive tự động nên phải coi là sai khác chưa đối chứng, không ghi đạt parity.

Groceries `stageInput` split comma/newline, trim, bỏ rỗng, dedup tên lowercase vào draft và launch parse. `updateItemChecked` nhận list ID/item IDs và dispatch repository checked/unchecked; rỗng là no-op. Sort enum xác minh AISLE/RECIPE/ALPHABETICAL/TIMESTAMP, mặc định AISLE; builder group items theo sort key rồi tạo display groups. **UNKNOWN:** exact aggregation, aisle classification, source reconciliation, checked-section behavior ở mọi sort và merchant backend.

## 11. Cooking Mode và PiP

**STATIC VERIFIED:** `pw6.invoke` branch 2 gọi `onCookWithReciMe`; method này ghi event và đăng ký Superwall. Callback `onCookWithReciMe$lambda$0` chỉ gọi markUserAsSubscribed. **Chưa chứng minh màn cooking từng bước riêng** từ đường này; không suy nội dung cooking mode từ tên marketing.

PiP là đường khác: `ya.invokeSuspend` branch chứa `extra_platform` đợi 2 giây, đọc Boolean state, kiểm tra system feature PiP rồi launch `RMPipActivity` với platform enum; nếu không đủ điều kiện gọi fallback mở platform. `RMPipActivity.onCreate` parse platform; thiếu/sai thì finish; tạo player và phát **bundled platform asset** theo `android.resource://`, không chứng minh phát recipe video. PiP params auto-enter, aspect ratio fallback 9:16; startup delayed 400ms. **UNKNOWN:** semantic tên Boolean state và điều kiện UI khởi động branch tới khi trace caller hoàn chỉnh/runtime.

## 12. Tài khoản và đồng bộ

**STATIC VERIFIED:** `RMUserServiceImpl.signInWithEmail` gọi Firebase Auth signInWithEmailAndPassword, lấy Firebase current user UID và cập nhật service state sau completion. `syncUser` no-op anonymous, còn lại chạy coroutine `ic7` branch 3; branch gọi API service.syncUser, kiểm tra HTTP success và throw error khi thất bại.

**Giới hạn:** đây là auth và user-sync request boundary. Không chứng minh recipe/cookbook/meal/grocery synchronization end-to-end, offline queue, conflict resolution, encryption, multi-device consistency hoặc server data model. Không dùng secret/config gốc để xây backend riêng.

## 13. Nutrition, AI và hỗ trợ

**STATIC VERIFIED:** nutrition calculation lưu request title/ingredients/servings, publish Loading và chạy coroutine với previous response/throttle flags; không thấy thuật toán tính trong body này. Ask ReciMe sendMessage trim, từ chối empty/busy, thêm user message và assistant placeholder, gửi RMChatRequest với postId và previousResponseId. Có conversational request state; không xác định model/provider/giá/chất lượng từ APK.

**UNKNOWN:** nutrition database, food mapping, inference model, prompt server, confidence/calibration và dịch recipe khi chưa inspect đường riêng.

## 14. Share, export và deep links

**STATIC VERIFIED:** `ReciMeActivity.j` xử lý VIEW khi host thuộc configured Singular link domain hoặc join.recime.app; onCreate đưa URI vào state rồi clear intent data. Root registry có home/sign_in/discover/meal-plan/groceries/browser deep links, cùng route parameters cookbook/post/editor/import. Export PDF action có Superwall registration khi showPaywall=true; chưa đủ chứng minh file generation/print workflow.

Không tái sử dụng app links, credentials, chữ ký hoặc provider endpoints độc quyền để giả mạo bản gốc. **UNKNOWN:** full web-domain routing, invite validation, public access policy và export output.

## 15. Onboarding, loading, errors, subscription

**STATIC VERIFIED:** Activity cài splash handling; root starts splash; onboarding initialize restore referral state rồi launch coroutine. Import/search/filter/nutrition có NetworkResponse hoặc loading state. Cookbook create/remove có success và generic-error paths. Root render subscription activation state; Cook with ReciMe và PDF có explicit Superwall hooks.

**UNKNOWN:** giá, quota, entitlement rules, trial eligibility, server-driven offer, full onboarding sequence và error text thực hiển thị ở mọi locale. Method registration không đồng nghĩa user đã mua hoặc feature đã unlock.

## 16. Motion, transitions, gestures, haptics

**STATIC VERIFIED:** root destination registry truyền `eg7` enter/exit/pop callbacks cho search/settings và một số routes. `eg7.invoke` branch 0–14 gọi `ck.a/e` với animation factory `ai8.J(500, 0, null, 6)`; factory body tạo animation specification với duration 500 và default delay/easing. Chỉ xác minh cấu hình này, không khẳng định duration cảm nhận/FPS hoặc trục/chiều của mọi transition trước khi resolve ck body/runtime.

Planner drag/drop có state/callback gọi thật như mục 9. **UNKNOWN:** haptic signal cho thao tác app cụ thể; presence framework haptics không chứng minh app gọi haptics. Không suy mọi màn dùng cùng animation, hoặc emulator có thể phản ánh rung vật lý.

## Coverage và phần cần bổ sung

Phạm vi là các Activity entrypoints, root Compose/nav chain, cookbook/filter/search, planner/grocery, import/review/save, auth/user-sync, nutrition/chat request, subscription hooks và PiP platform branch đã đọc. Chưa exhaust toàn bộ obfuscated classes và không phân tích server. Runtime/map/test report phải ghi riêng trạng thái thực mở được và input đã dùng. Các baseline cũ ghi thiếu APK/thiếu DB v4 chỉ còn giá trị lịch sử; ma trận mới ưu tiên method evidence này và source master thực tế.

## Chỉ mục evidence ngoài repository

Tên file bên dưới tương đối với thư mục evidence nêu đầu báo cáo. Mỗi JSON giữ metadata APK/provider, raw response, normalized result và các giới hạn. Class/method là địa chỉ chứng cứ, không phải mã được đưa vào ứng dụng độc lập.

| File | Class / method | Evidence ID |
|---|---|---|
| `main-onCreate.json` | `com.recime.ReciMeActivity.onCreate` | `ev_bd6f299a8eb1aa9a17e97c350391651293e95e55f04e7c05a983b952d24d0502` |
| `root-compose-invoke.json` | `defpackage.pf7.invoke` | `ev_90cd9cf4a81acc91e5a82e25f04448df13051beca9989d5ad09cf5a417d5d626` |
| `root-navigation.json` | `defpackage.i84.o` | `ev_23265a700154deecedfa5a17ecc257b68a1e94be6c30bda7dab3001cf3b467d3` |
| `agent-source-root-graph-invoke.json` | `defpackage.cg7.invoke` | `ev_65257d2b80ebd4529828d9fe6bf0e1e09d8ef404e3672a06b44448be5bddaac1` |
| `agent-source-nav-content.json` | `defpackage.bc6.invoke` | `ev_df11bf00b18dde878ab16a4ef4cd4c68b8166334e19fdb9ba6f80477120afcb5` |
| `agent-source-graph-destinations.json` | `defpackage.bg7.invoke` | `ev_804ced102b6c9242ee22c7317b14ab8ea00f898ce41d7b0dd294d694317eeea3` |
| `agent-source-graph-host-refs.json` | `defpackage.o84.c` | `ev_d1cfbf71c1b159473b0fbf23f2845d89eeacee643f7c7b40dea7e50860c0ca80` |
| `agent-source-nav-transitions.json` | `defpackage.eg7.invoke` | `ev_f40af170c334245f60476748f0e79f631a63d1c9c2dd5e666c6d83e379f83f22` |
| `agent-source-animation-tween.json` | `defpackage.ai8.J` | `ev_6bb003763096a22098f7c4e0f634d988427fe03ad993b6c551da66ee83742f30` |
| `agent-source-cookbook-create-body.json` | `defpackage.dz5.invokeSuspend` | `ev_27d0b811570a83f42a141a8331b929434ea4ad3b216c9075c01394c6a5e47119` |
| `agent-source-cookbook-delete-members-body.json` | `defpackage.hz5.invokeSuspend` | `ev_11179fa6170d5eda8589d882691623ee080c40bca0687de4459974b59f568e7e` |
| `agent-source-cookbook-filters.json` | `com.recime.screens.cookbook.RMCookbookScreenViewModel.applyFilters` | `ev_a75a7fceda32180e262e3228c152226197cfc7956d39ee13e13453c3cb93e405` |
| `agent-source-cookbook-filter-body.json` | `defpackage.az5.invokeSuspend` | `ev_ae1378050cc6e46a26f9c7ecbac110206347f6cdb5fd6642a436bf7e6fb11034` |
| `agent-source-cookbook-filter-refs.json` | `com.recime.screens.cookbook.RMCookbookScreenViewModel.applyIngredients` | `ev_3808c5371ddc14339a5fd9bb130906386c484226b7692a5b04f071555ebb7ed9` |
| `agent-source-search-searchedRecipes.json` | `com.recime.screens.search_recipes.RMSearchRecipesScreenViewModel.searchedRecipes` | `ev_f004983564dd09dd6803c1e5992e77ce3be0e041790d0dfcf5da806f5f90c34f` |
| `agent-source-search-body.json` | `defpackage.q47.invokeSuspend` | `ev_26dce81c61380168fd4dd3fe7e57d7f0da55bda39655bc35a0c8275fd0fc652c` |
| `agent-source-search-refs.json` | `com.recime.screens.search_recipes.RMSearchRecipesScreenViewModel.searchedRecipes` | `ev_f63d4863a80251872deee4d340731b092aa4f9f2f8585a49290c6e514192e47a` |
| `agent-source-filter-fields.json` | `com.recime.data.network.model.RMRecipeFilterState.getHasNonDefaultFilters` | `ev_a135ab8b87f6e55b4cab5ae883398baa52434ff6c5f527dbe9089bcc2c8cfb75` |
| `agent-source-filter-requires.json` | `com.recime.data.network.model.RMRecipeFilterState.getRequiresServerCall` | `ev_8655b491dcb59ef22b488c055e66b05c3f305dc0fe173fa8f147e9f09a09d692` |
| `import-text.json` | `com.recime.screens.in_app_import.RMInAppImportScreenViewModel.fetchRecipeFromText` | `ev_f544f3ca3728763b394128c01f6b013577d51d6d04136c90d616897c4f524630` |
| `import-image.json` | `com.recime.screens.in_app_import.RMInAppImportScreenViewModel.fetchRecipeFromImage` | `ev_d64f9799dc138b63d2ac5df6d89dd87e2f4234fce4fa4d9dbf5bf6f339e16b37` |
| `import-html.json` | `com.recime.screens.in_app_import.RMInAppImportScreenViewModel.fetchRecipeFromHtml` | `ev_81429729efe02c9511396a2e78cc23b46d37c7d5cccf24b04f2478ff30c9e604` |
| `share-text-url.json` | `com.recime.share_extension.RMShareExtensionRecipeImportViewModel.fetchRecipeFromTextOrUrl` | `ev_d8407e0e8604cf4377e8113b4af44fce2c56c1f15f07c07788963217bb2b37e1` |
| `share-fetch.json` | `com.recime.share_extension.RMShareExtensionRecipeImportViewModel.fetchRecipe` | `ev_34111f68423ab95fce29c0b0324f69134b9c8c8e471cdb68de790618e807637c` |
| `import-save-body.json` | `defpackage.cd6.invokeSuspend` | `ev_7d50ff3cd8c02c16706f3ae8949c9c4fdd774d241ff073caf49d109d3918fc72` |
| `editor-save-body.json` | `defpackage.ju6.invokeSuspend` | `ev_53d29d8b692b6b7edbffcf46a75838cdb13b79273bddf2a9e5a00ba832f9ca5c` |
| `agent-source-mealtype-class.json` | `com.recime.data.network.model.MealType` | `ev_8fa69e2c6848c20728d8a5219bd3999983b84ff0d8eec11f1d3843de1542d57f` |
| `agent-source-meal-drag.json` | `com.recime.screens.meal_plan.RMMealPlanViewModel.startDrag` | `ev_d0f941dfa3edac9f810f4897584bb9f17eb2778cb3f40537d8d9f0896a67d7c2` |
| `agent-source-meal-drop.json` | `com.recime.screens.meal_plan.RMMealPlanViewModel.completeDrop` | `ev_3b2efb29273d0a609af41b13c084ad88be7195ce328102e97a947265f9a081bb` |
| `agent-source-meal-moveMealItemToDay.json` | `com.recime.screens.meal_plan.RMMealPlanViewModel.moveMealItemToDay` | `ev_58d0656ec2eb48047073e2341dcdd85b98d3c4ceae5a142d3c9b4d43aec72e8e` |
| `agent-source-meal-getUserFirstDayOfWeek.json` | `com.recime.screens.meal_plan.RMMealPlanViewModel.getUserFirstDayOfWeek` | `ev_eb3d88a6a9490f7ac26978af5dea464d4160590e087fb5719b44500cc336ce97` |
| `settings-week.json` | `com.recime.screens.settings.RMSettingsViewModel.updateFirstDayOfWeek` | `ev_46c721e61e65d42bc226f2e1f63b170ed956814fcae24f04c3f26c16483575eb` |
| `agent-source-meal-prepareGroceryListFromMealPlan.json` | `com.recime.screens.meal_plan.RMMealPlanViewModel.prepareGroceryListFromMealPlan` | `ev_b2a048b29a7897459fd900cdad8f051f023c7094fc418982c944051bae03657e` |
| `agent-source-meal-add-grocery-body.json` | `defpackage.qi6.invokeSuspend` | `ev_be392e570fcaeddf553aacbf5cb8653549161c93100a4d9cb0d0b32bbe08079e` |
| `agent-source-meal-grocery-refs.json` | `com.recime.screens.meal_plan.RMMealPlanViewModel.addSelectedIngredientsToGrocery` | `ev_15756facd2a3cafd6af9f6d7092971ec0492efb53b0c78b45908f731fa53f612` |
| `agent-source-grocery-stageInput.json` | `com.recime.screens.groceries.RMGroceriesViewModel.stageInput` | `ev_94b5c49e889f664c267c728c46ee63d7f79bb5f3e17b7ffa319cae9333d0fcc6` |
| `agent-source-grocery-split.json` | `com.recime.screens.groceries.RMGroceriesViewModel.splitIngredients` | `ev_92794f098fffdde93497ef3f25230283e06c5fdd3ef97538736afed01365929c` |
| `agent-source-grocery-updateItemChecked.json` | `com.recime.screens.groceries.RMGroceriesViewModel.updateItemChecked` | `ev_46f847dd743ea9c72788ebb720273a318bc26c62c6dda7db85a906b6f0c82f13` |
| `agent-source-grocery-check-body.json` | `defpackage.c52.invokeSuspend` | `ev_e9acaea95e6df91bb6d0b1dea94de0eb32b4562f93f25cfbd9c3658875fb4174` |
| `agent-source-sort-enum.json` | `defpackage.dc8.<clinit>` | `ev_778b31fc366a80bc338312d65a9959f6152624cab3840ad0e55496a3dbda3824` |
| `agent-source-builder-sort.json` | `defpackage.ct2.b` | `ev_df72c40f7eb6234a20aa51f258a40b09ca1133a684943a019fdd2a931c97525f` |
| `agent-source-grocery-init.json` | `com.recime.screens.groceries.RMGroceriesViewModel.<init>` | `ev_9da05103db21e710cbda4b945a8e7681748b183a6b9a08909ea4faaab26046ac` |
| `agent-source-grocery-ref.json` | `com.recime.screens.groceries.RMGroceriesViewModel.stageInput` | `ev_8c08c390c489feba5f11928c8ac95a778cc567da915f78b25101d43921e9045e` |
| `cooking-refs.json` | `com.recime.screens.post.RMPostScreenViewModel.onCookWithReciMe` | `ev_5f58f33a33ee199d0504343256b6ac8cfa74fa54a32fc0918120ee9599143edc` |
| `agent-source-cooking-trigger.json` | `defpackage.pw6.invoke` | `ev_75c057192b9171c4389f778e327170e6b41dd94b981268e976c5aff498fd2390` |
| `post-cooking.json` | `com.recime.screens.post.RMPostScreenViewModel.onCookWithReciMe` | `ev_61b3e3162ed58303e387923f6b06176dc3107e607b34f55249a48b93c346d3e3` |
| `agent-source-cooking-route.json` | `com.recime.screens.post.RMPostScreenViewModel.onCookWithReciMe$lambda$0` | `ev_db19ad61f2afcf033e0f21fa779be09e068db22a6a9cfafdc81ced4532ca67c2` |
| `pip-refs.json` | `com.recime.RMPipActivity` | `ev_636f1f6c94aa5c7537f2ed3af0911919f2e6ef21499d0a6495fd7912795e36d3` |
| `agent-source-pip-trigger.json` | `defpackage.ya.invokeSuspend` | `ev_3f666c9928116c58b5fcb27919d689a8bbb640c442c1c9c6834d80cb76d389bd` |
| `pip-onCreate.json` | `com.recime.RMPipActivity.onCreate` | `ev_a0746b8c5bc8605e162aa32d7863cc1cfb77af12a00d5c06a8c22cb535274158` |
| `pip-params.json` | `com.recime.RMPipActivity.f` | `ev_aad7587e2faa966d1ef0080cd14f9ce4341de42f3e331501933d1eaea4b5b08e` |
| `user-email.json` | `com.recime.domain.repository.RMUserServiceImpl.signInWithEmail` | `ev_2bddc1078558d63ece400d18ea40c03cced52d77ed31616ec3ac9c65a274a019` |
| `user-sync.json` | `com.recime.domain.repository.RMUserServiceImpl.syncUser` | `ev_2d4a1fdb7362c4d71c92eaf36099603255b926314f2c352036f15f4d528f3a7e` |
| `agent-source-user-sync-body.json` | `defpackage.ic7.invokeSuspend` | `ev_e0cfbcd74740708346ddc0e677dcb48086a0d7b3470bd894404fd59841460a3e` |
| `post-nutrition.json` | `com.recime.screens.post.RMPostScreenViewModel.runNutritionCalculation` | `ev_2fe721b9fc7aa11122031d70f57f2e83f3e6c48ea0e9cf6d49ac7ce7ddc5e7ee` |
| `ask-send.json` | `com.recime.screens.post.RMAskReciMeViewModel.sendMessage` | `ev_a2b728a3cf9ef8a8d00e5094036e14fd5981d6f9a6e267a48359acac63c995e1` |
| `post-export.json` | `com.recime.screens.post.RMPostScreenViewModel.onExportPdf` | `ev_63358e9b4dddacb5fad4dd925c0cdcb557986a7879ddb9d640fe8d38b12c22ea` |
| `main-intent.json` | `com.recime.ReciMeActivity.j` | `ev_90d9d90fd15278706c4f4f1665b424fcc25659dc63b13d79311d315eebd485c9` |
| `onboard-init.json` | `com.recime.screens.onboarding.RMOnboardingViewModel.initialize` | `ev_80142e670df6e288759bb9d7ee1d1d39463312801e6b27e5d0c15b55ed39698c` |
| `agent-source-drag-refs.json` | `com.recime.screens.meal_plan.RMMealPlanViewModel.startDrag` | `ev_531324986dabc7b0bcfe4c3b7bbf0fb501f7f42a59e24dec4fa7a0914b2c2b27` |
| `agent-source-drag-handler.json` | `defpackage.n0.invoke` | `ev_177b27c7f8e77a5e24dd1a411a13b6ed64d56d5a26fda41ae3ede5f4e2effeaa` |

## Runtime bổ sung sau PR #5

Xem [UI map](REAL_RECIME_UI_UX_MAP.md): reuse screenshots/XML đã thu thập cho Home, cookbook create/empty/menu, Editor form, Meal Plan empty, Grocery empty và Discover. Lần tiếp tục23/10/2026 xác nhận official đang ở error Something went wrong/Try again/Cancel (`editor-current.png/xml`). Không có evidence mới về server hoặc save/detail success; không chạy lại APK inventory. Source/test nhóm Editor độc lập ghi tại [test results](TEST_RESULTS.md).
