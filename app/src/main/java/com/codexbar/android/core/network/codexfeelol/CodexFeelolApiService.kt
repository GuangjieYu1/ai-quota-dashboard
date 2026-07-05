package com.codexbar.android.core.network.codexfeelol

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface CodexFeelolApiService {

    @GET("api/v1/subscriptions")
    suspend fun getSubscriptions(
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = "application/json, text/plain, */*",
        @Header("Referer") referer: String = "https://feea.lol/subscriptions",
        @Query("timezone") timezone: String = "Asia/Shanghai"
    ): Response<CodexFeelolDto.SubscriptionResponse>
}
