package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.security.EncryptedPrefsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlinx.coroutines.test.runTest
import java.time.LocalDate

class ChatGPTPlusRepositoryImplTest {

    private fun createRepo(credential: Credential.ChatGPTPlusCredential?): ChatGPTPlusRepositoryImpl {
        val prefsManager = mock(EncryptedPrefsManager::class.java)
        `when`(prefsManager.loadCredential(AiService.CHATGPT_PLUS)).thenReturn(credential)
        return ChatGPTPlusRepositoryImpl(prefsManager)
    }

    @Test
    fun `fetchQuota returns success with remaining days`() = runTest {
        val today = LocalDate.now()
        val lastRenewal = today.minusDays(14) // expect nextRenewal = today+16, 16 days remaining
        val credential = Credential.ChatGPTPlusCredential(
            planName = "Plus",
            renewalDate = lastRenewal.toString(),
            billingPeriod = "Monthly"
        )
        val repo = createRepo(credential)

        val result = repo.fetchQuota()

        assertTrue(result is Result.Success)
        val quotaInfo = (result as Result.Success).value
        assertEquals(AiService.CHATGPT_PLUS, quotaInfo.service)
        assertEquals("Plus", quotaInfo.tier)
        assertEquals(1, quotaInfo.windows.size)
        val window = quotaInfo.windows.first()
        val remainingUtil = window.utilization
        assertTrue(remainingUtil in 0.4..0.6)
        assertEquals(16, window.remainingDays)
        assertEquals(30, window.periodDays)
    }

    @Test
    fun `fetchQuota returns CredentialNotFound when renewal date is blank`() = runTest {
        val credential = Credential.ChatGPTPlusCredential(
            planName = "Pro",
            renewalDate = "",
            billingPeriod = "Monthly"
        )
        val repo = createRepo(credential)

        val result = repo.fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.CredentialNotFound)
    }

    @Test
    fun `fetchQuota returns CredentialNotFound when credential is null`() = runTest {
        val repo = createRepo(null)

        val result = repo.fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.CredentialNotFound)
    }

    @Test
    fun `fetchQuota returns ParseError for invalid date`() = runTest {
        val credential = Credential.ChatGPTPlusCredential(
            planName = "Plus",
            renewalDate = "not-a-date",
            billingPeriod = "Monthly"
        )
        val repo = createRepo(credential)

        val result = repo.fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.ParseError)
    }

    @Test
    fun `fetchQuota calculates utilization correctly for yearly billing`() = runTest {
        val today = LocalDate.now()
        val lastRenewal = today.minusDays(65) // expect nextRenewal = today+300, 300 days remaining
        val credential = Credential.ChatGPTPlusCredential(
            planName = "Plus",
            renewalDate = lastRenewal.toString(),
            billingPeriod = "Yearly"
        )
        val repo = createRepo(credential)

        val result = repo.fetchQuota()

        assertTrue(result is Result.Success)
        val quotaInfo = (result as Result.Success).value
        val util = quotaInfo.windows.first().utilization
        val expectedUtil = 1.0 - 300.0 / 365.0
        assertEquals(expectedUtil, util, 0.01)
        assertEquals(300, quotaInfo.windows.first().remainingDays)
        assertEquals(365, quotaInfo.windows.first().periodDays)
    }

    @Test
    fun `fetchQuota returns full utilization when expired`() = runTest {
        val today = LocalDate.now()
        val lastRenewal = today.minusDays(35) // expect nextRenewal = today-5 → expired
        val credential = Credential.ChatGPTPlusCredential(
            planName = "Plus",
            renewalDate = lastRenewal.toString(),
            billingPeriod = "Monthly"
        )
        val repo = createRepo(credential)

        val result = repo.fetchQuota()

        assertTrue(result is Result.Success)
        val quotaInfo = (result as Result.Success).value
        assertEquals(1.0, quotaInfo.windows.first().utilization, 0.001)
        assertEquals(0, quotaInfo.windows.first().remainingDays)
    }

    @Test
    fun `refresh returns WARNING when remaining days are low`() = runTest {
        val today = LocalDate.now()
        val lastRenewal = today.minusDays(26) // expect nextRenewal = today+4, 4 days remaining
        val credential = Credential.ChatGPTPlusCredential(
            planName = "Plus",
            renewalDate = lastRenewal.toString(),
            billingPeriod = "Monthly"
        )
        val repo = createRepo(credential)

        val quota = repo.refresh()

        assertEquals(com.codexbar.android.core.domain.model.ProviderStatus.WARNING, quota.status)
    }

    @Test
    fun `refresh returns ERROR when expired`() = runTest {
        val today = LocalDate.now()
        val lastRenewal = today.minusDays(31) // expect nextRenewal = today-1 → expired
        val credential = Credential.ChatGPTPlusCredential(
            planName = "Plus",
            renewalDate = lastRenewal.toString(),
            billingPeriod = "Monthly"
        )
        val repo = createRepo(credential)

        val quota = repo.refresh()

        assertEquals(com.codexbar.android.core.domain.model.ProviderStatus.ERROR, quota.status)
    }
}
