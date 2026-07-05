package com.codexbar.android.core.network.deepseek

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface DeepSeekApiService {

    @GET("user/balance")
    suspend fun getBalance(
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = "application/json"
    ): Response<DeepSeekDto.BalanceResponse>

    @GET("https://platform.deepseek.com/api/v0/users/get_user_summary")
    suspend fun getUserSummary(
        @Header("Cookie") cookie: String,
        @Header("Accept") accept: String = "application/json"
    ): Response<DeepSeekDto.UserSummaryResponse>
}
