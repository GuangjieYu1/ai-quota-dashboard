package com.codexbar.android.core.domain.model

sealed class AppError {
    data class NetworkError(val message: String, val cause: Throwable? = null) : AppError()

    data class AuthError(
        val service: AiService,
        val isTerminal: Boolean,
        val message: String = ""
    ) : AppError()

    data object RateLimited : AppError()

    data class ParseError(val message: String, val cause: Throwable? = null) : AppError()

    data class CredentialNotFound(val service: AiService) : AppError()

    data object ServiceUnavailable : AppError()

    data class NeedsLogin(val service: AiService, val message: String = "Needs Login") : AppError()
}
