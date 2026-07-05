package com.codexbar.android.core.domain.model

enum class AiService(
    val displayName: String,
    val brandColor: Long,
    val baseUrl: String,
    val requiresManualCredentials: Boolean,
    val iconLabel: String,
    val homeUrl: String,
    val rechargeUrl: String?
) {
    CLAUDE(
        displayName = "Claude",
        brandColor = 0xFFD4A574,
        baseUrl = "https://api.anthropic.com/",
        requiresManualCredentials = false,
        iconLabel = "C",
        homeUrl = "https://claude.ai/",
        rechargeUrl = "https://claude.ai/settings/billing"
    ),
    CODEX(
        displayName = "Codex",
        brandColor = 0xFF10A37F,
        baseUrl = "https://chatgpt.com/",
        requiresManualCredentials = false,
        iconLabel = "GPT",
        homeUrl = "https://chatgpt.com/",
        rechargeUrl = "https://chatgpt.com/#pricing"
    ),
    GEMINI(
        displayName = "Gemini",
        brandColor = 0xFF4285F4,
        baseUrl = "https://cloudcode-pa.googleapis.com/",
        requiresManualCredentials = true,
        iconLabel = "G",
        homeUrl = "https://gemini.google.com/",
        rechargeUrl = "https://one.google.com/about/google-ai-plans/"
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        brandColor = 0xFF4F9CF5,
        baseUrl = "https://api.deepseek.com/",
        requiresManualCredentials = false,
        iconLabel = "DS",
        homeUrl = "https://platform.deepseek.com/usage",
        rechargeUrl = "https://platform.deepseek.com/top_up"
    ),
    CHATGPT_PLUS(
        displayName = "ChatGPT Plus",
        brandColor = 0xFF10A37F,
        baseUrl = "",
        requiresManualCredentials = false,
        iconLabel = "GPT",
        homeUrl = "https://chatgpt.com/",
        rechargeUrl = "https://chatgpt.com/#pricing"
    ),
    MIMO(
        displayName = "MiMo Token Plan",
        brandColor = 0xFFFF6700,
        baseUrl = "https://platform.xiaomimimo.com/",
        requiresManualCredentials = false,
        iconLabel = "M",
        homeUrl = "https://platform.xiaomimimo.com/",
        rechargeUrl = "https://platform.xiaomimimo.com/"
    );

    fun apiHost(): String = when (this) {
        CODEX, CHATGPT_PLUS -> "chatgpt.com"
        MIMO -> "platform.xiaomimimo.com"
        else -> ""
    }

}
