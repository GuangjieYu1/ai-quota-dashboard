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
4. Improve DeepSeek login-cookie capture and browser-like API headers.
5. Move feelol `expires_at` from the top tier chip into an `Expires` progress bar under `Monthly`.

## Important Correction: Theme Means UI Preset, Not Palette

The previous implementation treated themes as color/layout tokens: background color, card radius, spacing, elevation, compactness. This is not enough.

The intended feature is a set of complete dashboard UI presets, similar to the provided references:

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

4. `Mobile Native`
   - Current mobile list layout.
   - Simple vertical service cards.
   - Best for small screens and low implementation risk.

## Required Architecture Change

Do not keep extending `DashboardThemePalette` as if themes are just colors.

Introduce a separate concept:

```kotlin
enum class DashboardLayoutPreset {
    MOBILE_NATIVE,
    CONSOLE_GRID,
    GLASS_LAB,
    DETAIL_LEDGER
}
```

Keep color palette as a sub-token of each preset, but route rendering by preset:

```kotlin
when (layoutPreset) {
    MOBILE_NATIVE -> MobileNativeDashboard(...)
    CONSOLE_GRID -> ConsoleGridDashboard(...)
    GLASS_LAB -> GlassLabDashboard(...)
    DETAIL_LEDGER -> DetailLedgerDashboard(...)
}
```

## Implementation Plan for UI Presets

### Phase UI-1: Data model and settings

- Add `DashboardLayoutPreset` enum.
- Store selected preset in `EncryptedPrefsManager` or existing settings storage.
- Settings screen should show presets with preview names, not just color dots.
- Keep current `DashboardThemeStyle` only as palette if needed, or fold it into preset tokens.

### Phase UI-2: Extract current screen

- Rename current dashboard rendering into `MobileNativeDashboard`.
- Preserve existing behavior and data flow.
- Keep it as the default preset to avoid breaking existing APK behavior.

### Phase UI-3: Add Console Grid

- Add `ConsoleGridDashboard.kt`.
- Header: avatar, `AI Quota`/`ClaudeBar` style title, `usage monitor` subtitle, sync badge.
- Provider pills row: enabled providers.
- Metric cards: flatten selected service windows into tile cards.
- Phone layout: vertical list; tablet/wide layout: 2-column grid.
- Use terminal dark tokens and neon accent.

### Phase UI-4: Add Glass Lab

- Add `GlassLabDashboard.kt`.
- Soft gradient background.
- Floating panel/card container.
- Provider tabs as rounded pills.
- Metric cards in 2-column grid where possible.
- Keep mobile fallback to 1-column.

### Phase UI-5: Add Detail Ledger

- Add `DetailLedgerDashboard.kt`.
- Single selected provider at a time.
- Detailed bars for all windows.
- Extra usage and last updated sections.
- Histogram section should be placeholder if logs are not available.

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
- DeepSeek headers and cookie capture relaxation
- DeepSeek cookie validation: reject incomplete cookie values without `name=value`
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
