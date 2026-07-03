package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ExtraUsage
import com.codexbar.android.core.domain.model.ProviderKind
import com.codexbar.android.core.domain.model.ProviderQuota
import com.codexbar.android.core.domain.model.ProviderStatus
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.QuotaMetric
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.domain.repository.QuotaProvider
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.network.deepseek.DeepSeekApiService
import com.codexbar.android.core.network.deepseek.DeepSeekDto
import com.codexbar.android.core.security.EncryptedPrefsManager
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class DeepSeekRepositoryImpl @Inject constructor(
    private val apiService: DeepSeekApiService,
    private val prefsManager: EncryptedPrefsManager
) : QuotaRepository, QuotaProvider {

    override val id: String = "deepseek"
    override val displayName: String = "DeepSeek"
    override val kind: ProviderKind = ProviderKind.API_BALANCE

    override suspend fun fetchQuota(): Result<QuotaInfo, AppError> {
        val credential = prefsManager.loadCredential(AiService.DEEPSEEK)
            as? Credential.DeepSeekCredential
            ?: return Result.Failure(AppError.CredentialNotFound(AiService.DEEPSEEK))

        return try {
            val response = apiService.getBalance(
                authorization = "Bearer ${credential.accessToken}"
            )

            when (response.code()) {
                200 -> {
                    val body = response.body()
                        ?: return Result.Failure(AppError.ParseError("Empty response body"))
                    Result.Success(mapToQuotaInfo(body, credential.initialTotal))
                }
                401 -> Result.Failure(AppError.AuthError(AiService.DEEPSEEK, isTerminal = true, message = "Invalid API key"))
                402 -> Result.Failure(AppError.AuthError(AiService.DEEPSEEK, isTerminal = true, message = "Insufficient balance"))
                else -> Result.Failure(
                    AppError.NetworkError("HTTP ${response.code()}: ${response.message()}")
                )
            }
        } catch (e: IOException) {
            Result.Failure(AppError.NetworkError(e.message ?: "Network error", e))
        } catch (e: Exception) {
            Result.Failure(AppError.ParseError(e.message ?: "Parse error", e))
        }
    }

    override suspend fun validateCredential(): Result<Unit, AppError> {
        return when (val result = fetchQuota()) {
            is Result.Success -> Result.Success(Unit)
            is Result.Failure -> Result.Failure(result.error)
        }
    }

    override suspend fun refresh(): ProviderQuota {
        return when (val result = fetchQuota()) {
            is Result.Success -> {
                val info = result.value
                val balance = info.extraUsage?.monthlyLimit ?: 0.0
                val granted = info.extraUsage?.usedCredits ?: 0.0
                val toppedUp = balance - granted
                val currency = info.extraUsage?.currency ?: "CNY"
                val sym = if (currency == "CNY") "¥" else if (currency == "USD") "$" else "$"
                ProviderQuota(
                    id = id,
                    displayName = displayName,
                    status = if (balance > 0) ProviderStatus.OK else ProviderStatus.ERROR,
                    planName = info.tier,
                    metrics = listOf(
                        QuotaMetric(label = "Balance", value = "$sym${String.format("%.2f", balance)}"),
                        QuotaMetric(label = "Granted", value = "$sym${String.format("%.2f", granted)}"),
                        QuotaMetric(label = "Topped-up", value = "$sym${String.format("%.2f", toppedUp)}")
                    ),
                    lastUpdatedAt = formatInstant(info.fetchedAt)
                )
            }
            is Result.Failure -> {
                ProviderQuota(
                    id = id,
                    displayName = displayName,
                    status = when (val err = result.error) {
                        is AppError.AuthError -> ProviderStatus.NEEDS_LOGIN
                        is AppError.CredentialNotFound -> ProviderStatus.NEEDS_LOGIN
                        else -> ProviderStatus.ERROR
                    },
                    metrics = emptyList(),
                    errorMessage = formatError(result.error)
                )
            }
        }
    }

    private fun mapToQuotaInfo(response: DeepSeekDto.BalanceResponse, initialTotal: Double = 0.0): QuotaInfo {
        val info = response.balanceInfos.firstOrNull()
        val totalBalance = info?.totalBalance?.toDoubleOrNull() ?: 0.0
        val currency = info?.currency ?: "CNY"

        val utilization = if (initialTotal > 0) {
            (1.0 - (totalBalance / initialTotal)).coerceIn(0.0, 1.0)
        } else 0.0

        return QuotaInfo(
            service = AiService.DEEPSEEK,
            windows = emptyList(),
            extraUsage = ExtraUsage(
                isEnabled = response.isAvailable,
                monthlyLimit = initialTotal,
                usedCredits = totalBalance,
                utilization = utilization,
                currency = currency
            ),
            tier = if (response.isAvailable) "Active" else "Unavailable",
            fetchedAt = Instant.now()
        )
    }

    private fun formatError(error: AppError): String {
        return when (error) {
            is AppError.NetworkError -> "Network error"
            is AppError.AuthError -> if (error.isTerminal) "Invalid API Key" else "Auth error"
            is AppError.RateLimited -> "Rate limited"
            is AppError.ParseError -> "Parse error"
            is AppError.CredentialNotFound -> "Not configured"
            is AppError.ServiceUnavailable -> "Service unavailable"
            is AppError.NeedsLogin -> error.message
        }
    }

    private fun formatInstant(instant: Instant): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }
}
