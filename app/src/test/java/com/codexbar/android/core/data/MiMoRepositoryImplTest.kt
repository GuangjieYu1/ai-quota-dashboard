package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ProviderStatus
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.mimo.MiMoApiService
import com.codexbar.android.core.network.mimo.MiMoDto
import com.codexbar.android.core.security.EncryptedPrefsManager
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import retrofit2.Retrofit

class MiMoRepositoryImplTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: MiMoApiService
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var repository: MiMoRepositoryImpl
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val client = OkHttpClient.Builder().build()
        val contentType = "application/json".toMediaType()

        apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(MiMoApiService::class.java)

        prefsManager = mock(EncryptedPrefsManager::class.java)
        repository = MiMoRepositoryImpl(apiService, prefsManager, client, json)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // ── Backend Mode ──

    @Test
    fun `backend mode returns success with token data`() = runTest {
        val credential = Credential.MiMoCredential(
            useBackendMode = true,
            backendUrl = mockWebServer.url("/backend").toString(),
            backendToken = ""
        )
        `when`(prefsManager.loadCredential(AiService.MIMO)).thenReturn(credential)

        val responseJson = """{
            "provider": "mimo",
            "planName": "Lite",
            "totalTokens": 1000000,
            "usedTokens": 123456,
            "remainingTokens": 876544
        }""".trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quotaInfo = (result as Result.Success).value
        assertEquals(AiService.MIMO, quotaInfo.service)
        assertEquals("Lite", quotaInfo.tier)
        assertEquals(1000000.0, quotaInfo.extraUsage!!.monthlyLimit, 0.001)
        assertEquals(123456.0, quotaInfo.extraUsage!!.usedCredits, 0.001)
    }

    @Test
    fun `backend mode returns NeedsLogin on 401`() = runTest {
        val credential = Credential.MiMoCredential(
            useBackendMode = true,
            backendUrl = mockWebServer.url("/backend").toString(),
            backendToken = ""
        )
        `when`(prefsManager.loadCredential(AiService.MIMO)).thenReturn(credential)

        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.NeedsLogin)
    }

    @Test
    fun `backend mode returns CredentialNotFound when URL is blank`() = runTest {
        val credential = Credential.MiMoCredential(
            useBackendMode = true,
            backendUrl = "",
            backendToken = ""
        )
        `when`(prefsManager.loadCredential(AiService.MIMO)).thenReturn(credential)

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.CredentialNotFound)
    }

    // ── Direct Cookie Mode ──

    @Test
    fun `direct mode returns success with token data`() = runTest {
        val credential = Credential.MiMoCredential(
            useBackendMode = false,
            directCookie = "api-platform_serviceToken=test123"
        )
        `when`(prefsManager.loadCredential(AiService.MIMO)).thenReturn(credential)

        val usageJson = """{
            "code": 0,
            "data": {
                "usage": {
                    "percent": 0.25,
                    "items": [
                        {"name": "plan_total_token", "used": 500000, "limit": 2000000, "percent": 0.25}
                    ]
                }
            }
        }""".trimIndent()
        val detailJson = """{
            "code": 0,
            "data": {
                "planCode": "lite",
                "planName": "Lite",
                "currentPeriodEnd": "2026-07-22 23:59:59",
                "expired": false
            }
        }""".trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(usageJson))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(detailJson))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quotaInfo = (result as Result.Success).value
        assertEquals(AiService.MIMO, quotaInfo.service)
        assertEquals(2, quotaInfo.windows.size)
        assertEquals("Tokens", quotaInfo.windows[0].label)
        assertEquals("Plan Expires", quotaInfo.windows[1].label)
    }

    @Test
    fun `direct mode returns NeedsLogin when cookie expired`() = runTest {
        val credential = Credential.MiMoCredential(
            useBackendMode = false,
            directCookie = "expired-cookie"
        )
        `when`(prefsManager.loadCredential(AiService.MIMO)).thenReturn(credential)

        val responseJson = """{"code": 401, "message": "Cookie expired"}""".trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.NeedsLogin)
    }

    @Test
    fun `direct mode returns NeedsLogin on HTTP 401`() = runTest {
        val credential = Credential.MiMoCredential(
            useBackendMode = false,
            directCookie = "some-cookie"
        )
        `when`(prefsManager.loadCredential(AiService.MIMO)).thenReturn(credential)

        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.NeedsLogin)
    }

    // ── Common ──

    @Test
    fun `fetchQuota returns CredentialNotFound when no credential`() = runTest {
        `when`(prefsManager.loadCredential(AiService.MIMO)).thenReturn(null)

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.CredentialNotFound)
    }

    @Test
    fun `refresh returns NEEDS_LOGIN when no credential`() = runTest {
        `when`(prefsManager.loadCredential(AiService.MIMO)).thenReturn(null)

        val quota = repository.refresh()

        assertEquals(ProviderStatus.NEEDS_LOGIN, quota.status)
    }

    @Test
    fun `refresh returns OK with formatted numbers`() = runTest {
        val credential = Credential.MiMoCredential(
            useBackendMode = true,
            backendUrl = mockWebServer.url("/backend").toString(),
            backendToken = ""
        )
        `when`(prefsManager.loadCredential(AiService.MIMO)).thenReturn(credential)

        val responseJson = """{
            "provider": "mimo",
            "planName": "Lite",
            "totalTokens": 1000000,
            "usedTokens": 123456,
            "remainingTokens": 876544
        }""".trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val quota = repository.refresh()

        assertEquals(ProviderStatus.OK, quota.status)
        assertEquals("MiMo Token Plan", quota.displayName)
        assertEquals(1, quota.metrics.size)
    }
}
