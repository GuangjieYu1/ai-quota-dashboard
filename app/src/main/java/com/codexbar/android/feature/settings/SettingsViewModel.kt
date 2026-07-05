package com.codexbar.android.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.DashboardThemeStyle
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.codex.CodexDto
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.network.codex.CodexUsageResponseValidator
import com.codexbar.android.core.network.codex.JsonSessionResponse
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import com.codexbar.android.di.ChatGPTPlusRepository
import com.codexbar.android.di.ClaudeRepository
import com.codexbar.android.di.CodexRepository
import com.codexbar.android.di.DeepSeekRepository
import com.codexbar.android.di.GeminiRepository
import com.codexbar.android.di.MiMoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ClaudeRepository private val claudeRepository: QuotaRepository,
    @CodexRepository private val codexRepository: QuotaRepository,
    @GeminiRepository private val geminiRepository: QuotaRepository,
    @DeepSeekRepository private val deepSeekRepository: QuotaRepository,
    @ChatGPTPlusRepository private val chatGptPlusRepository: QuotaRepository,
    @MiMoRepository private val miMoRepository: QuotaRepository,
    private val prefsManager: EncryptedPrefsManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val pendingChanges = MutableStateFlow<Pair<AiService, String>?>(null)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    init {
        loadSavedCredentials()
        _uiState.update {
            it.copy(
                enabledServices = prefsManager.getEnabledServices(),
                dashboardTheme = prefsManager.getDashboardTheme(),
                refreshIntervalMinutes = prefsManager.getRefreshInterval(),
                notificationsEnabled = prefsManager.isNotificationsEnabled()
            )
        }
        observePendingChanges()
    }

    private fun loadSavedCredentials() {
        for (service in AiService.entries) {
            val credential = prefsManager.loadCredential(service) ?: continue
            val state = when (credential) {
                is Credential.ClaudeCredential -> ServiceCredentialState(
                    accessToken = credential.accessToken,
                    refreshToken = credential.refreshToken ?: ""
                )
                is Credential.CodexCredential -> ServiceCredentialState(
                    accessToken = credential.accessToken,
                    refreshToken = credential.refreshToken,
                    accountId = credential.accountId ?: "",
                    manualResponse = credential.manualResponse ?: ""
                )
                is Credential.GeminiCredential -> ServiceCredentialState(
                    accessToken = credential.accessToken,
                    refreshToken = credential.refreshToken,
                    oauthClientId = credential.oauthClientId,
                    oauthClientSecret = credential.oauthClientSecret,
                    expiresAtDisplay = formatExpiryMs(credential.expiresAtMs)
                )
                is Credential.DeepSeekCredential -> ServiceCredentialState(
                    accessToken = credential.accessToken,
                    baseUrl = credential.baseUrl,
                    initialTotal = if (credential.initialTotal > 0) credential.initialTotal.toString() else "",
                    sessionCookie = credential.sessionCookie
                )
                is Credential.ChatGPTPlusCredential -> ServiceCredentialState(
                    planName = credential.planName,
                    renewalDate = credential.renewalDate,
                    billingPeriod = credential.billingPeriod,
                    notes = credential.notes,
                    manualSessionResponse = credential.manualSessionResponse ?: ""
                )
                is Credential.MiMoCredential -> ServiceCredentialState(
                    backendUrl = credential.backendUrl,
                    backendToken = credential.backendToken,
                    directCookie = credential.directCookie,
                    useBackendMode = credential.useBackendMode
                )
            }
            _uiState.update {
                it.copy(serviceStates = it.serviceStates + (service to state))
            }
        }
    }

    fun updateField(service: AiService, field: String, value: String) {
        _uiState.update { state ->
            val current = state.serviceStates[service] ?: ServiceCredentialState()
            val updated = when (field) {
                "accessToken" -> current.copy(accessToken = value, validationResult = null)
                "refreshToken" -> current.copy(refreshToken = value, validationResult = null)
                "accountId" -> current.copy(accountId = value, validationResult = null)
                "oauthClientId" -> current.copy(oauthClientId = value, validationResult = null)
                "oauthClientSecret" -> current.copy(oauthClientSecret = value, validationResult = null)
                "baseUrl" -> current.copy(baseUrl = value, validationResult = null)
                "initialTotal" -> current.copy(initialTotal = value, validationResult = null)
                "sessionCookie" -> current.copy(sessionCookie = value, validationResult = null)
                "planName" -> current.copy(planName = value, validationResult = null)
                "renewalDate" -> current.copy(renewalDate = value, validationResult = null)
                "billingPeriod" -> current.copy(billingPeriod = value, validationResult = null)
                "notes" -> current.copy(notes = value, validationResult = null)
                "backendUrl" -> current.copy(backendUrl = value, validationResult = null)
                "backendToken" -> current.copy(backendToken = value, validationResult = null)
                "directCookie" -> current.copy(directCookie = value, validationResult = null)
                "manualResponse" -> current.copy(manualResponse = value, validationResult = null)
                "manualSessionResponse" -> current.copy(manualSessionResponse = value, validationResult = null)
                else -> current
            }
            state.copy(serviceStates = state.serviceStates + (service to updated))
        }
        scheduleSave(service)
    }

    fun updateUseBackendMode(service: AiService, useBackend: Boolean) {
        _uiState.update { state ->
            val current = state.serviceStates[service] ?: ServiceCredentialState()
            state.copy(serviceStates = state.serviceStates + (service to current.copy(useBackendMode = useBackend)))
        }
        scheduleSave(service)
    }

    fun setProviderEnabled(service: AiService, enabled: Boolean) {
        prefsManager.setProviderEnabled(service, enabled)
        _uiState.update { state ->
            val enabledServices = if (enabled) {
                state.enabledServices + service
            } else {
                state.enabledServices - service
            }
            state.copy(enabledServices = enabledServices)
        }
    }

    fun setDashboardTheme(style: DashboardThemeStyle) {
        prefsManager.setDashboardTheme(style)
        _uiState.update { it.copy(dashboardTheme = style) }
    }

    @OptIn(FlowPreview::class)
    private fun observePendingChanges() {
        viewModelScope.launch {
            pendingChanges
                .debounce(500)
                .collect { pair ->
                    pair?.let { (service, _) -> saveCredential(service) }
                }
        }
    }

    private fun scheduleSave(service: AiService) {
        pendingChanges.value = service to System.currentTimeMillis().toString()
    }

    private fun saveCredential(service: AiService) {
        val state = _uiState.value.serviceStates[service] ?: return

        val credential = when (service) {
            AiService.CLAUDE -> {
                if (state.accessToken.isBlank()) return
                Credential.ClaudeCredential(
                    accessToken = state.accessToken,
                    refreshToken = state.refreshToken.ifBlank { null }
                )
            }
            AiService.CODEX -> {
                val pastedResponse = state.manualResponse.trim()
                val sessionAccessToken = parseCodexSessionAccessToken(pastedResponse)
                val manualResponse = pastedResponse
                    .takeIf { it.isNotBlank() && isValidCodexUsageResponse(it) }
                val accessToken = sessionAccessToken ?: state.accessToken
                if (accessToken.isBlank() && state.refreshToken.isBlank() && manualResponse == null) return
                Credential.CodexCredential(
                    accessToken = accessToken,
                    refreshToken = state.refreshToken,
                    accountId = state.accountId.ifBlank { null },
                    manualResponse = if (sessionAccessToken != null) null else manualResponse
                )
            }
            AiService.GEMINI -> {
                if (state.accessToken.isBlank() || state.refreshToken.isBlank() ||
                    state.oauthClientId.isBlank() || state.oauthClientSecret.isBlank()) return
                Credential.GeminiCredential(
                    accessToken = state.accessToken,
                    refreshToken = state.refreshToken,
                    expiresAtMs = System.currentTimeMillis() + 3600_000,
                    oauthClientId = state.oauthClientId,
                    oauthClientSecret = state.oauthClientSecret
                )
            }
            AiService.DEEPSEEK -> {
                if (state.accessToken.isBlank() && state.sessionCookie.isBlank()) return
                Credential.DeepSeekCredential(
                    accessToken = state.accessToken,
                    baseUrl = state.baseUrl.ifBlank { "https://api.deepseek.com" },
                    initialTotal = state.initialTotal.toDoubleOrNull() ?: 0.0,
                    sessionCookie = state.sessionCookie
                )
            }
            AiService.CHATGPT_PLUS -> {
                if (state.renewalDate.isBlank() && state.manualSessionResponse.isBlank()) return
                Credential.ChatGPTPlusCredential(
                    planName = state.planName.ifBlank { "Plus" },
                    renewalDate = state.renewalDate,
                    billingPeriod = state.billingPeriod.ifBlank { "Monthly" },
                    notes = state.notes,
                    manualSessionResponse = state.manualSessionResponse.ifBlank { null }
                )
            }
            AiService.MIMO -> {
                if (state.useBackendMode && state.backendUrl.isBlank()) return
                if (!state.useBackendMode && state.directCookie.isBlank()) return
                Credential.MiMoCredential(
                    backendUrl = state.backendUrl,
                    backendToken = state.backendToken,
                    directCookie = state.directCookie,
                    useBackendMode = state.useBackendMode
                )
            }
        }

        prefsManager.saveCredential(service, credential)
    }

    private fun parseCodexSessionAccessToken(response: String): String? {
        if (response.isBlank()) return null
        return runCatching {
            json.decodeFromString<JsonSessionResponse>(response).accessToken?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun isValidCodexUsageResponse(response: String): Boolean {
        return runCatching {
            val usageResponse = json.decodeFromString<CodexDto.UsageResponse>(response)
            CodexUsageResponseValidator.hasUsageData(usageResponse)
        }.getOrDefault(false)
    }

    fun validateCredential(service: AiService) {
        val repo = when (service) {
            AiService.CLAUDE -> claudeRepository
            AiService.CODEX -> codexRepository
            AiService.GEMINI -> geminiRepository
            AiService.DEEPSEEK -> deepSeekRepository
            AiService.CHATGPT_PLUS -> chatGptPlusRepository
            AiService.MIMO -> miMoRepository
        }

        saveCredential(service)

        _uiState.update { state ->
            val current = state.serviceStates[service] ?: ServiceCredentialState()
            state.copy(
                serviceStates = state.serviceStates + (service to current.copy(isValidating = true, validationResult = null))
            )
        }

        viewModelScope.launch {
            val result = repo.validateCredential()
            val validationResult = when (result) {
                is Result.Success -> ValidationResult.Success
                is Result.Failure -> ValidationResult.Failure(formatAppError(result.error))
            }

            _uiState.update { state ->
                val current = state.serviceStates[service] ?: ServiceCredentialState()
                state.copy(
                    serviceStates = state.serviceStates + (service to current.copy(
                        isValidating = false,
                        validationResult = validationResult
                    ))
                )
            }
        }
    }

    fun reloadCredentials() {
        loadSavedCredentials()
    }

    fun saveAll() {
        for (service in AiService.entries) {
            saveCredential(service)
        }
    }

    fun setRefreshInterval(minutes: Long) {
        prefsManager.setRefreshInterval(minutes)
        WorkManagerInitializer.scheduleConfiguredRefresh(appContext, prefsManager)
        _uiState.update { it.copy(refreshIntervalMinutes = minutes) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefsManager.setNotificationsEnabled(enabled)
        _uiState.update { it.copy(notificationsEnabled = enabled) }
    }

    fun showDeleteConfirmDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = true) }
    }

    fun dismissDeleteConfirmDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = false) }
    }

    fun deleteAllCredentials() {
        prefsManager.deleteAllCredentials()
        _uiState.update {
            SettingsUiState(
                enabledServices = prefsManager.getEnabledServices(),
                dashboardTheme = prefsManager.getDashboardTheme(),
                refreshIntervalMinutes = it.refreshIntervalMinutes,
                notificationsEnabled = it.notificationsEnabled
            )
        }
    }

    private fun formatExpiryMs(expiresAtMs: Long): String {
        return try {
            val instant = Instant.ofEpochMilli(expiresAtMs)
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (_: Exception) {
            "Unknown"
        }
    }

    private fun formatAppError(error: AppError): String {
        return when (error) {
            is AppError.NetworkError -> "Network error: ${error.message}"
            is AppError.AuthError -> if (error.isTerminal) "Authentication failed (re-login required)" else "Authentication error"
            is AppError.RateLimited -> "Rate limited"
            is AppError.ParseError -> "Parse error: ${error.message}"
            is AppError.CredentialNotFound -> "Not configured"
            is AppError.ServiceUnavailable -> "Service unavailable"
            is AppError.NeedsLogin -> error.message
        }
    }
}
