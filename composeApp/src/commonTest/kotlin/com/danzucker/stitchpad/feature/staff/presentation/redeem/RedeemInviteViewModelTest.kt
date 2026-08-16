package com.danzucker.stitchpad.feature.staff.presentation.redeem

import app.cash.turbine.test
import com.danzucker.stitchpad.feature.auth.domain.NoOpRemoteSyncGate
import com.danzucker.stitchpad.core.data.staff.FakeInviteRedemptionRepository
import com.danzucker.stitchpad.core.data.staff.FakeStaffMembershipPrefsStore
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.staff.StaffError
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SignOutUseCase
import com.danzucker.stitchpad.feature.notification.push.PushTokenRegistrar
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RedeemInviteViewModelTest {

    private lateinit var repo: FakeInviteRedemptionRepository
    private lateinit var prefs: FakeStaffMembershipPrefsStore
    private lateinit var deepLink: PendingDeepLinkHolder
    private lateinit var authRepo: FakeAuthRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = FakeInviteRedemptionRepository()
        prefs = FakeStaffMembershipPrefsStore()
        deepLink = PendingDeepLinkHolder()
        authRepo = FakeAuthRepository()
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun buildViewModel() = RedeemInviteViewModel(
        inviteRedemptionRepository = repo,
        staffMembershipPrefs = prefs,
        signOutUseCase = SignOutUseCase(authRepo, NoOpRegistrar(), PendingDeepLinkHolder(), FakeStaffMembershipPrefsStore(), NoOpRemoteSyncGate()),
        pendingDeepLink = deepLink,
    )

    private class NoOpRegistrar : PushTokenRegistrar {
        override suspend fun registerForUser(userId: String) {}
        override suspend fun register(userId: String, token: String) {}
        override suspend fun unregisterForUser(userId: String) {}
        override suspend fun invalidateToken() {}
    }

    @Test
    fun a_deep_link_code_prefills_the_normalised_state() {
        deepLink.setJoinWorkshop("k7qp-3rm9")
        val vm = buildViewModel()
        assertEquals("K7QP3RM9", vm.state.value.code)
        assertEquals(true, vm.state.value.prefilled)
    }

    @Test
    fun code_change_normalises_uppercase_and_caps_length() {
        val vm = buildViewModel()
        vm.onAction(RedeemInviteAction.OnCodeChange("k7qp-3rm9-extra"))
        assertEquals("K7QP3RM9", vm.state.value.code)
    }

    @Test
    fun a_short_code_shows_an_inline_error_and_does_not_redeem() = runTest {
        val vm = buildViewModel()
        vm.onAction(RedeemInviteAction.OnCodeChange("K7QP"))
        vm.onAction(RedeemInviteAction.OnJoinClick)
        assertNotNull(vm.state.value.codeError)
        assertNull(repo.lastCode)
    }

    @Test
    fun a_successful_redeem_persists_the_workshop_uid_and_navigates_to_pending() = runTest {
        val vm = buildViewModel()
        vm.onAction(RedeemInviteAction.OnCodeChange("K7QP3RM9"))
        vm.events.test {
            vm.onAction(RedeemInviteAction.OnJoinClick)
            val event = awaitItem()
            assertIs<RedeemInviteEvent.NavigateToPending>(event)
            assertEquals("Ade Fashions", event.workshopName)
        }
        assertEquals("K7QP3RM9", repo.lastCode)
        assertEquals("owner-9", prefs.workshopUid.value)
        assertEquals("Ade Fashions", prefs.workshopName.value)
        assertEquals(false, vm.state.value.isLoading)
    }

    @Test
    fun successfulRedeemDoesNotSuspendWithoutAnAttachedEventCollector() = runTest {
        val vm = buildViewModel()
        vm.onAction(RedeemInviteAction.OnCodeChange("K7QP3RM9"))

        vm.onAction(RedeemInviteAction.OnJoinClick)
        advanceUntilIdle()

        assertEquals("owner-9", prefs.workshopUid.value)
        assertEquals(false, vm.state.value.isLoading)
    }

    @Test
    fun an_invite_error_shows_an_inline_code_error() = runTest {
        repo.result = Result.Error(StaffError.INVITE_EXPIRED)
        val vm = buildViewModel()
        vm.onAction(RedeemInviteAction.OnCodeChange("K7QP3RM9"))
        vm.onAction(RedeemInviteAction.OnJoinClick)
        assertNotNull(vm.state.value.codeError)
        assertNull(prefs.workshopUid.value)
    }

    @Test
    fun a_network_error_is_surfaced_as_a_snackbar_not_an_inline_error() = runTest {
        repo.result = Result.Error(StaffError.NETWORK)
        val vm = buildViewModel()
        vm.onAction(RedeemInviteAction.OnCodeChange("K7QP3RM9"))
        vm.events.test {
            vm.onAction(RedeemInviteAction.OnJoinClick)
            assertIs<RedeemInviteEvent.ShowError>(awaitItem())
        }
        assertNull(vm.state.value.codeError)
    }

    @Test
    fun signing_out_clears_the_stored_uid_and_emits_signed_out() = runTest {
        prefs.setWorkshop("owner-9", null)
        val vm = buildViewModel()
        vm.events.test {
            vm.onAction(RedeemInviteAction.OnSignOutClick)
            assertIs<RedeemInviteEvent.SignedOut>(awaitItem())
        }
        assertEquals(1, prefs.clearCount)
    }

    @Test
    fun a_failed_sign_out_does_not_navigate_or_clear_the_stored_uid() = runTest {
        authRepo.signOutError = com.danzucker.stitchpad.feature.auth.domain.AuthError.UNKNOWN
        prefs.setWorkshop("owner-9", null)
        val vm = buildViewModel()
        vm.events.test {
            vm.onAction(RedeemInviteAction.OnSignOutClick)
            expectNoEvents()
        }
        assertEquals(0, prefs.clearCount)
        assertEquals("owner-9", prefs.workshopUid.value)
    }
}
