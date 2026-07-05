# Codex Handoff Plan

Branch: `codex/fix-quota-integrations`

Compare / patch URL:

```text
https://github.com/GuangjieYu1/ai-quota-dashboard/compare/main...codex/fix-quota-integrations
```

## Goals

1. Fix ChatGPT Plus validation when a valid `/api/auth/session` response is pasted but no renewal date is configured.
2. Add `Codex (feelol)` as a separate provider backed by `https://feea.lol/api/v1/subscriptions?timezone=Asia%2FShanghai`.
3. Parse feelol daily, weekly, and monthly usage/limit values, expiry, and reset windows.
4. Improve DeepSeek login-cookie capture and browser-like API headers.
5. Make dashboard themes affect layout density/shape/spacing, not only colors.

## Implemented

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
- dashboard card layout theme tokens

## Local Verification Command

Run this on the machine that can access GitHub and Android Gradle:

```bash
cd /home/yugj/code/ai-quota-dashboard
git fetch origin
git checkout codex/fix-quota-integrations
./gradlew assembleDebug
```

If compile fails, check first for remaining exhaustive `when (AiService)` sites and Compose import errors.
