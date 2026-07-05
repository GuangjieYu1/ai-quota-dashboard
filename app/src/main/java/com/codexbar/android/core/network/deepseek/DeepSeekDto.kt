package com.codexbar.android.core.network.deepseek

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

object DeepSeekDto {

    @Serializable
    data class BalanceResponse(
        @SerialName("is_available") val isAvailable: Boolean = true,
        @SerialName("balance_infos") val balanceInfos: List<BalanceInfo> = emptyList()
    )

    @Serializable
    data class BalanceInfo(
        val currency: String = "CNY",
        @SerialName("total_balance") val totalBalance: String = "0",
        @SerialName("granted_balance") val grantedBalance: String = "0",
        @SerialName("topped_up_balance") val toppedUpBalance: String = "0"
    )

    @Serializable
    data class UserSummaryResponse(
        val code: Int? = null,
        val message: String? = null,
        val data: UserSummaryData? = null
    )

    @Serializable
    data class UserSummaryData(
        @SerialName("monthly_costs") val monthlyCosts: JsonElement? = null,
        @SerialName("normal_wallets") val normalWallets: JsonElement? = null
    )
}
