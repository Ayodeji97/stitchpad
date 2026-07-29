package com.danzucker.stitchpad.feature.staff.presentation.pending

import app.cash.turbine.test
import com.danzucker.stitchpad.core.data.staff.FakeInviteRedemptionRepository
import com.danzucker.stitchpad.core.data.staff.FakeStaffMembershipPrefsStore
import com.danzucker.stitchpad.core.domain.session.FakeActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class StaffPendingViewModelTest {

    private lateinit var prefs: FakeStaffMembershipPrefsStore
    private lateinit var authRepo: FakeAuthRepository
    private lateinit var repo: FakeInviteRedemptionRepository

    private fun pending() = WorkshopSession("staff-1", "staff-1", StaffRole.STAFF, MembershipStatus.PENDING)
    private fun active() = WorkshopSession("staff-1", "owner-9", StaffRole.STAFF, MembershipStatus.ACTIVE)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        prefs = FakeStaffMembershipPrefsStore(initial = "owner-9")
        authRepo = FakeAuthRepository()
        repo = FakeInviteRedemptionRepository()
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun buildViewModel(provider: FakeActiveWorkshopProvider) = StaffPendingViewModel(
        workshopName = "Ade Fashions",
        activeWorkshopProvider = provider,
        staffMembershipPrefs = prefs,
        inviteRedemptionRepository = repo,
        signOutUseCase = SignOutUseCase(authRepo, NoOpRegistrar(), PendingDeepLinkHolder(), FakeStaffMembershipPrefsStore()),
    )

    private class NoOpRegistrar : PushTokenRegistrar {
        override suspend fun registerForUser(userId: String) {}
        override suspend fun register(userId: String, token: String) {}
        override suspend fun unregisterForUser(userId: String) {}
        override suspend fun invalidateToken() {}
    }

    @Test
    fun workshop_name_is_exposed_in_state() {
        val vm = buildViewModel(FakeActiveWorkshopProvider(pending()))
        assertEquals("Ade Fashions", vm.state.value.workshopName)
    }

    @Test
    fun approval_navigates_to_home() = runTest {
        val provider = FakeActiveWorkshopProvider(pending())
        val vm = buildViewModel(provider)
        vm.events.test {
            provider.setSession(active())
            assertIs<StaffPendingEvent.NavigateToHome>(awaitItem())
        }
    }

    @Test
    fun owner_declining_after_pending_navigates_back_to_redeem_with_the_declined_flag() = runTest {
        val provider = FakeActiveWorkshopProvider(pending())
        val vm = buildViewModel(provider)
        vm.events.test {
            provider.setSession(WorkshopSession.ownerOfSelf("staff-1"))
            val event = awaitItem()
            assertIs<StaffPendingEvent.NavigateToRedeem>(event)
            assertEquals(true, event.declined)
        }
        assertEquals(1, prefs.clearCount)
    }

    @Test
    fun leaving_cancels_server_side_then_clears_and_navigates_to_redeem() = runTest {
        val vm = buildViewModel(FakeActiveWorkshopProvider(pending()))
        vm.events.test {
            vm.onAction(StaffPendingAction.OnLeaveClick)
            val event = awaitItem()
            assertIs<StaffPendingEvent.NavigateToRedeem>(event)
            assertEquals(false, event.declined)
        }
        assertEquals("owner-9", repo.lastCancelledWorkshopUid)
        assertEquals(1, prefs.clearCount)
    }

    @Test
    fun a_failed_leave_keeps_the_membership_and_shows_an_error() = runTest {
        repo.cancelResult = com.danzucker.stitchpad.core.domain.error.Result.Error(
            com.danzucker.stitchpad.core.domain.staff.StaffError.NETWORK,
        )
        val vm = buildViewModel(FakeActiveWorkshopProvider(pending()))
        vm.events.test {
            vm.onAction(StaffPendingAction.OnLeaveClick)
            assertIs<StaffPendingEvent.ShowError>(awaitItem())
        }
        // Server cancel failed → local state preserved so the user stays pending.
        assertEquals(0, prefs.clearCount)
        assertEquals("owner-9", prefs.workshopUid.value)
    }

    @Test
    fun signing_out_clears_the_stored_uid_and_emits_signed_out() = runTest {
        val vm = buildViewModel(FakeActiveWorkshopProvider(pending()))
        vm.events.test {
            vm.onAction(StaffPendingAction.OnSignOutClick)
            assertIs<StaffPendingEvent.SignedOut>(awaitItem())
        }
        assertEquals(1, prefs.clearCount)
    }

    @Test
    fun a_failed_sign_out_does_not_navigate_or_clear() = runTest {
        authRepo.signOutError = com.danzucker.stitchpad.feature.auth.domain.AuthError.UNKNOWN
        val vm = buildViewModel(FakeActiveWorkshopProvider(pending()))
        vm.events.test {
            vm.onAction(StaffPendingAction.OnSignOutClick)
            expectNoEvents()
        }
        assertEquals(0, prefs.clearCount)
    }
}
