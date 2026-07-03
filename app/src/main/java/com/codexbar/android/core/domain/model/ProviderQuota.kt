package com.codexbar.android.core.domain.model

data class ProviderQuota(
    val id: String,
    val displayName: String,
    val status: ProviderStatus,
    val planName: String? = null,
    val metrics: List<QuotaMetric>,
    val lastUpdatedAt: String? = null,
    val errorMessage: String? = null
)
