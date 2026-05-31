package com.danzucker.stitchpad.feature.auth.presentation.verifyemail

import app.cash.turbine.test
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EmailVerificationViewModelTest {

    private lateinit var authRepository: FakeAuthRepository
    private lateinit var preferences: FakeOnboardingPreferences

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        authRepository = FakeAuthRepository()
        preferences = FakeOnboardingPreferences()
        authRepository.currentUser = User(
            id = "uid",
            email = "tailor@stitchpad.app",
            displayName = "Ade",
            businessName = null,
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = EmailVerificationViewModel(authRepository, preferences)

    @Test
    fun loadsCurrentUserEmailIntoState() {
        val vm = buildViewModel()
        assertEquals("tailor@stitchpad.app", vm.state.value.email)
    }

    @Test
    fun resumedWhenAlreadyVerifiedNavigatesToNext() = runTest {
        authRepository.isEmailVerifiedValue = true
        val vm = buildViewModel()
        vm.events.test {
            vm.onAction(EmailVerificationAction.OnScreenResumed)
            assertIs<EmailVerificationEvent.NavigateToNext>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun manualCheckWhenVerifiedNavigatesToNext() = runTest {
        authRepository.isEmailVerifiedValue = true
        val vm = buildViewModel()
        vm.events.test {
            vm.onAction(EmailVerificationAction.OnCheckVerificationClick)
            assertIs<EmailVerificationEvent.NavigateToNext>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun manualCheckWhenNotVerifiedShowsMessageAndStays() = runTest {
        val vm = buildViewModel()
        vm.events.test {
            vm.onAction(EmailVerificationAction.OnCheckVerificationClick)
            assertIs<EmailVerificationEvent.ShowMessage>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(vm.state.value.isChecking)
    }

    @Test
    fun manualCheckWhenReloadFailsShowsError() = runTest {
        authRepository.shouldReturnError = AuthError.NETWORK_ERROR
        val vm = buildViewModel()
        vm.events.test {
            vm.onAction(EmailVerificationAction.OnCheckVerificationClick)
            assertIs<EmailVerificationEvent.ShowError>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(vm.state.value.isChecking)
    }

    @Test
    fun resendSendsEmailAndStartsCooldown() = runTest {
        val vm = buildViewModel()
        vm.events.test {
            vm.onAction(EmailVerificationAction.OnResendClick)
            assertIs<EmailVerificationEvent.ShowMessage>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, authRepository.emailVerificationSentCount)
        assertEquals(60, vm.state.value.resendCooldownSeconds)
    }

    @Test
    fun resendIgnoredWhileOnCooldown() = runTest {
        val vm = buildViewModel()
        vm.onAction(EmailVerificationAction.OnResendClick)
        runCurrent()
        // Second tap while cooldown is active must be a no-op.
        vm.onAction(EmailVerificationAction.OnResendClick)
        runCurrent()
        assertEquals(1, authRepository.emailVerificationSentCount)
    }

    @Test
    fun resendFailureEmitsShowError() = runTest {
        authRepository.shouldReturnError = AuthError.TOO_MANY_REQUESTS
        val vm = buildViewModel()
        vm.events.test {
            vm.onAction(EmailVerificationAction.OnResendClick)
            assertIs<EmailVerificationEvent.ShowError>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, vm.state.value.resendCooldownSeconds)
    }

    @Test
    fun debugSkipSetsBypassFlagAndNavigatesToNext() = runTest {
        val vm = buildViewModel()
        vm.events.test {
            vm.onAction(EmailVerificationAction.OnDebugSkipClick)
            assertIs<EmailVerificationEvent.NavigateToNext>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(preferences.emailVerificationBypassed)
    }

    @Test
    fun logOutSignsOutAndNavigatesToLogin() = runTest {
        val vm = buildViewModel()
        vm.events.test {
            vm.onAction(EmailVerificationAction.OnLogOutClick)
            assertIs<EmailVerificationEvent.NavigateToLogin>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(authRepository.currentUser)
    }

    @Test
    fun pollingAdvancesOnceVerified() = runTest {
        // Share the scheduler so advanceTimeBy controls the VM's poll delay.
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        authRepository.verifyAfterReloads = 2 // verified on the 2nd reload
        val vm = buildViewModel()
        vm.events.test {
            vm.onAction(EmailVerificationAction.OnScreenResumed) // reload #1 (not verified) + start polling
            advanceTimeBy(4_100L) // past one POLL_INTERVAL_MS
            runCurrent()
            assertIs<EmailVerificationEvent.NavigateToNext>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(authRepository.reloadCount >= 2)
    }

    @Test
    fun pausingStopsPolling() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = buildViewModel()
        vm.onAction(EmailVerificationAction.OnScreenResumed) // reload #1 + start polling
        runCurrent()
        val reloadsAfterResume = authRepository.reloadCount
        vm.onAction(EmailVerificationAction.OnScreenPaused) // cancel polling
        advanceTimeBy(12_100L) // 3 poll intervals would have elapsed
        runCurrent()
        assertEquals(reloadsAfterResume, authRepository.reloadCount)
    }
}
