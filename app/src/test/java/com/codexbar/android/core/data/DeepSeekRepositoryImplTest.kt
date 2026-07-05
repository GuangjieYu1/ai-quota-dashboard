package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.deepseek.DeepSeekApiService
import com.codexbar.android.core.network.deepseek.DeepSeekDto
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
import retrofit2.Response
import retrofit2.Retrofit

class DeepSeekRepositoryImplTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: DeepSeekApiService
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var repository: DeepSeekRepositoryImpl
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    private val testCredential = Credential.DeepSeekCredential(accessToken = "sk-test-key")

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
            .create(DeepSeekApiService::class.java)

        prefsManager = mock(EncryptedPrefsManager::class.java)
        `when`(prefsManager.loadCredential(AiService.DEEPSEEK)).thenReturn(testCredential)

        repository = DeepSeekRepositoryImpl(apiService, prefsManager)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `fetchQuota returns success with balance data`() = runTest {
        val responseJson = """
        {
            "is_available": true,
            "balance_infos": [
                {
                    "total_balance": "12.34",
                    "granted_balance": "0.00",
                    "topped_up_balance": "12.34",
                    "currency": "CNY"
                }
            ]
        }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quotaInfo = (result as Result.Success).value
        assertEquals(AiService.DEEPSEEK, quotaInfo.service)
        assertEquals("Active", quotaInfo.tier)
        assertEquals(12.34, quotaInfo.extraUsage!!.monthlyLimit, 0.001)
        assertEquals(0.0, quotaInfo.extraUsage!!.usedCredits, 0.001)
    }

    @Test
    fun `fetchQuota returns success with initialTotal`() = runTest {
        val responseJson = """
        {
            "is_available": true,
            "balance_infos": [
                {
                    "total_balance": "12.34",
                    "granted_balance": "0.00",
                    "topped_up_balance": "12.34",
                    "currency": "CNY"
                }
            ]
        }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val credentialWithInitial = Credential.DeepSeekCredential(
            accessToken = "sk-test-key",
            initialTotal = 50.0
        )
        `when`(prefsManager.loadCredential(AiService.DEEPSEEK)).thenReturn(credentialWithInitial)

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quotaInfo = (result as Result.Success).value
        assertEquals(50.0, quotaInfo.extraUsage!!.monthlyLimit, 0.001)
        assertEquals(37.66, quotaInfo.extraUsage!!.usedCredits, 0.001)
        assertEquals(1.0 - 12.34 / 50.0, quotaInfo.extraUsage!!.utilization, 0.001)
    }

    @Test
    fun `fetchQuota maps user summary monthly costs and wallet balance`() = runTest {
        val summaryApi = object : DeepSeekApiService {
            override suspend fun getBalance(
                authorization: String,
                accept: String
            ): Response<DeepSeekDto.BalanceResponse> {
                error("Balance API should not be called when session cookie exists")
            }

            override suspend fun getUserSummary(
                cookie: String,
                accept: String
            ): Response<DeepSeekDto.UserSummaryResponse> {
                return Response.success(
                    DeepSeekDto.UserSummaryResponse(
                        code = 0,
                        data = DeepSeekDto.UserSummaryData(
                            monthlyCosts = json.parseToJsonElement("""{"amount":"12.4","currency":"CNY"}"""),
                            normalWallets = json.parseToJsonElement("""[{"balance":"37.6","currency":"CNY"}]""")
                        )
                    )
                )
            }
        }
        `when`(prefsManager.loadCredential(AiService.DEEPSEEK)).thenReturn(
            Credential.DeepSeekCredential(
                accessToken = "",
                sessionCookie = "token=test"
            )
        )
        val summaryRepository = DeepSeekRepositoryImpl(summaryApi, prefsManager)

        val result = summaryRepository.fetchQuota()

        assertTrue(result is Result.Success)
        val quotaInfo = (result as Result.Success).value
        assertEquals(50.0, quotaInfo.extraUsage!!.monthlyLimit, 0.001)
        assertEquals(12.4, quotaInfo.extraUsage!!.usedCredits, 0.001)
        assertEquals(12.4 / 50.0, quotaInfo.extraUsage!!.utilization, 0.001)
    }

    @Test
    fun `fetchQuota returns AuthError on 401`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError)
        assertTrue((error as AppError.AuthError).isTerminal)
    }

    @Test
    fun `fetchQuota returns CredentialNotFound when no credential saved`() = runTest {
        `when`(prefsManager.loadCredential(AiService.DEEPSEEK)).thenReturn(null)

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.CredentialNotFound)
    }

    @Test
    fun `refresh returns ProviderQuota with OK status`() = runTest {
        val responseJson = """
        {
            "is_available": true,
            "balance_infos": [
                {
                    "total_balance": "12.34",
                    "granted_balance": "0.00",
                    "topped_up_balance": "12.34",
                    "currency": "CNY"
                }
            ]
        }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val credentialWithInitial = Credential.DeepSeekCredential(
            accessToken = "sk-test-key",
            initialTotal = 50.0
        )
        `when`(prefsManager.loadCredential(AiService.DEEPSEEK)).thenReturn(credentialWithInitial)

        val quota = repository.refresh()

        assertEquals("deepseek", quota.id)
        assertEquals("DeepSeek", quota.displayName)
        assertEquals(com.codexbar.android.core.domain.model.ProviderStatus.OK, quota.status)
    }

    @Test
    fun `refresh returns NEEDS_LOGIN on credential not found`() = runTest {
        `when`(prefsManager.loadCredential(AiService.DEEPSEEK)).thenReturn(null)

        val quota = repository.refresh()

        assertEquals(com.codexbar.android.core.domain.model.ProviderStatus.NEEDS_LOGIN, quota.status)
    }
}
