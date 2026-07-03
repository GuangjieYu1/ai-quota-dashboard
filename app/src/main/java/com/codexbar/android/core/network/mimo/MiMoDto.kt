package com.codexbar.android.core.network.mimo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object MiMoDto {

    @Serializable
    data class UsageResponse(
        val code: Int? = null,
        val message: String? = null,
        val data: UsageData? = null
    )

    @Serializable
    data class UsageData(
        val usage: TokenGroup? = null,
        @SerialName("monthUsage") val monthUsage: TokenGroup? = null
    )

    @Serializable
    data class TokenGroup(
        val percent: Double = 0.0,
        val items: List<TokenItem> = emptyList()
    )

    @Serializable
    data class TokenItem(
        val name: String = "",
        val used: Long = 0,
        val limit: Long = 0,
        val percent: Double = 0.0
    )

    @Serializable
    data class BackendUsageResponse(
        val provider: String = "mimo",
        val planName: String? = null,
        val usedTokens: Long = 0,
        val remainingTokens: Long = 0,
        val totalTokens: Long = 0,
        val resetAt: String? = null,
        val lastUpdatedAt: String? = null
    )

    @Serializable
    data class PlanDetailResponse(
        val code: Int? = null,
        val message: String? = null,
        val data: PlanDetailData? = null
    )

    @Serializable
    data class PlanDetailData(
        val planCode: String? = null,
        val planName: String? = null,
        val currentPeriodEnd: String? = null,
        val expired: Boolean? = null,
        val enableAutoRenew: Boolean? = null,
        val autoRenewDiscount: String? = null,
        val hasAutoRenewSubscribed: Boolean? = null,
        val clawEnabled: Boolean? = null,
        val clawPeriodEnd: String? = null,
        val clawPurchased: Boolean? = null
    )
}
