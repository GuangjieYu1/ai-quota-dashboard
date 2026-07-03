package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ExtraUsage
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.domain.repository.QuotaProvider
import com.codexbar.android.core.domain.model.ProviderKind
import com.codexbar.android.core.domain.model.ProviderQuota
import com.codexbar.android.core.domain.model.ProviderStatus
import com.codexbar.android.core.domain.model.QuotaMetric
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.network.mimo.MiMoApiService
import com.codexbar.android.core.network.mimo.MiMoDto
import com.codexbar.android.core.security.EncryptedPrefsManager
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class MiMoRepositoryImpl @Inject constructor(
    private val apiService: MiMoApiService,
    private val prefsManager: EncryptedPrefsManager,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : QuotaRepository, QuotaProvider {

    override val id: String = "mimo"
    override val displayName: String = "MiMo Token Plan"
    override val kind: ProviderKind = ProviderKind.COOKIE_USAGE

    override suspend fun fetchQuota(): Result<QuotaInfo, AppError> {
        val credential = prefsManager.loadCredential(AiService.MIMO)
            as? Credential.MiMoCredential
            ?: return Result.Failure(AppError.CredentialNotFound(AiService.MIMO))

        return try {
            if (credential.useBackendMode) {
                fetchFromBackend(credential)
            } else {
                fetchDirect(credential)
            }
        } catch (e: IOException) {
            Result.Failure(AppError.NetworkError(e.message ?: "Network error", e))
        } catch (e: Exception) {
            Result.Failure(AppError.ParseError(e.message ?: "Parse error", e))
        }
    }

    private suspend fun fetchFromBackend(credential: Credential.MiMoCredential): Result<QuotaInfo, AppError> {
        if (credential.backendUrl.isBlank()) {
            return Result.Failure(AppError.CredentialNotFound(AiService.MIMO))
        }

        val requestBuilder = Request.Builder()
            .url(credential.backendUrl)
            .get()

        if (credential.backendToken.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${credential.backendToken}")
        }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()

        return when (response.code) {
            200 -> {
                val body = response.body?.string()
                    ?: return Result.Failure(AppError.ParseError("Empty response body"))
                try {
                    val parsed = json.decodeFromString<MiMoDto.BackendUsageResponse>(body)
                    val total = parsed.totalTokens
                    val used = parsed.usedTokens
                    val utilization = if (total > 0) used.toDouble() / total.toDouble() else 0.0
                    Result.Success(
                        QuotaInfo(
                            service = AiService.MIMO,
                            windows = listOf(
                                UsageWindow(
                                    label = "Tokens",
                                    utilization = utilization,
                                    resetsAt = parsed.resetAt?.let { Instant.parse(it) }
                                )
                            ),
                            extraUsage = ExtraUsage(
                                isEnabled = true,
                                monthlyLimit = total.toDouble(),
                                usedCredits = used.toDouble(),
                                utilization = utilization,
                                currency = "Tokens"
                            ),
                            tier = parsed.planName ?: "Unknown",
                            fetchedAt = Instant.now()
                        )
                    )
                } catch (e: Exception) {
                    Result.Failure(AppError.ParseError("Backend response parse failed: ${e.message}"))
                }
            }
            401 -> Result.Failure(AppError.NeedsLogin(AiService.MIMO, "Backend auth failed"))
            else -> Result.Failure(
                AppError.NetworkError("Backend HTTP ${response.code}")
            )
        }
    }

    private suspend fun fetchDirect(credential: Credential.MiMoCredential): Result<QuotaInfo, AppError> {
        if (credential.directCookie.isBlank()) {
            return Result.Failure(AppError.NeedsLogin(AiService.MIMO, "MiMo cookie not configured"))
        }

        val usageResponse = apiService.getUsage(cookie = credential.directCookie)

        val usageBody = when (usageResponse.code()) {
            200 -> usageResponse.body() ?: return Result.Failure(AppError.ParseError("Empty usage response"))
            401 -> return Result.Failure(AppError.NeedsLogin(AiService.MIMO, "Cookie expired or invalid"))
            else -> return Result.Failure(AppError.NetworkError("HTTP ${usageResponse.code()}: ${usageResponse.message()}"))
        }

        if (usageBody.code != 0 || usageBody.data == null) {
            val msg = usageBody.message ?: "Cookie expired or invalid"
            return Result.Failure(AppError.NeedsLogin(AiService.MIMO, msg))
        }

        val planTotalItem = usageBody.data.usage?.items?.find { it.name == "plan_total_token" }
            ?: usageBody.data.monthUsage?.items?.find { it.name == "plan_total_token" }
        if (planTotalItem == null) {
            return Result.Failure(AppError.ParseError("plan_total_token not found in response"))
        }

        val used = planTotalItem.used
        val total = planTotalItem.limit
        val tokenUtilization = if (total > 0) used.toDouble() / total.toDouble() else 0.0

        val windows = mutableListOf(
            UsageWindow(label = "Tokens", utilization = tokenUtilization, resetsAt = null)
        )

        // Fetch plan detail for expiration info
        val detailResponse = apiService.getPlanDetail(cookie = credential.directCookie)
        if (detailResponse.code() == 200) {
            val detailBody = detailResponse.body()
            if (detailBody?.code == 0 && detailBody.data != null) {
                val currentPeriodEnd = detailBody.data.currentPeriodEnd
                val planName = detailBody.data.planName ?: "MiMo Plan"
                if (!currentPeriodEnd.isNullOrBlank()) {
                    try {
                        val endDate = LocalDate.parse(
                            currentPeriodEnd.substringBefore(" "),
                            DateTimeFormatter.ISO_LOCAL_DATE
                        )
                        val today = LocalDate.now()
                        val remainingDays = ChronoUnit.DAYS.between(today, endDate).toInt().coerceAtLeast(0)
                        val periodDays = 30
                        val expiredUtilization = if (remainingDays <= 0) 1.0
                            else (1.0 - remainingDays.toDouble() / periodDays).coerceIn(0.0, 1.0)

                        windows.add(
                            UsageWindow(
                                label = "Plan Expires",
                                utilization = expiredUtilization,
                                resetsAt = null,
                                remainingDays = remainingDays,
                                periodDays = periodDays
                            )
                        )

                        return Result.Success(
                            QuotaInfo(
                                service = AiService.MIMO,
                                windows = windows,
                                extraUsage = null,
                                tier = planName,
                                fetchedAt = Instant.now()
                            )
                        )
                    } catch (_: Exception) {}
                }
            }
        }

        // Fallback: token window only
        return Result.Success(
            QuotaInfo(
                service = AiService.MIMO,
                windows = windows,
                extraUsage = null,
                tier = "MiMo Plan",
                fetchedAt = Instant.now()
            )
        )
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
                val tokenWindow = info.windows.firstOrNull { it.label == "Tokens" }
                val planWindow = info.windows.firstOrNull { it.label == "Plan Expires" }
                val metrics = mutableListOf<QuotaMetric>()
                tokenWindow?.let { w ->
                    metrics.add(QuotaMetric(label = "Used", value = "${(w.utilization * 100).toInt()}%"))
                }
                planWindow?.let { w ->
                    metrics.add(QuotaMetric(label = "Plan", value = "${w.remainingDays ?: 0} days left"))
                }
                if (metrics.isEmpty()) {
                    metrics.add(QuotaMetric(label = "Status", value = "Unknown"))
                }
                val isExpired = planWindow?.remainingDays != null && planWindow.remainingDays <= 0
                val isLowTokens = tokenWindow?.utilization != null && tokenWindow.utilization >= 0.85
                ProviderQuota(
                    id = id,
                    displayName = displayName,
                    status = when {
                        isExpired -> ProviderStatus.ERROR
                        isLowTokens -> ProviderStatus.WARNING
                        else -> ProviderStatus.OK
                    },
                    planName = info.tier,
                    metrics = metrics,
                    lastUpdatedAt = formatInstant(info.fetchedAt)
                )
            }
            is Result.Failure -> {
                ProviderQuota(
                    id = id,
                    displayName = displayName,
                    status = when (val err = result.error) {
                        is AppError.NeedsLogin -> ProviderStatus.NEEDS_LOGIN
                        is AppError.CredentialNotFound -> ProviderStatus.NEEDS_LOGIN
                        else -> ProviderStatus.ERROR
                    },
                    metrics = emptyList(),
                    errorMessage = formatError(result.error)
                )
            }
        }
    }

    private fun formatNum(n: Long): String {
        return when {
            n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
            n >= 1_000 -> "${n / 1_000}.${(n % 1_000) / 100}K"
            else -> n.toString()
        }
    }

    private fun formatError(error: AppError): String {
        return when (error) {
            is AppError.NetworkError -> "Network error"
            is AppError.AuthError -> "Auth error"
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
