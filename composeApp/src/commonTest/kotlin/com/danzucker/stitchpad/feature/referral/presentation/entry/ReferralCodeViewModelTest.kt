package com.danzucker.stitchpad.feature.referral.presentation.entry

import app.cash.turbine.test
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.referral.data.FakeReferralPreferencesStore
import com.danzucker.stitchpad.feature.referral.data.FakeReferralRepository
import com.danzucker.stitchpad.feature.referral.domain.AttributionOutcome
import com.danzucker.stitchpad.feature.referral.domain.ReferralError
import com.danzucker.stitchpad.feature.referral.domain.ReferralSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReferralCodeViewModelTest {

    private val repo = FakeReferralRepository()
    private val prefs = FakeReferralPreferencesStore()

    private fun viewModel() = ReferralCodeViewModel(repo, prefs)

    @BeforeTest
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `apply submits normalized code with manual source and device hash`() = runTest {
        prefs.deviceId = "dev-42"
        val vm = viewModel()
        vm.onAction(ReferralCodeAction.OnCodeChange(" abcd-1234 "))
        vm.onAction(ReferralCodeAction.OnApplyClick)
        assertEquals("ABCD1234", repo.lastCode)
        assertEquals("dev-42", repo.lastDeviceHash)
        assertEquals(ReferralSource.MANUAL, repo.lastSource)
    }

    @Test
    fun `apply success shows message navigates back and marks attributed`() = runTest {
        repo.result = Result.Success(AttributionOutcome(alreadyAttributed = false, marketerId = "m1"))
        val vm = viewModel()
        vm.onAction(ReferralCodeAction.OnCodeChange("ABCD1234"))
        vm.events.test {
            vm.onAction(ReferralCodeAction.OnApplyClick)
            assertTrue(awaitItem() is ReferralCodeEvent.ApplySucceeded)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(prefs.attributed)
    }

    @Test
    fun `already attributed is treated as success`() = runTest {
        repo.result = Result.Success(AttributionOutcome(alreadyAttributed = true, marketerId = "m1"))
        val vm = viewModel()
        vm.onAction(ReferralCodeAction.OnCodeChange("ABCD1234"))
        vm.events.test {
            vm.onAction(ReferralCodeAction.OnApplyClick)
            assertTrue(awaitItem() is ReferralCodeEvent.ApplySucceeded)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(prefs.attributed)
    }

    @Test
    fun `code not found shows message and does not navigate back`() = runTest {
        repo.result = Result.Error(ReferralError.CODE_NOT_FOUND)
        val vm = viewModel()
        vm.onAction(ReferralCodeAction.OnCodeChange("NOPE9999"))
        vm.events.test {
            vm.onAction(ReferralCodeAction.OnApplyClick)
            assertTrue(awaitItem() is ReferralCodeEvent.ShowMessage)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(prefs.attributed)
    }

    @Test
    fun `blank code does not call the repository`() = runTest {
        val vm = viewModel()
        vm.onAction(ReferralCodeAction.OnCodeChange("   "))
        vm.onAction(ReferralCodeAction.OnApplyClick)
        assertEquals(0, repo.callCount)
    }
}
