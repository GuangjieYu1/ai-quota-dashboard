package com.codexbar.android.core.domain.model

enum class AiService(
    val displayName: String,
    val brandColor: Long,
    val baseUrl: String,
    val requiresManualCredentials: Boolean
) {
    CLAUDE(
        displayName = "Claude",
        brandColor = 0xFFD4A574,
        baseUrl = "https://api.anthropic.com/",
        requiresManualCredentials = false
    ),
    CODEX(
        displayName = "Codex",
        brandColor = 0xFF10A37F,
        baseUrl = "https://chatgpt.com/",
        requiresManualCredentials = false
    ),
    GEMINI(
        displayName = "Gemini",
        brandColor = 0xFF4285F4,
        baseUrl = "https://cloudcode-pa.googleapis.com/",
        requiresManualCredentials = true
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        brandColor = 0xFF4F9CF5,
        baseUrl = "https://api.deepseek.com/",
        requiresManualCredentials = true
    ),
    CHATGPT_PLUS(
        displayName = "ChatGPT Plus",
        brandColor = 0xFF10A37F,
        baseUrl = "",
        requiresManualCredentials = false
    ),
    MIMO(
        displayName = "MiMo Token Plan",
        brandColor = 0xFFFF6700,
        baseUrl = "https://platform.xiaomimimo.com/",
        requiresManualCredentials = true
    );

    fun apiHost(): String = when (this) {
        CODEX, CHATGPT_PLUS -> "chatgpt.com"
        MIMO -> "platform.xiaomimimo.com"
        else -> ""
    }

}
