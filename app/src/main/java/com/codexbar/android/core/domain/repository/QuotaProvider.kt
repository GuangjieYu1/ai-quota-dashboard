package com.codexbar.android.core.domain.repository

import com.codexbar.android.core.domain.model.ProviderKind
import com.codexbar.android.core.domain.model.ProviderQuota

interface QuotaProvider {
    val id: String
    val displayName: String
    val kind: ProviderKind

    suspend fun refresh(): ProviderQuota
}
