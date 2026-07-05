package com.codexbar.android.feature.settings

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.DashboardThemeStyle

data class SettingsUiState(
    val serviceStates: Map<AiService, ServiceCredentialState> = AiService.entries.associateWith {
        ServiceCredentialState()
    },
    val enabledServices: Set<AiService> = emptySet(),
    val dashboardTheme: DashboardThemeStyle = DashboardThemeStyle.SYSTEM,
    val refreshIntervalMinutes: Long = 30L,
    val notificationsEnabled: Boolean = true,
    val showDeleteConfirmDialog: Boolean = false
)

data class ServiceCredentialState(
    val accessToken: String = "",
    val refreshToken: String = "",
    val accountId: String = "",
    val manualResponse: String = "",
    val oauthClientId: String = "",
    val oauthClientSecret: String = "",
    val expiresAtDisplay: String = "",
    val baseUrl: String = "",
    val initialTotal: String = "",
    val sessionCookie: String = "",
    val planName: String = "",
    val renewalDate: String = "",
    val billingPeriod: String = "Monthly",
    val notes: String = "",
    val manualSessionResponse: String = "",
    val backendUrl: String = "",
    val backendToken: String = "",
    val directCookie: String = "",
    val useBackendMode: Boolean = true,
    val isValidating: Boolean = false,
    val validationResult: ValidationResult? = null
)

sealed class ValidationResult {
    data object Success : ValidationResult()
    data class Failure(val message: String) : ValidationResult()
}
