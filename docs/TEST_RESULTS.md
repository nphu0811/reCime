# Kết quả kiểm thử — nhóm Editor lifecycle

Ngày23/10/2026, nhánh `wip/rea-recime-602`, baseline15497c9, PR#5. Không đổi SQLite version4 hoặc migration.

- Baseline CI15497c9 và CookbookMembershipTest2/2 PASS theo tiến độ được cung cấp; phiên này không phân tích lại APK.
- Build assembleDebug, assembleDebugAndroidTest, testDebugUnitTest và lintDebug: PASS trước vòng instrumentation cuối. JVM16tests; lint0errors/154warnings có sẵn.
- Instrumentation vòng đầu:12/15 PASS,3FAIL do dock item bị clip. Sửa dock wrap_content, root consume system-bar insets.
- Vòng hai:14/15 PASS; EditorLifecycleTest3/3, cookbook/DB/migration/planner PASS. Còn fail test tìm recipe vì title card bị clip khi hero chiếm không gian. Đã ẩn hero trong search results, giữ strict Espresso click.
- Vòng cuối: **15/15 PASS**,0failures/0errors, Emulator Android17. Gradle toàn lệnh BUILD SUCCESSFUL2m28s; XML report xác nhận. EditorLifecycleTest3/3, CookbookMembershipTest2/2, LabV4FeaturesTest2/2 và các test DB/migration/Planner/LabUI liên quan đều qua. Build/unit/lint trong cùng lệnh PASS.

Lệnh vòng cuối:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.recime.EditorLifecycleTest,com.example.recime.CookbookMembershipTest,com.example.recime.LabV4FeaturesTest,com.example.recime.LabDatabaseTest,com.example.recime.sql.LegacyRoomV2MigrationTest,com.example.recime.PlannerUiTest,com.example.recime.LabUiTest'
```

Logs cục bộ gitignored: `.tools/editor-validation.log`, `.tools/editor-device-tests.log`, `.tools/editor-final-tests.log`. Reports trong app/build; không commit APK/screenshots tài khoản/decompiled source. Test fixtures được cleanup; DB migrations chạy trên database riêng.

Phạm vi Editor: draft giữ qua library refresh/recreation; validation không mutate observed recipe; retry sau thiếu nutrition source; metadata favorite/catalog attribution giữ; pending save qua recreation không double insert. Không xác minh kill-process trong lúc commit, disk-full, full photo flow hoặc mọi thiết bị/font scale. Live backend/provider và toàn bộ import/OCR suite không nằm trong nhóm này.
