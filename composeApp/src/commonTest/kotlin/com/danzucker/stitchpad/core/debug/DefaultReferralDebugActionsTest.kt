package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.referral.data.FakeReferralPreferencesStore
import com.danzucker.stitchpad.feature.referral.data.FakeReferralRepository
import com.danzucker.stitchpad.feature.referral.domain.AttributionOutcome
import com.danzucker.stitchpad.feature.referral.domain.ReferralError
import com.danzucker.stitchpad.feature.referral.domain.ReferralSource
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val DAY_MS = 24L * 60 * 60 * 1000

class DefaultReferralDebugActionsTest {

    private lateinit var referralRepository: FakeReferralRepository
    private lateinit var preferences: FakeReferralPreferencesStore
    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var userRepository: FakeUserRepository
    private lateinit var authRepository: FakeAuthRepository

    private val fixedNow = 1_752_000_000_000L

    @BeforeTest
    fun setUp() {
        referralRepository = FakeReferralRepository()
        preferences = FakeReferralPreferencesStore()
        customerRepository = FakeCustomerRepository()
        userRepository = FakeUserRepository()
        authRepository = FakeAuthRepository().apply {
            currentUser = User(
                id = "test-uid",
                email = "test@example.com",
                displayName = "Test Tailor",
                businessName = null,
                phoneNumber = null,
                whatsappNumber = null,
                avatarColorIndex = 3,
            )
        }
    }

    private fun actions() = DefaultReferralDebugActions(
        referralRepository = referralRepository,
        preferences = preferences,
        customerRepository = customerRepository,
        userRepository = userRepository,
        authRepository = authRepository,
    )

    @Test
    fun `attributeWithCode submits the code with the device hash and manual source`() = runTest {
        preferences.deviceId = "dev-xyz"

        val result = actions().attributeWithCode("TESTREF9")

        assertIs<DebugActionResult.Success>(result)
        assertEquals("TESTREF9", referralRepository.lastCode)
        assertEquals("dev-xyz", referralRepository.lastDeviceHash)
        assertEquals(ReferralSource.MANUAL, referralRepository.lastSource)
    }

    @Test
    fun `attributeWithCode returns failure when the server rejects the code`() = runTest {
        referralRepository.result = Result.Error(ReferralError.CODE_NOT_FOUND)

        val result = actions().attributeWithCode("BADCODE")

        assertIs<DebugActionResult.Failure>(result)
    }

    @Test
    fun `attributeWithCode returns failure when not signed in`() = runTest {
        authRepository.currentUser = null

        val result = actions().attributeWithCode("TESTREF9")

        assertIs<DebugActionResult.Failure>(result)
        assertEquals(0, referralRepository.callCount)
    }

    @Test
    fun `seedQualification sets business name and four customers on four distinct days`() = runTest {
        val result = actions().seedQualification(fixedNow)

        assertIs<DebugActionResult.Success>(result)
        assertEquals("QA Test Workshop", userRepository.lastBusinessName)
        val created = customerRepository.customersList
        assertEquals(4, created.size)
        assertEquals(
            listOf(fixedNow, fixedNow + DAY_MS, fixedNow + 2 * DAY_MS, fixedNow + 3 * DAY_MS),
            created.map { it.createdAt },
        )
        // Every seeded customer belongs to the signed-in user.
        assertTrue(created.all { it.userId == "test-uid" })
    }

    @Test
    fun `seedQualification returns failure when a customer write fails`() = runTest {
        customerRepository.shouldReturnError = DataError.Network.NO_INTERNET

        val result = actions().seedQualification(fixedNow)

        assertIs<DebugActionResult.Failure>(result)
    }

    @Test
    fun `resetCaptureState clears the referral preferences`() = runTest {
        preferences.attributed = true

        val result = actions().resetCaptureState()

        assertIs<DebugActionResult.Success>(result)
        assertEquals(1, preferences.resetForDebugCallCount)
        assertEquals(false, preferences.attributed)
    }

    @Test
    fun `attributeWithCode reports already-attributed replays as success`() = runTest {
        referralRepository.result =
            Result.Success(AttributionOutcome(alreadyAttributed = true, marketerId = "mkt_test"))

        val result = actions().attributeWithCode("TESTREF9")

        assertIs<DebugActionResult.Success>(result)
    }
}
