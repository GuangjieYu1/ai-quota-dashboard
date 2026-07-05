package com.codexbar.android.core.network.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexUsageResponseValidatorTest {

    @Test
    fun `validationMessage detects unauthorized detail response`() {
        val message = CodexUsageResponseValidator.validationMessage("""{"detail":"Unauthorized"}""")

        assertTrue(message.startsWith("Unauthorized"))
        assertFalse(CodexUsageResponseValidator.isValidUsageResponse("""{"detail":"Unauthorized"}"""))
    }

    @Test
    fun `validationMessage accepts usage response with rate limit`() {
        val response = """
        {
            "plan_type": "pro",
            "rate_limit": {
                "primary_window": {
                    "used_percent": 45,
                    "limit_window_seconds": 18000
                }
            }
        }
        """.trimIndent()

        assertEquals(
            CodexUsageResponseValidator.VALID_RESPONSE_MESSAGE,
            CodexUsageResponseValidator.validationMessage(response)
        )
        assertTrue(CodexUsageResponseValidator.isValidUsageResponse(response))
    }

    @Test
    fun `validationMessage rejects unrelated json`() {
        assertEquals(
            "Invalid Codex usage response",
            CodexUsageResponseValidator.validationMessage("""{"ok":true}""")
        )
    }
}
