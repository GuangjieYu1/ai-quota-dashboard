package com.codexbar.android.core.network.mimo

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface MiMoApiService {

    @GET("api/v1/tokenPlan/usage")
    suspend fun getUsage(
        @Header("Cookie") cookie: String,
        @Header("Referer") referer: String = "https://platform.xiaomimimo.com/console/plan-manage",
        @Header("User-Agent") userAgent: String = "CodexBar-Android"
    ): Response<MiMoDto.UsageResponse>

    @GET("api/v1/tokenPlan/detail")
    suspend fun getPlanDetail(
        @Header("Cookie") cookie: String,
        @Header("Referer") referer: String = "https://platform.xiaomimimo.com/console/plan-manage",
        @Header("User-Agent") userAgent: String = "CodexBar-Android"
    ): Response<MiMoDto.PlanDetailResponse>
}
