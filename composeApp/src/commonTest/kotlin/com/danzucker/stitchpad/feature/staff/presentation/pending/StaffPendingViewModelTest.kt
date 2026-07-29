package com.danzucker.stitchpad.feature.staff.presentation.pending

import app.cash.turbine.test
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

    private fun pending() = WorkshopSession("staff-1", "staff-1", StaffRole.STAFF, MembershipStatus.PENDING)
    private fun active() = WorkshopSession("staff-1", "owner-9", StaffRole.STAFF, MembershipStatus.ACTIVE)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        prefs = FakeStaffMembershipPrefsStore(initial = "owner-9")
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun buildViewModel(provider: FakeActiveWorkshopProvider) = StaffPendingViewModel(
        workshopName = "Ade Fashions",
        activeWorkshopProvider = provider,
        staffMembershipPrefs = prefs,
        signOutUseCase = SignOutUseCase(FakeAuthRepository(), NoOpRegistrar(), PendingDeepLinkHolder()),
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
    }

    @Test
    fun leaving_clears_the_stored_uid_and_navigates_to_redeem_without_the_declined_flag() = runTest {
        val vm = buildViewModel(FakeActiveWorkshopProvider(pending()))
        vm.events.test {
            vm.onAction(StaffPendingAction.OnLeaveClick)
            val event = awaitItem()
            assertIs<StaffPendingEvent.NavigateToRedeem>(event)
            assertEquals(false, event.declined)
        }
        assertEquals(1, prefs.clearCount)
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
}
