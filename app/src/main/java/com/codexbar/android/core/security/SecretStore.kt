package com.codexbar.android.core.security

interface SecretStore {
    suspend fun getSecret(key: String): String?
    suspend fun setSecret(key: String, value: String)
    suspend fun deleteSecret(key: String)
}
