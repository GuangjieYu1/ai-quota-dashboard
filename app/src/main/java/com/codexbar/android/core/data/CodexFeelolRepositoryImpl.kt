package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.network.codexfeelol.CodexFeelolApiService
import com.codexbar.android.core.network.codexfeelol.CodexFeelolDto
import com.codexbar.android.core.security.EncryptedPrefsManager
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import javax.inject.Inject

class CodexFeelolRepositoryImpl @Inject constructor(
    private val apiService: CodexFeelolApiService,
    private val prefsManager: EncryptedPrefsManager
) : QuotaRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun fetchQuota(): Result<QuotaInfo, AppError> {
        val credential = prefsManager.loadCredential(AiService.CODEX_FEELOL)
            as? Credential.CodexFeelolCredential
            ?: return Result.Failure(AppError.CredentialNotFound(AiService.CODEX_FEELOL))

        credential.manualResponse?.takeIf { it.isNotBlank() }?.let { response ->
            return parseManualResponse(response)
        }

        if (credential.accessToken.isBlank()) {
            return Result.Failure(AppError.CredentialNotFound(AiService.CODEX_FEELOL))
        }

        return try {
            val response = apiService.getSubscriptions("Bearer ${credential.accessToken}")
            when (response.code()) {
                200 -> {
                    val body = response.body()
                        ?: return Result.Failure(AppError.ParseError("Empty feelol response body"))
                    Result.Success(mapToQuotaInfo(body))
                }
                401, 403 -> Result.Failure(
                    AppError.AuthError(
                        AiService.CODEX_FEELOL,
                        isTerminal = true,
                        message = "feelol login expired"
                    )
                )
                else -> Result.Failure(AppError.NetworkError("HTTP ${response.code()}: ${response.message()}"))
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

    private fun parseManualResponse(response: String): Result<QuotaInfo, AppError> {
        return try {
            Result.Success(mapToQuotaInfo(json.decodeFromString<CodexFeelolDto.SubscriptionResponse>(response)))
        } catch (e: Exception) {
            Result.Failure(AppError.ParseError("Invalid feelol response: ${e.message}", e))
        }
    }

    private fun mapToQuotaInfo(response: CodexFeelolDto.SubscriptionResponse): QuotaInfo {
        if (response.code != null && response.code != 0) {
            error(response.message ?: "feelol request failed")
        }

        val subscription = response.data.firstOrNull { it.status.equals("active", ignoreCase = true) }
            ?: response.data.firstOrNull()
            ?: error("No feelol subscription data")
        val group = subscription.group ?: error("Missing feelol subscription group")

        val windows = buildList {
            add(buildWindow("Daily", subscription.dailyUsageUsd, group.dailyLimitUsd, subscription.dailyWindowStart, WindowKind.DAILY))
            add(buildWindow("Weekly", subscription.weeklyUsageUsd, group.weeklyLimitUsd, subscription.weeklyWindowStart, WindowKind.WEEKLY))
            add(buildWindow("Monthly", subscription.monthlyUsageUsd, group.monthlyLimitUsd, subscription.monthlyWindowStart, WindowKind.MONTHLY))
            buildExpiryWindow(subscription.startsAt, subscription.expiresAt)?.let { add(it) }
        }

        return QuotaInfo(
            service = AiService.CODEX_FEELOL,
            windows = windows,
            extraUsage = null,
            tier = group.name?.takeIf { it.isNotBlank() } ?: "feelol",
            fetchedAt = Instant.now()
        )
    }

    private fun buildWindow(
        label: String,
        used: Double,
        limit: Double,
        windowStart: String?,
        kind: WindowKind
    ): UsageWindow {
        val utilization = if (limit > 0) (used / limit).coerceIn(0.0, 1.0) else 0.0
        val reset = parseOffset(windowStart)?.let { start ->
            when (kind) {
                WindowKind.DAILY -> start.plusDays(1)
                WindowKind.WEEKLY -> start.plusDays(7)
                WindowKind.MONTHLY -> start.plusMonths(1)
            }.toInstant()
        }
        return UsageWindow(label = label, utilization = utilization, resetsAt = reset)
    }

    private fun buildExpiryWindow(startsAt: String?, expiresAt: String?): UsageWindow? {
        val start = parseOffset(startsAt)?.toInstant() ?: return null
        val expires = parseOffset(expiresAt)?.toInstant() ?: return null
        val now = Instant.now()
        val totalDays = Duration.between(start, expires).toDays().coerceAtLeast(1).toInt()
        val remainingDays = Duration.between(now, expires).toDays().coerceAtLeast(0).toInt()
        val elapsedMillis = Duration.between(start, now).toMillis().coerceAtLeast(0)
        val totalMillis = Duration.between(start, expires).toMillis().coerceAtLeast(1)
        val utilization = (elapsedMillis.toDouble() / totalMillis.toDouble()).coerceIn(0.0, 1.0)
        return UsageWindow(
            label = "Expires",
            utilization = utilization,
            resetsAt = null,
            remainingDays = remainingDays,
            periodDays = totalDays
        )
    }

    private fun parseOffset(value: String?): OffsetDateTime? {
        if (value.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME) }.getOrNull()
    }

    private enum class WindowKind { DAILY, WEEKLY, MONTHLY }
}
