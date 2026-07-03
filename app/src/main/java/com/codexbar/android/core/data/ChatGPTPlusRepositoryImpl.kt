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
import com.codexbar.android.core.security.EncryptedPrefsManager
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class ChatGPTPlusRepositoryImpl @Inject constructor(
    private val prefsManager: EncryptedPrefsManager
) : QuotaRepository, QuotaProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override val id: String = "chatgpt_plus"
    override val displayName: String = "ChatGPT Plus"
    override val kind: ProviderKind = ProviderKind.MANUAL_PLAN

    override suspend fun fetchQuota(): Result<QuotaInfo, AppError> {
        val credential = prefsManager.loadCredential(AiService.CHATGPT_PLUS)
            as? Credential.ChatGPTPlusCredential

        val planName = credential?.planName?.ifBlank { "Plus" } ?: "Plus"
        var renewalStr = credential?.renewalDate ?: ""
        var billingPeriod = credential?.billingPeriod?.ifBlank { "Monthly" } ?: "Monthly"

        if (renewalStr.isBlank() && credential?.manualSessionResponse != null) {
            try {
                val session = json.decodeFromString<ChatGPTPlusRepositoryImpl.SessionResponse>(credential.manualSessionResponse)
                if (renewalStr.isBlank()) renewalStr = session.plan?.renewalDate ?: ""
                if (billingPeriod.isBlank()) {
                    billingPeriod = when (session.plan?.interval?.lowercase()) {
                        "year" -> "Yearly"
                        else -> "Monthly"
                    }
                }
            } catch (_: Exception) {}
        }

        if (renewalStr.isBlank()) {
            return Result.Failure(AppError.CredentialNotFound(AiService.CHATGPT_PLUS))
        }

        return try {
            val lastRenewal = LocalDate.parse(renewalStr, DateTimeFormatter.ISO_LOCAL_DATE)
            val today = LocalDate.now()
            val periodDays = when (billingPeriod.lowercase()) {
                "yearly" -> 365
                else -> 30
            }

            val nextRenewal = lastRenewal.plusDays(periodDays.toLong())
            val remainingDays = ChronoUnit.DAYS.between(today, nextRenewal).toInt().coerceAtLeast(0)
            val utilization = if (remainingDays <= 0) {
                1.0
            } else {
                (1.0 - remainingDays.toDouble() / periodDays).coerceIn(0.0, 1.0)
            }

            Result.Success(
                QuotaInfo(
                    service = AiService.CHATGPT_PLUS,
                    windows = listOf(
                        UsageWindow(
                            label = "Remaining",
                            utilization = utilization,
                            resetsAt = nextRenewal.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                            remainingDays = remainingDays,
                            periodDays = periodDays
                        )
                    ),
                    extraUsage = null,
                    tier = planName,
                    fetchedAt = Instant.now()
                )
            )
        } catch (e: Exception) {
            Result.Failure(AppError.ParseError("Invalid renewal date format. Use yyyy-MM-dd"))
        }
    }

    private data class SessionResponse(
        val plan: SessionPlan? = null
    )

    private data class SessionPlan(
        val renewalDate: String? = null,
        val interval: String? = null
    )

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
                val remainingDays = info.windows.firstOrNull()?.remainingDays ?: 0
                val periodDays = info.windows.firstOrNull()?.periodDays ?: 30

                val status = when {
                    remainingDays <= 0 -> ProviderStatus.ERROR
                    remainingDays <= 7 -> ProviderStatus.WARNING
                    else -> ProviderStatus.OK
                }
                ProviderQuota(
                    id = id,
                    displayName = displayName,
                    status = status,
                    planName = info.tier,
                    metrics = listOf(
                        QuotaMetric(
                            label = "Remaining",
                            value = if (remainingDays > 0) "${remainingDays} days" else "Expired"
                        )
                    ),
                    lastUpdatedAt = formatInstant(info.fetchedAt)
                )
            }
            is Result.Failure -> {
                val error = result.error
                ProviderQuota(
                    id = id,
                    displayName = displayName,
                    status = when (error) {
                        is AppError.CredentialNotFound -> ProviderStatus.NEEDS_LOGIN
                        else -> ProviderStatus.ERROR
                    },
                    metrics = emptyList(),
                    errorMessage = formatError(error)
                )
            }
        }
    }

    private fun formatError(error: AppError): String {
        return when (error) {
            is AppError.NetworkError -> "Network error"
            is AppError.AuthError -> "Auth error"
            is AppError.RateLimited -> "Rate limited"
            is AppError.ParseError -> error.message
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
