# Codex Handoff Plan

Branch: `codex/fix-quota-integrations`

Compare URL:

```text
https://github.com/GuangjieYu1/ai-quota-dashboard/compare/main...codex/fix-quota-integrations
```

Patch URL:

```text
https://github.com/GuangjieYu1/ai-quota-dashboard/compare/main...codex/fix-quota-integrations.patch
```

## Current Functional Goals

1. Fix ChatGPT Plus validation when a valid `/api/auth/session` response is pasted but no renewal date is configured.
2. Add `Codex (feelol)` as a separate provider backed by `https://feea.lol/api/v1/subscriptions?timezone=Asia%2FShanghai`.
3. Parse feelol daily, weekly, and monthly usage/limit values, expiry, and reset windows.
4. DeepSeek should use the official API-key balance path only. Do not rely on `platform.deepseek.com` browser cookies for quota because that private web endpoint rejects the request in the Android app.
5. Move feelol `expires_at` from the top tier chip into an `Expires` progress bar under `Monthly`.

## DeepSeek Decision

The browser-login / platform-cookie path is deprecated for this APK. It kept producing `DeepSeek summary rejected the request` because the app was not reliably capturing a complete authenticated browser session and the private platform summary endpoint is not a stable public API.

Use the official API-key flow instead:

- Settings should ask for `DeepSeek API Key`.
- Repository calls `GET https://api.deepseek.com/user/balance` with `Authorization: Bearer <api key>`.
- Ignore and clear old `sessionCookie` values.
- A cookie-only DeepSeek credential must not count as configured.

DeepSeek official docs describe using `https://api.deepseek.com` as the OpenAI-compatible base URL and `Authorization: Bearer ${DEEPSEEK_API_KEY}` for API calls. The official balance endpoint only returns availability plus `total_balance`, `granted_balance`, and `topped_up_balance`; it does not expose monthly cost, historical spend, or web-account summary fields.

Supported DeepSeek amount strategy:

1. Official mode: show current balance from `/user/balance`, including total / granted / topped-up balances.
2. Derived mode: if the user manually enters `initialTotal`, compute `used = initialTotal - total_balance`.
3. Local-log mode: if the app later records DeepSeek API calls locally, estimate daily / weekly / monthly spend from local logs and published model pricing.
4. Do not depend on `platform.deepseek.com/api/v0/users/get_user_summary` in the APK. It is a private web endpoint and has already rejected Android app requests.

## Important Correction: Theme Means UI Preset, Not Palette

The previous implementation treated themes as color/layout tokens: background color, card radius, spacing, elevation, compactness. This is not enough.

The intended feature is a set of complete dashboard UI presets. The user specifically wants these three families:

1. `LEGO_BRICK`
   - Toy-like modular dashboard.
   - Bright plastic block panels.
   - Thick outlines, chunky rounded rectangles, studs/dots as decorative anchors.
   - Provider cards look like connected bricks.
   - Quota bars look like stacked brick segments.
   - Friendly, playful, very readable on phone.

2. `CYBERPUNK_GRID`
   - Replace the previous anime/HUD idea.
   - Inspired by the provided black-orange grid reference image.
   - Dark engineering/cyberpunk screen, black background with amber/orange grid lines.
   - Large circular donut gauges for remaining quota.
   - Relay / distribution / circuit labels as decorative information architecture.
   - Segmented tabs such as S1 / S2 / S3 at the top.
   - Dashed data-flow lines connecting left-side nodes to right-side gauges.
   - Use amber, orange, cyan, and muted gray accents.
   - Avoid copying any specific copyrighted UI; use the reference as style direction only.

3. `MONITOR_PANEL`
   - The style represented by the earlier ClaudeBar / Glass Lab / Detail Ledger reference screenshots.
   - Includes dark terminal grid, glass lab, and detailed ledger variants.
   - This should feel like a usage monitor / aircraft cockpit / ClaudeBar-style control panel, not normal Material cards.

Keep `MOBILE_NATIVE` as the safe default fallback.

## Required Architecture Change

Do not keep extending `DashboardThemePalette` as if themes are just colors.

Introduce a separate concept:

