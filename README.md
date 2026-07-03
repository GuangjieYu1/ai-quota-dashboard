# AI Quota Dashboard

Android app for monitoring AI service plans and quotas. Forked from [hyunnnchoi/CodexBar-android](https://github.com/hyunnnchoi/CodexBar-android).

## Supported Providers

| Provider | Type | Auth |
|----------|------|------|
| **ChatGPT Plus** | Manual plan (renewal date) | None (date only) |
| **Codex** | API quota (5h + 7d windows) | ChatGPT session token |
| **DeepSeek** | API balance | API Key (sk-...) |
| **MiMo Token Plan** | Token usage | Backend URL or Direct Cookie |
| Claude | API quota (legacy) | Access token |
| Gemini | API quota (legacy) | OAuth token |

## Development Preview

This project supports a **Mock Mode** (`AppConfig.USE_MOCK_DATA = true`) that renders four provider cards with fake data—no API keys or credentials needed.

### 1. Android Studio Preview (no build required)

1. Open `MockDashboardScreen.kt`
2. At the bottom, find `DashboardPhonePreview` and `DashboardTabletPreview`
3. Click the split/design view icon in the top-right of the editor
4. Or right-click the Preview annotation → "Show Preview"

You should see:
- **Phone view (390×844)**: Single column of 4 provider cards
- **Tablet view (800×1280)**: Adaptive grid (2 columns)

### 2. Run on Emulator

**Android Studio:**
1. Device Manager → Create Virtual Device → Pixel 8 or Tablet
2. Select a system image (API 26+)
3. Run app

**Command line:**
```bash
export ANDROID_HOME=/home/user/Android
emulator -avd <avd_name>
./gradlew installDebug
```

### 3. Build Debug APK
```bash
export ANDROID_HOME=/home/user/Android
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

### Switching Mock / Live Mode

Edit `app/src/main/java/com/codexbar/android/core/config/AppConfig.kt`:
```kotlin
object AppConfig {
    const val USE_MOCK_DATA = true   // ← toggle this
}
```

| Value | Behavior |
|-------|----------|
| `true` | Shows 4 mock cards, no credentials needed, no network calls |
| `false` | Normal Hilt-based flow, requires configured credentials |

## Architecture

```
core/
├── domain/model/        # Data models (QuotaInfo, ProviderQuota, etc.)
├── domain/repository/   # QuotaRepository + QuotaProvider interfaces
├── data/               # Repository implementations per provider
├── network/            # Retrofit API services + DTOs per provider
└── security/           # EncryptedSharedPreferences
feature/
├── dashboard/          # Main quota cards UI
└── settings/           # Per-provider configuration
di/                     # Hilt DI modules
```

## Configuration (Live Mode)

1. Open Settings from the top bar
2. Configure each provider:
   - **ChatGPT Plus**: Enter plan name + renewal date
   - **Codex**: Enter access token + refresh token from ChatGPT session
   - **DeepSeek**: Enter API Key (sk-...)
   - **MiMo**: Choose Backend URL mode (recommended) or Direct Cookie

## Security

- All credentials stored in EncryptedSharedPreferences (AES-256-GCM)
- HTTP logging set to BASIC level only (no headers/body in release builds)
- MiMo Direct Cookie mode marked as advanced/unsafe
- No credentials logged to logcat

## License

MIT
