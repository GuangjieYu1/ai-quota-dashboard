package com.codexbar.android.core.network.codexfeelol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object CodexFeelolDto {

    @Serializable
    data class SubscriptionResponse(
        val code: Int? = null,
        val message: String? = null,
        val data: List<Subscription> = emptyList()
    )

    @Serializable
    data class Subscription(
        val id: Long? = null,
        @SerialName("user_id") val userId: Long? = null,
        @SerialName("group_id") val groupId: Long? = null,
        @SerialName("starts_at") val startsAt: String? = null,
        @SerialName("expires_at") val expiresAt: String? = null,
        val status: String? = null,
        @SerialName("daily_window_start") val dailyWindowStart: String? = null,
        @SerialName("weekly_window_start") val weeklyWindowStart: String? = null,
        @SerialName("monthly_window_start") val monthlyWindowStart: String? = null,
        @SerialName("daily_usage_usd") val dailyUsageUsd: Double = 0.0,
        @SerialName("weekly_usage_usd") val weeklyUsageUsd: Double = 0.0,
        @SerialName("monthly_usage_usd") val monthlyUsageUsd: Double = 0.0,
        val group: Group? = null
    )

    @Serializable
    data class Group(
        val id: Long? = null,
        val name: String? = null,
        val description: String? = null,
        val platform: String? = null,
        val status: String? = null,
        @SerialName("subscription_type") val subscriptionType: String? = null,
        @SerialName("daily_limit_usd") val dailyLimitUsd: Double = 0.0,
        @SerialName("weekly_limit_usd") val weeklyLimitUsd: Double = 0.0,
        @SerialName("monthly_limit_usd") val monthlyLimitUsd: Double = 0.0
    )
}
