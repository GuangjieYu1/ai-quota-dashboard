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
import com.codexbar.android.core.domain.repository.QuotaProvider
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.network.deepseek.DeepSeekApiService
import com.codexbar.android.core.network.deepseek.DeepSeekDto
import com.codexbar.android.core.security.EncryptedPrefsManager
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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

        return if (credential.sessionCookie.isNotBlank()) {
            fetchQuotaFromUserSummary(credential)
        } else {
            fetchQuotaFromApiBalance(credential)
        }
    }

    private suspend fun fetchQuotaFromUserSummary(
        credential: Credential.DeepSeekCredential
    ): Result<QuotaInfo, AppError> {
        return try {
            val response = apiService.getUserSummary(cookie = credential.sessionCookie)
            when (response.code()) {
                200 -> {
                    val body = response.body()
                        ?: return Result.Failure(AppError.ParseError("Empty response body"))
                    if (body.code != null && body.code != 0) {
                        val message = body.message ?: "DeepSeek summary rejected the request"
                        return if (message.contains("token", ignoreCase = true) ||
                            message.contains("auth", ignoreCase = true) ||
                            message.contains("login", ignoreCase = true)
                        ) {
                            Result.Failure(AppError.AuthError(AiService.DEEPSEEK, isTerminal = true, message = message))
                        } else {
                            Result.Failure(AppError.ParseError(message))
                        }
                    }
                    Result.Success(mapUserSummaryToQuotaInfo(body))
                }
                401, 403 -> Result.Failure(AppError.AuthError(AiService.DEEPSEEK, isTerminal = true, message = "DeepSeek login expired"))
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

    private suspend fun fetchQuotaFromApiBalance(
        credential: Credential.DeepSeekCredential
    ): Result<QuotaInfo, AppError> {
        if (credential.accessToken.isBlank()) {
            return Result.Failure(AppError.CredentialNotFound(AiService.DEEPSEEK))
        }

        return try {
            val response = apiService.getBalance(
                authorization = "Bearer ${credential.accessToken}"
            )

            when (response.code()) {
                200 -> {
                    val body = response.body()
                        ?: return Result.Failure(AppError.ParseError("Empty response body"))
                    Result.Success(mapBalanceToQuotaInfo(body, credential.initialTotal))
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
                val total = info.extraUsage?.monthlyLimit ?: 0.0
                val used = info.extraUsage?.usedCredits ?: 0.0
                val balance = (total - used).coerceAtLeast(0.0)
                val currency = info.extraUsage?.currency ?: "CNY"
                val sym = currencySymbol(currency)
                ProviderQuota(
                    id = id,
                    displayName = displayName,
                    status = if (balance > 0) ProviderStatus.OK else ProviderStatus.ERROR,
                    planName = info.tier,
                    metrics = listOf(
                        QuotaMetric(label = "Used", value = "$sym${formatAmount(used)}"),
                        QuotaMetric(label = "Balance", value = "$sym${formatAmount(balance)}"),
                        QuotaMetric(label = "Total", value = "$sym${formatAmount(total)}")
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

    private fun mapUserSummaryToQuotaInfo(response: DeepSeekDto.UserSummaryResponse): QuotaInfo {
        val data = response.data ?: error("Missing DeepSeek summary data")
        val amount = findDouble(data.monthlyCosts, "amount")
            ?: error("Missing monthly_costs.amount")
        val balance = findDouble(data.normalWallets, "balance")
            ?: error("Missing normal_wallets.balance")
        val total = (amount + balance).coerceAtLeast(0.0)
        val utilization = if (total > 0) {
            (amount / total).coerceIn(0.0, 1.0)
        } else 0.0
        val currency = findString(data.normalWallets, "currency")
            ?: findString(data.monthlyCosts, "currency")
            ?: "CNY"

        return QuotaInfo(
            service = AiService.DEEPSEEK,
            windows = emptyList(),
            extraUsage = ExtraUsage(
                isEnabled = true,
                monthlyLimit = total,
                usedCredits = amount,
                utilization = utilization,
                currency = currency
            ),
            tier = "Platform",
            fetchedAt = Instant.now()
        )
    }

    private fun mapBalanceToQuotaInfo(response: DeepSeekDto.BalanceResponse, initialTotal: Double = 0.0): QuotaInfo {
        val info = response.balanceInfos.firstOrNull()
        val totalBalance = info?.totalBalance?.toDoubleOrNull() ?: 0.0
        val currency = info?.currency ?: "CNY"
        val total = if (initialTotal > 0) initialTotal else totalBalance
        val used = (total - totalBalance).coerceAtLeast(0.0)

        val utilization = if (total > 0) {
            (used / total).coerceIn(0.0, 1.0)
        } else 0.0

        return QuotaInfo(
            service = AiService.DEEPSEEK,
            windows = emptyList(),
            extraUsage = ExtraUsage(
                isEnabled = response.isAvailable,
                monthlyLimit = total,
                usedCredits = used,
                utilization = utilization,
                currency = currency
            ),
            tier = if (response.isAvailable) "Active" else "Unavailable",
            fetchedAt = Instant.now()
        )
    }

    private fun findDouble(element: JsonElement?, key: String): Double? {
        val value = findPrimitive(element, key)?.contentOrNull ?: return null
        return value
            .replace(",", "")
            .filter { it.isDigit() || it == '.' || it == '-' }
            .toDoubleOrNull()
    }

    private fun findString(element: JsonElement?, key: String): String? {
        return findPrimitive(element, key)?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    private fun findPrimitive(element: JsonElement?, key: String): JsonPrimitive? {
        return when (element) {
            is JsonObject -> {
                element[key] as? JsonPrimitive
                    ?: element.values.firstNotNullOfOrNull { findPrimitive(it, key) }
            }
            is JsonArray -> element.firstNotNullOfOrNull { findPrimitive(it, key) }
            is JsonPrimitive -> null
            null -> null
        }
    }

    private fun currencySymbol(currency: String): String {
        return when (currency.uppercase()) {
            "CNY", "RMB", "¥" -> "¥"
            "USD", "$" -> "$"
            else -> "$currency "
        }
    }

    private fun formatAmount(amount: Double): String {
        return String.format("%.0f", amount)
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