```kotlin
enum class DashboardLayoutPreset {
    MOBILE_NATIVE,
    LEGO_BRICK,
    CYBERPUNK_GRID,
    MONITOR_PANEL
}
```

Route dashboard rendering by preset:

```kotlin
when (layoutPreset) {
    MOBILE_NATIVE -> MobileNativeDashboard(...)
    LEGO_BRICK -> LegoBrickDashboard(...)
    CYBERPUNK_GRID -> CyberpunkGridDashboard(...)
    MONITOR_PANEL -> MonitorPanelDashboard(...)
}
```

`DashboardThemePalette` can remain as internal tokens, but it must no longer be the main theme abstraction.

## Monitor Panel Submodes

`MONITOR_PANEL` may internally support three layouts:

1. `Console Grid`
   - Dark terminal style.
   - Header: app avatar, title, subtitle, syncing badge.
   - Top segmented provider tabs.
   - 2-column metric tiles on wide screens, 1-column on phones.
   - Each metric tile has a large remaining percentage, status chip, progress bar, reset caption.
   - Bottom dock style controls.

2. `Glass Lab`
   - Bright glassmorphism style.
   - Soft gradient background.
   - Floating centered panel.
   - Rounded white cards.
   - Provider tabs as pills.
   - Includes usage tiles, cost usage, token usage, working time cards.

3. `Detail Ledger`
   - Single-provider detailed card style.
   - Dark analytics panel.
   - Header contains provider, account/email, plan.
   - Large horizontal quota bars for session/weekly/monthly.
   - Secondary stats section: cost, tokens, latest tokens, reset credits.
   - Mini histogram/log-derived section can be placeholder until data exists.

## Implementation Plan for UI Presets

### Phase UI-1: Data model and settings

- Add `DashboardLayoutPreset` enum.
- Store selected preset in `EncryptedPrefsManager` or existing settings storage.
- Settings screen should show preset preview cards, not just color dots.
- Preset choices shown to user: `Mobile Native`, `Lego Brick`, `Cyberpunk Grid`, `Monitor Panel`.

### Phase UI-2: Extract current screen

- Rename current dashboard rendering into `MobileNativeDashboard`.
- Preserve existing behavior and data flow.
- Keep it as the default preset to avoid breaking existing APK behavior.

### Phase UI-3: Add Lego Brick

- Add `LegoBrickDashboard.kt`.
- Use chunky cards, block spacing, and segmented brick quota bars.
- Provider icon/avatar should sit in circular or square plastic-brick badge.
- Avoid brand/IP copying; keep it inspired by construction toys, not literal LEGO logos.

### Phase UI-4: Add Cyberpunk Grid

- Add `CyberpunkGridDashboard.kt`.
- Use a dark grid background, amber/orange circuit lines, relay labels, S1/S2/S3 tabs, and large donut gauges.
- Render each quota window as a circular gauge card, with left-side node labels and dashed connector lines.
- Phone layout should stack gauges vertically like the reference image.
- Tablet/wide layout can split into a left data-flow column and right gauge column.

### Phase UI-5: Add Monitor Panel

- Add `MonitorPanelDashboard.kt`.
- Start with dark console grid as MVP.
- Later add glass and ledger submodes if time permits.

## Already Implemented in This Branch

- `AiService.CODEX_FEELOL`
- `Credential.CodexFeelolCredential`
- feelol Retrofit DTO/API/repository
- DI wiring for network and repository modules
- encrypted prefs save/load/hasCredential/default enablement
- settings UI fields for feelol token/JSON
- login flow for feelol URL/API/token extraction
- dashboard and worker repository wiring
- token worker skips feelol refresh
- ChatGPT Plus session JSON validate fallback
- DeepSeek switched to API-key-only credential path
- old DeepSeek platform cookie no longer counts as a valid credential
- feelol expiry moved into an `Expires` usage window
- preliminary theme tokens, but this is not the final theme architecture

## Local Verification Command

Run this on the machine that can access GitHub and Android Gradle:

```bash
cd /home/yugj/code/ai-quota-dashboard
git fetch origin
git checkout codex/fix-quota-integrations
./gradlew assembleDebug
```

If compile fails, check first for remaining exhaustive `when (AiService)` sites, Compose imports, nullable DTO fields, and Hilt qualifier wiring.
