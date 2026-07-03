package com.codexbar.android.core.domain.model

data class QuotaMetric(
    val label: String,
    val value: String,
    val percentage: Float? = null,
    val resetAt: String? = null,
    val warning: String? = null
)
