package com.danzucker.stitchpad.feature.settings
import com.danzucker.stitchpad.feature.auth.domain.NoOpRemoteSyncGate
import com.danzucker.stitchpad.core.domain.session.FakeActiveWorkshopProvider
import com.danzucker.stitchpad.core.data.staff.FakeInviteRedemptionRepository

import app.cash.turbine.test
import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.config.FakeAppConfigRepository
import com.danzucker.stitchpad.core.config.FakeCommunityJoinTracker
import com.danzucker.stitchpad.core.config.domain.CommunityBannerDismissal
import com.danzucker.stitchpad.core.config.domain.model.AppConfig
import com.danzucker.stitchpad.core.config.domain.repository.AppConfigRepository
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.data.staff.FakeStaffMembershipPrefsStore
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImagePreferencesStore
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle
import com.danzucker.stitchpad.core.domain.preferences.ThemePreference
import com.danzucker.stitchpad.core.domain.preferences.ThemePreferencesStore
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageDocSource
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageSnapshot
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageStore
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SignOutUseCase
import com.danzucker.stitchpad.feature.notification.push.PushPermissionController
import com.danzucker.stitchpad.feature.notification.push.PushTokenRegistrar
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import com.danzucker.stitchpad.feature.review.presentation.FakeStoreReviewLauncher
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsViewModel
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsHubFlagTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun settingsHubEnabled_false_byDefault() = runTest {
        val vm = buildSettingsVm(appConfig = FakeAppConfigRepository())
        vm.state.test {
            // Skip the stateIn(initialValue = SettingsState()) placeholder and
            // assert on the repository-backed loaded state, so this verifies the
            // flag is actually wired through buildState (not just the default).
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertFalse(state.settingsHubEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun settingsHubEnabled_true_whenRemoteFlagOn() = runTest {
        val config = FakeAppConfigRepository(AppConfig.Disabled.copy(settingsHubEnabled = true))
        val vm = buildSettingsVm(appConfig = config)
        vm.state.test {
            // Skip the stateIn placeholder (settingsHubEnabled = false) and assert
            // on the loaded state, which reflects the remote flag from buildState.
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertTrue(state.settingsHubEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun settingsHubEnabled_staysOn_whenConfigReplaysDisabledSentinel() = runTest {
        val config = FakeAppConfigRepository(AppConfig.Disabled.copy(settingsHubEnabled = true))
        val vm = buildSettingsVm(appConfig = config)
        vm.state.test {
            var state = awaitItem()
            while (!state.settingsHubEnabled) state = awaitItem()
            // The repo re-emits the Disabled sentinel on every resubscribe / read
            // error; the whole-landing layout flag must NOT flash back to false.
            config.emit(AppConfig.Disabled)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun settingsHubEnabled_turnsOff_whenClearedByRealConfig() = runTest {
        val config = FakeAppConfigRepository(AppConfig.Disabled.copy(settingsHubEnabled = true))
        val vm = buildSettingsVm(appConfig = config)
        vm.state.test {
            var state = awaitItem()
            while (!state.settingsHubEnabled) state = awaitItem()
            // A real config (a distinct instance, not the sentinel) with the flag
            // off is the remote kill switch and must still apply.
            config.emit(AppConfig.Disabled.copy(settingsHubEnabled = false))
            while (state.settingsHubEnabled) state = awaitItem()
            assertFalse(state.settingsHubEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun buildSettingsVm(
    appConfig: AppConfigRepository = FakeAppConfigRepository(),
): SettingsViewModel {
    val authRepo = FakeAuthRepository().apply {
        currentUser = User(
            id = "u1",
            email = "u@x.com",
            displayName = "Ada",
            businessName = "Ada Couture",
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
        )
    }

    val userRepo = FakeUserRepository().apply {
        userFlow.value = User(
            id = "u1",
            email = "u@x.com",
            displayName = "Ada",
            businessName = "Ada Couture",
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
        )
    }

    return SettingsViewModel(
        authRepository = authRepo,
        userRepository = userRepo,
        entitlementsProvider = HubFlagFakeEntitlementsProvider(),
        customerRepository = FakeCustomerRepository(),
        measurementPreferencesStore = HubFlagFakeMeasurementPreferencesStore(),
        themePreferencesStore = HubFlagFakeThemePreferencesStore(),
        receiptImagePreferencesStore = HubFlagFakeReceiptImagePreferencesStore(),
        smartUsageStore = HubFlagFakeSmartUsageStore(),
        smartUsageDocSource = HubFlagFakeSmartUsageDocSource(),
        signOutUseCase = SignOutUseCase(authRepo, HubFlagNoOpPushTokenRegistrar(), PendingDeepLinkHolder(), FakeStaffMembershipPrefsStore(), NoOpRemoteSyncGate()),
        pushPermissionController = HubFlagNoOpPushPermissionController(),
        appConfigRepository = appConfig,
        communityJoinTracker = FakeCommunityJoinTracker(),
        dismissal = CommunityBannerDismissal(FakeOnboardingPreferences()),
        activeWorkshopProvider = FakeActiveWorkshopProvider(),
        inviteRedemptionRepository = FakeInviteRedemptionRepository(),
        analytics = FakeAnalytics(),
        storeReviewLauncher = FakeStoreReviewLauncher(),
    )
}

// ── Minimal inline fakes ──────────────────────────────────────────────────────

private class HubFlagNoOpPushTokenRegistrar : PushTokenRegistrar {
    override suspend fun registerForUser(userId: String) {}
    override suspend fun register(userId: String, token: String) {}
    override suspend fun unregisterForUser(userId: String) {}
    override suspend fun invalidateToken() {}
}

private class HubFlagNoOpPushPermissionController : PushPermissionController {
    override suspend fun shouldRequest(): Boolean = false
    override suspend fun requestPermission(): Boolean = false
}

private class HubFlagFakeEntitlementsProvider : EntitlementsProvider {
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

private class HubFlagFakeMeasurementPreferencesStore : MeasurementPreferencesStore {
    override suspend fun getUnit(): MeasurementUnit = MeasurementUnit.INCHES
    override suspend fun setUnit(unit: MeasurementUnit) = Unit
}

private class HubFlagFakeThemePreferencesStore : ThemePreferencesStore {
    override fun observeTheme(): Flow<ThemePreference> = flowOf(ThemePreference.SYSTEM)
    override suspend fun getTheme(): ThemePreference = ThemePreference.SYSTEM
    override suspend fun setTheme(theme: ThemePreference) = Unit
}

private class HubFlagFakeReceiptImagePreferencesStore(
    initial: ReceiptImageStyle = ReceiptImageStyle.LIGHT,
) : ReceiptImagePreferencesStore {
    private val _flow = MutableStateFlow(initial)
    override fun observeStyle(): Flow<ReceiptImageStyle> = _flow
    override suspend fun getStyle(): ReceiptImageStyle = _flow.value
    override suspend fun setStyle(style: ReceiptImageStyle) {
        _flow.value = style
    }
}

private class HubFlagFakeSmartUsageStore : SmartUsageStore {
    private val _flow = MutableStateFlow<Int?>(null)
    override val remainingFreeQuota: StateFlow<Int?> = _flow
    override fun update(remaining: Int?) { _flow.value = remaining }
}

private class HubFlagFakeSmartUsageDocSource : SmartUsageDocSource {
    override fun observeSnapshot(userId: String): Flow<SmartUsageSnapshot> =
        flowOf(SmartUsageSnapshot.Empty)
}
