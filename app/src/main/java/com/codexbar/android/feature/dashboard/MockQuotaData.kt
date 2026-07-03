package com.codexbar.android.feature.dashboard

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.ProviderStatus
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

object MockQuotaData {

    private val tomorrow: Instant = Instant.now().plus(Duration.ofDays(1))
    private val nextWeek: Instant = Instant.now().plus(Duration.ofDays(7))
    private val farFuture: Instant = ZonedDateTime.parse("2026-07-19T00:00:00Z").toInstant()

    fun providers(): List<ServiceCardData> = listOf(
        chatGptPlus(),
        codex(),
        deepSeek(),
        miMo()
    )

    private fun chatGptPlus() = ServiceCardData(
        service = AiService.CHATGPT_PLUS,
        windows = listOf(
            UsageWindowUi(label = "Subscription", utilization = 0.47, resetsAt = farFuture)
        ),
        extraUsage = null,
        tier = "Plus",
        status = ProviderStatus.OK,
        lastUpdated = Instant.now()
    )

    private fun codex() = ServiceCardData(
        service = AiService.CODEX,
        windows = listOf(
            UsageWindowUi(label = "5-hour window", utilization = 0.58, resetsAt = tomorrow),
            UsageWindowUi(label = "7-day window", utilization = 0.32, resetsAt = nextWeek)
        ),
        extraUsage = null,
        tier = "Plus",
        status = ProviderStatus.WARNING,
        lastUpdated = Instant.now()
    )

    private fun deepSeek() = ServiceCardData(
        service = AiService.DEEPSEEK,
        windows = listOf(
            UsageWindowUi(label = "Balance ¥12.34", utilization = 0.0, resetsAt = null)
        ),
        extraUsage = null,
        tier = "API Balance",
        status = ProviderStatus.OK,
        lastUpdated = Instant.now()
    )

    private fun miMo() = ServiceCardData(
        service = AiService.MIMO,
        windows = emptyList(),
        extraUsage = null,
        tier = "Lite",
        status = ProviderStatus.NEEDS_LOGIN,
        lastUpdated = Instant.now(),
        error = AppError.NeedsLogin(AiService.MIMO, "Cookie not configured")
    )
}
