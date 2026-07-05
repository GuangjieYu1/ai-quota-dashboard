package com.codexbar.android.core.network.codex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object CodexUsageResponseValidator {
    const val VALID_RESPONSE_MESSAGE = "Valid JSON response"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(response: String): CodexDto.UsageResponse? {
        return runCatching {
            json.decodeFromString<CodexDto.UsageResponse>(response)
        }.getOrNull()
    }

    fun hasUsageData(response: CodexDto.UsageResponse): Boolean {
        return response.planType != null || response.rateLimit != null || response.credits != null
    }

    fun validationMessage(response: String): String {
        if (isUnauthorized(response)) {
            return "Unauthorized: paste session JSON from https://chatgpt.com/api/auth/session instead."
        }

        val usageResponse = parse(response) ?: return "Invalid JSON response"
        return if (hasUsageData(usageResponse)) {
            VALID_RESPONSE_MESSAGE
        } else {
            "Invalid Codex usage response"
        }
    }

    fun isValidUsageResponse(response: String): Boolean {
        val usageResponse = parse(response) ?: return false
        return hasUsageData(usageResponse)
    }

    private fun isUnauthorized(response: String): Boolean {
        val trimmed = response.trim()
        if (trimmed.equals("unauthorized", ignoreCase = true)) return true

        val element = runCatching { json.parseToJsonElement(trimmed) }.getOrNull()
        return element?.containsUnauthorized() ?: trimmed.contains("unauthorized", ignoreCase = true)
    }

    private fun JsonElement.containsUnauthorized(): Boolean {
        return when (this) {
            is JsonPrimitive -> jsonPrimitive.contentOrNull?.contains("unauthorized", ignoreCase = true) == true
            is JsonObject -> values.any { it.containsUnauthorized() }
            is JsonArray -> any { it.containsUnauthorized() }
        }
    }
}
