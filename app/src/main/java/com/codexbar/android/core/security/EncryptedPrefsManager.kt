package com.codexbar.android.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.DashboardThemeStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedPrefsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val KEY_ENABLED_SERVICES = "enabled_services"
        const val KEY_DASHBOARD_THEME = "dashboard_theme"

        val DefaultEnabledServices = setOf(
            AiService.CHATGPT_PLUS,
            AiService.CODEX,
            AiService.DEEPSEEK,
            AiService.MIMO
        )
    }

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            "codexbar_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveCredential(service: AiService, credential: Credential) {
        val editor = prefs.edit()
        val prefix = service.name

        editor.putString("${prefix}_access_token", credential.accessToken)
        editor.putString("${prefix}_refresh_token", credential.refreshToken)

        when (credential) {
            is Credential.ClaudeCredential -> {
                credential.expiresAt?.let {
                    editor.putLong("${prefix}_expires_at", it.epochSecond)
                }
                credential.scopes?.let {
                    editor.putString("${prefix}_scopes", it)
                }
                credential.rateLimitTier?.let {
                    editor.putString("${prefix}_rate_limit_tier", it)
                }
            }
            is Credential.CodexCredential -> {
                if (!credential.accountId.isNullOrBlank()) {
                    editor.putString("${prefix}_account_id", credential.accountId)
                } else {
                    editor.remove("${prefix}_account_id")
                }
                if (!credential.manualResponse.isNullOrBlank()) {
                    editor.putString("${prefix}_manual_response", credential.manualResponse)
                } else {
                    editor.remove("${prefix}_manual_response")
                }
            }
            is Credential.GeminiCredential -> {
                editor.putLong("${prefix}_expires_at_ms", credential.expiresAtMs)
                editor.putString("${prefix}_oauth_client_id", credential.oauthClientId)
                editor.putString("${prefix}_oauth_client_secret", credential.oauthClientSecret)
            }
            is Credential.DeepSeekCredential -> {
                editor.putString("${prefix}_base_url", credential.baseUrl)
                editor.putString("${prefix}_session_cookie", credential.sessionCookie)
                if (credential.initialTotal > 0) {
                    editor.putString("${prefix}_initial_total", credential.initialTotal.toString())
                } else {
                    editor.remove("${prefix}_initial_total")
                }
            }
            is Credential.ChatGPTPlusCredential -> {
                editor.putString("${prefix}_plan_name", credential.planName)
                editor.putString("${prefix}_renewal_date", credential.renewalDate)
                editor.putString("${prefix}_billing_period", credential.billingPeriod)
                editor.putString("${prefix}_notes", credential.notes)
                credential.manualSessionResponse?.let {
                    editor.putString("${prefix}_manual_session_response", it)
                }
            }
            is Credential.MiMoCredential -> {
                editor.putString("${prefix}_backend_url", credential.backendUrl)
                editor.putString("${prefix}_backend_token", credential.backendToken)
                editor.putString("${prefix}_direct_cookie", credential.directCookie)
                editor.putBoolean("${prefix}_use_backend_mode", credential.useBackendMode)
            }
        }

        setProviderEnabled(service, true, editor)
        editor.apply() // atomic write via SharedPreferences commit semantics
    }

    fun loadCredential(service: AiService): Credential? {
        val prefix = service.name
        val accessToken = prefs.getString("${prefix}_access_token", "") ?: ""

        return when (service) {
            AiService.CLAUDE -> {
                if (accessToken.isBlank()) return null
                val refreshToken = prefs.getString("${prefix}_refresh_token", null)
                val expiresAt = prefs.getLong("${prefix}_expires_at", -1L)
                    .takeIf { it > 0 }
                    ?.let { Instant.ofEpochSecond(it) }
                val scopes = prefs.getString("${prefix}_scopes", null)
                val rateLimitTier = prefs.getString("${prefix}_rate_limit_tier", null)
                Credential.ClaudeCredential(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAt = expiresAt,
                    scopes = scopes,
                    rateLimitTier = rateLimitTier
                )
            }
            AiService.CODEX -> {
                val manualResponse = prefs.getString("${prefix}_manual_response", null)
                if (accessToken.isBlank() && manualResponse.isNullOrBlank()) return null
                val refreshToken = prefs.getString("${prefix}_refresh_token", null) ?: ""
                val accountId = prefs.getString("${prefix}_account_id", null)
                Credential.CodexCredential(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    accountId = accountId,
                    manualResponse = manualResponse
                )
            }
            AiService.GEMINI -> {
                if (accessToken.isBlank()) return null
                val refreshToken = prefs.getString("${prefix}_refresh_token", null) ?: return null
                val expiresAtMs = prefs.getLong("${prefix}_expires_at_ms", -1L)
                    .takeIf { it > 0 } ?: return null
                val clientId = prefs.getString("${prefix}_oauth_client_id", null) ?: return null
                val clientSecret = prefs.getString("${prefix}_oauth_client_secret", null) ?: return null
                Credential.GeminiCredential(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAtMs = expiresAtMs,
                    oauthClientId = clientId,
                    oauthClientSecret = clientSecret
                )
            }
            AiService.DEEPSEEK -> {
                val baseUrl = prefs.getString("${prefix}_base_url", "https://api.deepseek.com") ?: "https://api.deepseek.com"
                val initialTotalStr = prefs.getString("${prefix}_initial_total", null)
                val initialTotal = initialTotalStr?.toDoubleOrNull() ?: 0.0
                val sessionCookie = prefs.getString("${prefix}_session_cookie", "") ?: ""
                if (accessToken.isBlank() && sessionCookie.isBlank()) return null
                Credential.DeepSeekCredential(
                    accessToken = accessToken,
                    baseUrl = baseUrl,
                    initialTotal = initialTotal,
                    sessionCookie = sessionCookie
                )
            }
            AiService.CHATGPT_PLUS -> {
                val planName = prefs.getString("${prefix}_plan_name", "Plus") ?: "Plus"
                val renewalDate = prefs.getString("${prefix}_renewal_date", "") ?: ""
                val billingPeriod = prefs.getString("${prefix}_billing_period", "Monthly") ?: "Monthly"
                val notes = prefs.getString("${prefix}_notes", "") ?: ""
                val manualSessionResponse = prefs.getString("${prefix}_manual_session_response", null)
                if (accessToken.isBlank() && renewalDate.isBlank() && manualSessionResponse.isNullOrBlank()) return null
                Credential.ChatGPTPlusCredential(
                    accessToken = accessToken,
                    planName = planName,
                    renewalDate = renewalDate,
                    billingPeriod = billingPeriod,
                    notes = notes,
                    manualSessionResponse = manualSessionResponse
                )
            }
            AiService.MIMO -> {
                val backendUrl = prefs.getString("${prefix}_backend_url", "") ?: ""
                val backendToken = prefs.getString("${prefix}_backend_token", "") ?: ""
                val directCookie = prefs.getString("${prefix}_direct_cookie", "") ?: ""
                val useBackendMode = prefs.getBoolean("${prefix}_use_backend_mode", true)
                if (backendUrl.isBlank() && directCookie.isBlank()) return null
                Credential.MiMoCredential(
                    accessToken = accessToken,
                    backendUrl = backendUrl,
                    backendToken = backendToken,
                    directCookie = directCookie,
                    useBackendMode = useBackendMode
                )
            }
        }
    }

    fun deleteCredential(service: AiService) {
        val prefix = "${service.name}_"
        val editor = prefs.edit()

        val keys = prefs.all.keys.filter { it.startsWith(prefix) }
        keys.forEach { editor.remove(it) }

        editor.apply()
    }

    fun deleteAllCredentials() {
        val editor = prefs.edit()
        AiService.entries.forEach { service ->
            val prefix = "${service.name}_"
            prefs.all.keys
                .filter { it.startsWith(prefix) }
                .forEach { editor.remove(it) }
        }
        editor.apply()
    }

    fun hasCredential(service: AiService): Boolean {
        val prefix = service.name
        return when (service) {
            AiService.CODEX -> {
                !prefs.getString("${prefix}_access_token", "").isNullOrBlank() ||
                    !prefs.getString("${prefix}_manual_response", "").isNullOrBlank()
            }
            AiService.CHATGPT_PLUS -> {
                !prefs.getString("${prefix}_renewal_date", "").isNullOrBlank() ||
                    !prefs.getString("${prefix}_manual_session_response", "").isNullOrBlank()
            }
            AiService.MIMO -> {
                !prefs.getString("${prefix}_backend_url", "").isNullOrBlank() ||
                    !prefs.getString("${prefix}_direct_cookie", "").isNullOrBlank()
            }
            AiService.DEEPSEEK -> {
                !prefs.getString("${prefix}_access_token", "").isNullOrBlank() ||
                    !prefs.getString("${prefix}_session_cookie", "").isNullOrBlank()
            }
            else -> !prefs.getString("${prefix}_access_token", "").isNullOrBlank()
        }
    }

    fun getEnabledServices(): Set<AiService> {
        if (!prefs.contains(KEY_ENABLED_SERVICES)) {
            return AiService.entries
                .filter { service -> DefaultEnabledServices.contains(service) || hasCredential(service) }
                .toSet()
        }
        return prefs.getStringSet(KEY_ENABLED_SERVICES, emptySet())
            .orEmpty()
            .mapNotNull { name -> AiService.entries.find { it.name == name } }
            .toSet()
    }

    fun isProviderEnabled(service: AiService): Boolean {
        return getEnabledServices().contains(service)
    }

    fun setProviderEnabled(service: AiService, enabled: Boolean) {
        val editor = prefs.edit()
        setProviderEnabled(service, enabled, editor)
        editor.apply()
    }

    private fun setProviderEnabled(
        service: AiService,
        enabled: Boolean,
        editor: SharedPreferences.Editor
    ) {
        val names = getEnabledServices().map { it.name }.toMutableSet()
        if (enabled) {
            names.add(service.name)
        } else {
            names.remove(service.name)
        }
        editor.putStringSet(KEY_ENABLED_SERVICES, names)
    }

    fun getDashboardTheme(): DashboardThemeStyle {
        val name = prefs.getString(KEY_DASHBOARD_THEME, DashboardThemeStyle.SYSTEM.name)
            ?: DashboardThemeStyle.SYSTEM.name
        return DashboardThemeStyle.entries.find { it.name == name } ?: DashboardThemeStyle.SYSTEM
    }

    fun setDashboardTheme(style: DashboardThemeStyle) {
        prefs.edit().putString(KEY_DASHBOARD_THEME, style.name).apply()
    }

    fun getRefreshInterval(): Long {
        return prefs.getLong("refresh_interval_minutes", 30L)
    }

    fun setRefreshInterval(minutes: Long) {
        prefs.edit().putLong("refresh_interval_minutes", minutes).apply()
    }

    fun isNotificationsEnabled(): Boolean {
        return prefs.getBoolean("notifications_enabled", true)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
    }

    fun saveResetTimes(service: AiService, windows: List<Pair<String, Instant?>>) {
        val editor = prefs.edit()
        windows.forEach { (label, resetsAt) ->
            val key = "${service.name}_${label}_resets_at"
            if (resetsAt != null) {
                editor.putLong(key, resetsAt.epochSecond)
            } else {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    fun loadResetTimes(service: AiService): Map<String, Instant> {
        val prefix = "${service.name}_"
        val suffix = "_resets_at"
        return prefs.all
            .filter { it.key.startsWith(prefix) && it.key.endsWith(suffix) }
            .mapNotNull { (key, value) ->
                val label = key.removePrefix(prefix).removeSuffix(suffix)
                val epochSecond = (value as? Long)?.takeIf { it > 0 } ?: return@mapNotNull null
                label to Instant.ofEpochSecond(epochSecond)
            }
            .toMap()
    }
}
