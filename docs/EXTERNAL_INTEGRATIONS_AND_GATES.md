# External integrations and verification gates — 22 October 2026

**This is an independently written reCime app, NOT the official ReciMe distribution.**
The source includes working local features; third-party integrations may require user credentials, platform approval and region-specific licensing. Never mark unavailable integrations "implemented".

## Implemented in feature branch

- SQLite schema v4 added **without deleting** v1-v3 recipes/meals/groceries data.
- Real cookbook table + recipe memberships; create, rename, delete, multiple cookbook membership.
- Four meal types, multiple recipes in one slot, edit/delete/clear week, adding meals from detail.
- Planned grocery derived automatically from saved meals; item checks persist across regeneration; separate manual cart is preserved.
- Ingredient name filter; Android Cooking Mode with wake lock scoped to visible view; PDF export and native print flow.
- Optional **real** recipe Q&A through backend's `POST /assistant/ask` and OpenAI Responses API. If OPENAI_API_KEY is absent, API returns HTTP 503 rather than a fake result.

### Local AI credentials (on YOUR computer only)

In PowerShell from project root:

```powershell
Copy-Item backend/.env.example backend/.env
notepad backend/.env
.\backend\run.ps1
```

Set `OPENAI_API_KEY=...` in `backend/.env` and `OPENAI_MODEL=gpt-4.1-mini` only if your OpenAI API account can use that model. This key has separate API billing; ChatGPT Plus is not an API credit. The `.env` file is gitignored. No key in Android APK, Gradle, Git commits or chat. **Do not run this backend on a public network without authentication/rate limiting.** Use the existing USB `adb reverse tcp:8765 tcp:8765` workflow; verify `/health` returns `assistantAvailable: true`.

## Remaining external blockers / evidence needed

1. **Official Android binary and runtime**: install `com.recime.app` from Google Play on a phone/emulator you control. REA/JADX/ADB can compare behavior, layout states and APK static structure, but do not assume ReciMe's proprietary API/algorithms/keys. Need version, test account, visual recordings and valid hashes. No APK from third-party mirrors.
2. **Instagram/Facebook imports**: Meta developer app, approved scopes and supported user-owned/business content access where required, app review, OAuth redirect URIs, documented permission model. Public posts on arbitrary accounts cannot always be accessed using Graph API. Do not rely on scraping paywalled/authenticated content.
3. **TikTok / YouTube / Pinterest imports**: official developer app credentials/OAuth and platform-permitted media/caption access for the intended account/content; the current TikTok oEmbed is only metadata, NOT audio/video extraction. Never bypass DRM or private account restrictions.
4. **Nutrition estimates**: endpoint `/nutrition/estimate` now uses live USDA search when configured, accepts g/kg amounts and returns source IDs; user review is mandatory. It returns incomplete (no totals) for unmatched ingredients. Obtain `FDC_API_KEY` from official USDA FoodData Central service (requires API key) and approve nutrition dataset mapping, language translation and confidence thresholds. Need ingredient measurement and yield data; incomplete ingredients must remain "unknown". Nothing should silently invent nutrition values; other units and exact food mappings still need broader verification.
5. **Multi-device account/sync**: Choose identity/database provider and provide new test project information (e.g. Supabase URL + anon key + RLS policies, or Firebase app config) plus a secure deployment target. No production service-role/private credentials embedded on phone. Need conflict resolution and offline queue tested with 2 Android devices.
6. **Grocery checkout**: region-specific approved merchant/affiliate provider API, delivery availability, pricing/tax/privacy and checkout contract. Cannot fabricate live orders or claim ReciMe partner inventory access.
7. **Cooking AI and translation**: optional remote-model account/billing or authorized on-device model and validated language coverage. No key will be embedded in client.

### Minimal acceptance tests for 1:1 FUNCTIONAL parity

- For each feature capture official ReciMe version, locale, navigation screenshots/video and expected normal/error/offline paths.
- Implement same journey in independent app with owned source code and licensed assets, and test multiple recipes/day, cookbook memberships, shopping reconciliation, import retry, nutrition confidence, AI failure, cloud conflicts.
- Run `assembleDebug testDebugUnitTest lintDebug` AND device `connectedDebugAndroidTest`; Android instrumentation and UI recording are not replaced by CI unit tests.
- Compare the functional parity matrix in `RECIME_OFFICIAL_PARITY_GAP_AUDIT.md` and mark feature PASS only with evidence. Do not claim exact APK/branding/signing equality.

**Security:** Never send credentials in chat, screenshots or GitHub issues. Put backend keys in `backend/.env`; app auth config in local secure machine/configuration or platform secret store.
