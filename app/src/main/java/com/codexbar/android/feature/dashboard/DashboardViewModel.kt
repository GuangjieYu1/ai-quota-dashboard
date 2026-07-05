package com.codexbar.android.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.DashboardThemeStyle
import com.codexbar.android.core.domain.model.ProviderStatus
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.di.ChatGPTPlusRepository
import com.codexbar.android.di.ClaudeRepository
import com.codexbar.android.di.CodexRepository
import com.codexbar.android.di.DeepSeekRepository
import com.codexbar.android.di.GeminiRepository
import com.codexbar.android.di.MiMoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ClaudeRepository private val claudeRepository: QuotaRepository,
    @CodexRepository private val codexRepository: QuotaRepository,
    @GeminiRepository private val geminiRepository: QuotaRepository,
    @DeepSeekRepository private val deepSeekRepository: QuotaRepository,
    @ChatGPTPlusRepository private val chatGPTPlusRepository: QuotaRepository,
    @MiMoRepository private val miMoRepository: QuotaRepository,
    private val prefsManager: EncryptedPrefsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _dashboardTheme = MutableStateFlow(prefsManager.getDashboardTheme())
    val dashboardTheme: StateFlow<DashboardThemeStyle> = _dashboardTheme.asStateFlow()

    private val repoMap: Map<AiService, QuotaRepository> = mapOf(
        AiService.CLAUDE to claudeRepository,
        AiService.CODEX to codexRepository,
        AiService.GEMINI to geminiRepository,
        AiService.DEEPSEEK to deepSeekRepository,
        AiService.CHATGPT_PLUS to chatGPTPlusRepository,
        AiService.MIMO to miMoRepository
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _uiState.value = DashboardUiState.Loading
            _dashboardTheme.value = prefsManager.getDashboardTheme()

            val enabledServices = prefsManager.getEnabledServices()
            val repos = repoMap.filterKeys { service ->
                enabledServices.contains(service)
            }

            if (repos.isEmpty()) {
                _uiState.value = DashboardUiState.Success(emptyList(), Instant.now())
                _isRefreshing.value = false
                return@launch
            }

            val deferreds = repos.map { (service, repo) ->
                async { service to repo.fetchQuota() }
            }

            val results = deferreds.map { it.await() }

            val successCards = mutableListOf<ServiceCardData>()
            val errors = mutableMapOf<AiService, AppError>()

            for ((service, result) in results) {
                when (result) {
                    is Result.Success -> {
                        successCards.add(mapToCardData(result.value))
                    }
                    is Result.Failure -> {
                        errors[service] = result.error
                        successCards.add(
                            ServiceCardData(
                                service = service,
                                windows = emptyList(),
                                extraUsage = null,
                                tier = null,
                                error = result.error
                            )
                        )
                    }
                }
            }

            val sortedCards = successCards.sortedByDescending { card ->
                maxUtilization(card)
            }.map { card ->
                if (card.error != null) card.copy(status = mapErrorToStatus(card.error))
                else {
                    val maxUtil = maxUtilization(card)
                    val status = when {
                        maxUtil >= 1.0 -> ProviderStatus.ERROR
                        maxUtil >= 0.8 -> ProviderStatus.WARNING
                        else -> ProviderStatus.OK
                    }
                    card.copy(status = status, lastUpdated = Instant.now())
                }
            }

            val allCredentialErrors = errors.isNotEmpty() &&
                errors.all { (_, e) -> e is AppError.CredentialNotFound }

            _uiState.value = when {
                errors.isEmpty() -> DashboardUiState.Success(sortedCards, Instant.now())
                allCredentialErrors -> DashboardUiState.Success(sortedCards, Instant.now())
                successCards.all { it.error != null } -> DashboardUiState.Error(errors.values.first())
                else -> DashboardUiState.PartialSuccess(sortedCards, errors)
            }

            _isRefreshing.value = false
        }
    }

    private fun maxUtilization(card: ServiceCardData): Double {
        return card.windows.maxOfOrNull { it.utilization }
            ?: card.extraUsage?.utilization
            ?: 0.0
    }

    private fun mapErrorToStatus(error: AppError): ProviderStatus {
        return when (error) {
            is AppError.NeedsLogin -> ProviderStatus.NEEDS_LOGIN
            is AppError.AuthError -> ProviderStatus.NEEDS_LOGIN
            is AppError.CredentialNotFound -> ProviderStatus.NEEDS_LOGIN
            else -> ProviderStatus.ERROR
        }
    }

    private fun mapToCardData(quotaInfo: QuotaInfo): ServiceCardData {
        return ServiceCardData(
            service = quotaInfo.service,
            windows = quotaInfo.windows.map { window ->
                val resetsAtLabel = if (window.label == "7d Usage" && window.resetsAt != null) {
                    java.time.ZoneId.systemDefault().let { zone ->
                        val zdt = window.resetsAt.atZone(zone)
                        String.format(
                            "%d年%02d月%02d日 %02d:%02d",
                            zdt.year, zdt.monthValue, zdt.dayOfMonth,
                            zdt.hour, zdt.minute
                        )
                    }
                } else null
                UsageWindowUi(
                    label = window.label,
                    utilization = window.utilization,
                    resetsAt = window.resetsAt,
                    remainingDays = window.remainingDays,
                    periodDays = window.periodDays,
                    resetsAtLabel = resetsAtLabel
                )
            },
            extraUsage = quotaInfo.extraUsage?.let { extra ->
                ExtraUsageUi(
                    monthlyLimit = extra.monthlyLimit,
                    usedCredits = extra.usedCredits,
                    utilization = extra.utilization,
                    currency = extra.currency
                )
            },
            tier = quotaInfo.tier,
            lastUpdated = quotaInfo.fetchedAt
        )
    }
}
