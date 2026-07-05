package com.codexbar.android.core.domain.model

import java.time.Instant

sealed class Credential {
    abstract val accessToken: String
    abstract val refreshToken: String?

    data class ClaudeCredential(
        override val accessToken: String,
        override val refreshToken: String?,
        val expiresAt: Instant? = null,
        val scopes: String? = null,
        val rateLimitTier: String? = null
    ) : Credential()

    data class CodexCredential(
        override val accessToken: String,
        override val refreshToken: String,
        val accountId: String? = null,
        val manualResponse: String? = null
    ) : Credential()

    data class GeminiCredential(
        override val accessToken: String,
        override val refreshToken: String,
        val expiresAtMs: Long,
        val oauthClientId: String,
        val oauthClientSecret: String
    ) : Credential()

    data class DeepSeekCredential(
        override val accessToken: String,
        override val refreshToken: String? = null,
        val baseUrl: String = "https://api.deepseek.com",
        val initialTotal: Double = 0.0,
        val sessionCookie: String = ""
    ) : Credential()

    data class ChatGPTPlusCredential(
        override val accessToken: String = "",
        override val refreshToken: String? = null,
        val planName: String = "Plus",
        val renewalDate: String = "",
        val billingPeriod: String = "Monthly",
        val notes: String = "",
        val manualSessionResponse: String? = null
    ) : Credential()

    data class MiMoCredential(
        override val accessToken: String = "",
        override val refreshToken: String? = null,
        val backendUrl: String = "",
        val backendToken: String = "",
        val directCookie: String = "",
        val useBackendMode: Boolean = true
    ) : Credential()
}
