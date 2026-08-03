package com.danzucker.stitchpad.feature.settings.presentation.home

import app.cash.turbine.test
import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.config.FakeAppConfigRepository
import com.danzucker.stitchpad.core.config.FakeCommunityJoinTracker
import com.danzucker.stitchpad.core.config.domain.CommunityBannerDismissal
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.data.staff.FakeInviteRedemptionRepository
import com.danzucker.stitchpad.core.data.staff.FakeStaffMembershipPrefsStore
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImagePreferencesStore
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle
import com.danzucker.stitchpad.core.domain.preferences.ThemePreference
import com.danzucker.stitchpad.core.domain.preferences.ThemePreferencesStore
import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.FakeActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
import com.danzucker.stitchpad.core.domain.staff.StaffError
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageDocSource
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageSnapshot
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageStore
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SignOutUseCase
import com.danzucker.stitchpad.feature.notification.push.PushPermissionController
import com.danzucker.stitchpad.feature.notification.push.PushTokenRegistrar
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import com.danzucker.stitchpad.feature.review.presentation.FakeStoreReviewLauncher
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelStaffTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun activeStaffSessionSetsIsActiveStaffTrue() = runTest {
        val vm = buildVm(session = activeStaffSession())
        vm.state.test {
            var state = awaitItem()
            while (!state.isActiveStaff) state = awaitItem()
            assertTrue(state.isActiveStaff)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun ownerSessionKeepsIsActiveStaffFalse() = runTest {
        val vm = buildVm(session = WorkshopSession.ownerOfSelf("staff-1"))
        vm.state.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertFalse(state.isActiveStaff)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun confirmLeaveOnSuccessCancelsMembershipThenSignsOut() = runTest {
        val authRepo = staffAuthRepo()
        val inviteRepo = FakeInviteRedemptionRepository() // cancel succeeds by default
        val staffPrefs = FakeStaffMembershipPrefsStore(initial = "owner-9")
        val vm = buildVm(
            session = activeStaffSession(),
            authRepo = authRepo,
            inviteRepo = inviteRepo,
            staffPrefs = staffPrefs,
        )

        vm.events.test {
            vm.onAction(SettingsAction.OnConfirmLeaveWorkshop)
            // Reuses the same nav event the Sign out action emits.
            assertEquals(SettingsEvent.NavigateToLoginAfterSignOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // Cancel was attempted server-side against the owner's tree...
        assertEquals("owner-9", inviteRepo.lastCancelledWorkshopUid)
        // ...and only THEN did sign-out run (clears session + staff prefs).
        assertNull(authRepo.currentUser)
        assertTrue(staffPrefs.clearCount > 0)
    }

    @Test
    fun confirmLeaveOnErrorDoesNotSignOutAndSurfacesError() = runTest {
        val authRepo = staffAuthRepo()
        val inviteRepo = FakeInviteRedemptionRepository().apply {
            cancelResult = Result.Error(StaffError.NETWORK)
        }
        val staffPrefs = FakeStaffMembershipPrefsStore(initial = "owner-9")
        val vm = buildVm(
            session = activeStaffSession(),
            authRepo = authRepo,
            inviteRepo = inviteRepo,
            staffPrefs = staffPrefs,
        )

        vm.events.test {
            vm.onAction(SettingsAction.OnConfirmLeaveWorkshop)
            // The failure surfaces as a snackbar, not a nav-away.
            assertIs<SettingsEvent.ShowSnackbar>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals("owner-9", inviteRepo.lastCancelledWorkshopUid)
        // Cancel failed → we must NOT have signed out or cleared staff prefs.
        assertNotNull(authRepo.currentUser)
        assertEquals(0, staffPrefs.clearCount)
        // Loading flag starts false and returns to false after the failed attempt.
        assertFalse(vm.state.value.isLeavingWorkshop)
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun activeStaffSession(): WorkshopSession = WorkshopSession(
    authUid = "staff-1",
    workshopUid = "owner-9",
    role = StaffRole.STAFF,
    membershipStatus = MembershipStatus.ACTIVE,
)

private fun staffAuthRepo(): FakeAuthRepository = FakeAuthRepository().apply {
    currentUser = User(
        id = "staff-1",
        email = "staff@x.com",
        displayName = "Bola",
        businessName = "",
        phoneNumber = null,
        whatsappNumber = null,
        avatarColorIndex = 0,
    )
}

private fun buildVm(
    session: WorkshopSession,
    authRepo: FakeAuthRepository = staffAuthRepo(),
    inviteRepo: FakeInviteRedemptionRepository = FakeInviteRedemptionRepository(),
    staffPrefs: FakeStaffMembershipPrefsStore = FakeStaffMembershipPrefsStore(),
): SettingsViewModel {
    val userRepo = FakeUserRepository().apply {
        userFlow.value = User(
            id = "staff-1",
            email = "staff@x.com",
            displayName = "Bola",
            businessName = "",
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
        )
    }
    val workshopProvider: ActiveWorkshopProvider = FakeActiveWorkshopProvider(initial = session)

    return SettingsViewModel(
        authRepository = authRepo,
        userRepository = userRepo,
        entitlementsProvider = StaffFakeEntitlementsProvider(),
        customerRepository = FakeCustomerRepository(),
        measurementPreferencesStore = StaffFakeMeasurementPreferencesStore(),
        themePreferencesStore = StaffFakeThemePreferencesStore(),
        receiptImagePreferencesStore = StaffFakeReceiptImagePreferencesStore(),
        smartUsageStore = StaffFakeSmartUsageStore(),
        smartUsageDocSource = StaffFakeSmartUsageDocSource(),
        signOutUseCase = SignOutUseCase(
            authRepo,
            StaffNoOpPushTokenRegistrar(),
            PendingDeepLinkHolder(),
            staffPrefs,
        ),
        pushPermissionController = StaffNoOpPushPermissionController(),
        appConfigRepository = FakeAppConfigRepository(),
        communityJoinTracker = FakeCommunityJoinTracker(),
        dismissal = CommunityBannerDismissal(FakeOnboardingPreferences()),
        activeWorkshopProvider = workshopProvider,
        inviteRedemptionRepository = inviteRepo,
        analytics = FakeAnalytics(),
        storeReviewLauncher = FakeStoreReviewLauncher(),
    )
}

// ── Minimal inline fakes ──────────────────────────────────────────────────────

private class StaffNoOpPushTokenRegistrar : PushTokenRegistrar {
    override suspend fun registerForUser(userId: String) {}
    override suspend fun register(userId: String, token: String) {}
    override suspend fun unregisterForUser(userId: String) {}
    override suspend fun invalidateToken() {}
}

private class StaffNoOpPushPermissionController : PushPermissionController {
    override suspend fun shouldRequest(): Boolean = false
    override suspend fun requestPermission(): Boolean = false
}

private class StaffFakeEntitlementsProvider : EntitlementsProvider {
    private val _flow = MutableStateFlow(
        UserEntitlements(
            tier = SubscriptionTier.FREE,
            customerCap = Int.MAX_VALUE,
            smartCoinAllowance = 5,
            isInWelcomeWindow = false,
            welcomeEndsAt = null,
            isWithinWelcomeEndingWarning = false,
            welcomeDaysLeft = null,
            canUseCustomMeasurements = false,
        )
    )
    override val flow: StateFlow<UserEntitlements> = _flow
    override fun current(): UserEntitlements = _flow.value
    override suspend fun awaitHydrated(): UserEntitlements = _flow.value
}

private class StaffFakeMeasurementPreferencesStore : MeasurementPreferencesStore {
    override suspend fun getUnit(): MeasurementUnit = MeasurementUnit.INCHES
    override suspend fun setUnit(unit: MeasurementUnit) = Unit
}

private class StaffFakeThemePreferencesStore : ThemePreferencesStore {
    override fun observeTheme(): Flow<ThemePreference> = flowOf(ThemePreference.SYSTEM)
    override suspend fun getTheme(): ThemePreference = ThemePreference.SYSTEM
    override suspend fun setTheme(theme: ThemePreference) = Unit
}

private class StaffFakeReceiptImagePreferencesStore : ReceiptImagePreferencesStore {
    private val _flow = MutableStateFlow(ReceiptImageStyle.LIGHT)
    override fun observeStyle(): Flow<ReceiptImageStyle> = _flow
    override suspend fun getStyle(): ReceiptImageStyle = _flow.value
    override suspend fun setStyle(style: ReceiptImageStyle) { _flow.value = style }
}

private class StaffFakeSmartUsageStore : SmartUsageStore {
    private val _flow = MutableStateFlow<Int?>(null)
    override val remainingFreeQuota: StateFlow<Int?> = _flow
    override fun update(remaining: Int?) { _flow.value = remaining }
}

private class StaffFakeSmartUsageDocSource : SmartUsageDocSource {
    override fun observeSnapshot(userId: String): Flow<SmartUsageSnapshot> =
        flowOf(SmartUsageSnapshot.Empty)
}
