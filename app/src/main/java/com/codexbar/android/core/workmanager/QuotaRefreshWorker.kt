package com.codexbar.android.core.workmanager

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result as WorkResult
import androidx.work.WorkerParameters
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result as DomainResult
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.tile.QuotaTileService
import com.codexbar.android.core.widget.QuotaGlanceWidget
import com.codexbar.android.core.widget.WidgetPrefsManager
import com.codexbar.android.di.ChatGPTPlusRepository
import com.codexbar.android.di.ClaudeRepository
import com.codexbar.android.di.CodexRepository
import com.codexbar.android.di.DeepSeekRepository
import com.codexbar.android.di.GeminiRepository
import com.codexbar.android.di.MiMoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant

@HiltWorker
class QuotaRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    @ClaudeRepository private val claudeRepository: QuotaRepository,
    @CodexRepository private val codexRepository: QuotaRepository,
    @GeminiRepository private val geminiRepository: QuotaRepository,
    @DeepSeekRepository private val deepSeekRepository: QuotaRepository,
    @ChatGPTPlusRepository private val chatGPTPlusRepository: QuotaRepository,
    @MiMoRepository private val miMoRepository: QuotaRepository,
    private val prefsManager: EncryptedPrefsManager,
    private val notificationService: QuotaNotificationService,
    private val widgetPrefsManager: WidgetPrefsManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): WorkResult {
        val enabledServices = prefsManager.getEnabledServices()
        val repos = buildList {
            if (enabledServices.contains(AiService.CLAUDE) && prefsManager.hasCredential(AiService.CLAUDE)) {
                add(AiService.CLAUDE to claudeRepository)
            }
            if (enabledServices.contains(AiService.CODEX) && prefsManager.hasCredential(AiService.CODEX)) {
                add(AiService.CODEX to codexRepository)
            }
            if (enabledServices.contains(AiService.GEMINI) && prefsManager.hasCredential(AiService.GEMINI)) {
                add(AiService.GEMINI to geminiRepository)
            }
            if (enabledServices.contains(AiService.DEEPSEEK) && prefsManager.hasCredential(AiService.DEEPSEEK)) {
                add(AiService.DEEPSEEK to deepSeekRepository)
            }
            if (enabledServices.contains(AiService.CHATGPT_PLUS) && prefsManager.hasCredential(AiService.CHATGPT_PLUS)) {
                add(AiService.CHATGPT_PLUS to chatGPTPlusRepository)
            }
            if (enabledServices.contains(AiService.MIMO) && prefsManager.hasCredential(AiService.MIMO)) {
                add(AiService.MIMO to miMoRepository)
            }
        }

        if (repos.isEmpty()) return WorkResult.success()

        return try {
            val quotas = coroutineScope {
                repos.map { (_, repo) ->
                    async { repo.fetchQuota() }
                }.awaitAll()
            }

            val successfulQuotas = quotas.mapNotNull { result ->
                when (result) {
                    is DomainResult.Success -> result.value
                    is DomainResult.Failure -> null
                }
            }

            if (successfulQuotas.isNotEmpty()) {
                // Cache quota data for widgets
                cacheQuotaData(successfulQuotas)

                if (prefsManager.isNotificationsEnabled()) {
                    notificationService.showQuotaNotification(successfulQuotas)
                    checkForResets(successfulQuotas)
                }

                // Update all widgets
                QuotaGlanceWidget().updateAll(applicationContext)
            }

            // Request tile update
            TileService.requestListeningState(
                applicationContext,
                ComponentName(applicationContext, QuotaTileService::class.java)
            )

            val hasRetryableFailure = quotas.any { result ->
                result is DomainResult.Failure && result.error.isRetryable()
            }

            if (successfulQuotas.isEmpty() && hasRetryableFailure) {
                WorkResult.retry()
            } else {
                WorkResult.success()
            }
        } catch (_: Exception) {
            WorkResult.retry()
        }
    }

    private fun AppError.isRetryable(): Boolean {
        return when (this) {
            is AppError.CredentialNotFound,
            is AppError.NeedsLogin -> false
            is AppError.AuthError -> !isTerminal
            else -> true
        }
    }

    private fun cacheQuotaData(quotas: List<QuotaInfo>) {
        for (quota in quotas) {
            val sourceWindows = if (quota.windows.isEmpty() && quota.extraUsage != null) {
                listOf(Triple("Balance", quota.extraUsage.utilization, null as Long?))
            } else {
                quota.windows.map { window ->
                    Triple(
                        window.label,
                        window.utilization,
                        window.resetsAt?.epochSecond
                    )
                }
            }
            val windows = sourceWindows.map { (label, utilization, resetsAt) ->
                Triple(
                    label,
                    utilization,
                    resetsAt
                )
            }
            widgetPrefsManager.cacheAllQuotaData(quota.service, windows)
            widgetPrefsManager.cacheTier(quota.service, quota.tier)
        }
    }

    private fun checkForResets(quotas: List<QuotaInfo>) {
        val now = Instant.now()
        for (quota in quotas) {
            val previousResetTimes = prefsManager.loadResetTimes(quota.service)

            // Detect resets: previous resetsAt was in the future, now it's in the past
            for (window in quota.windows) {
                val previousResetAt = previousResetTimes[window.label] ?: continue
                if (previousResetAt.isBefore(now) && window.resetsAt != null && window.resetsAt.isAfter(now)) {
                    notificationService.showResetNotification(quota.service, window.label)
                }
            }

            // Save current reset times for next comparison
            prefsManager.saveResetTimes(
                quota.service,
                quota.windows.map { it.label to it.resetsAt }
            )
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = android.app.Notification.Builder(applicationContext, QuotaNotificationService.CHANNEL_ID)
            .setContentTitle("Refreshing quota data...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()

        return ForegroundInfo(QuotaNotificationService.NOTIFICATION_ID + 1, notification)
    }
}
