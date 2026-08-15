package com.danzucker.stitchpad.feature.settings
import com.danzucker.stitchpad.feature.auth.domain.NoOpRemoteSyncGate
import com.danzucker.stitchpad.core.domain.session.FakeActiveWorkshopProvider
import com.danzucker.stitchpad.core.data.staff.FakeInviteRedemptionRepository

import app.cash.turbine.test
import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.config.FakeAppConfigRepository
import com.danzucker.stitchpad.core.config.FakeCommunityJoinTracker
import com.danzucker.stitchpad.core.config.domain.CommunityBannerDismissal
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
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsAction
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsEvent
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
import kotlin.test.assertEquals

class SettingsHubNavigationTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun accountSecurityClick_emitsNavigateToAccountSecurity() = runTest {
        val vm = buildSettingsVm()
        vm.events.test {
            vm.onAction(SettingsAction.OnAccountSecurityClick)
            assertEquals(SettingsEvent.NavigateToAccountSecurity, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun inviteRewardsClick_emitsNavigateToInviteRewards() = runTest {
        val vm = buildSettingsVm()
        vm.events.test {
            vm.onAction(SettingsAction.OnInviteRewardsClick)
            assertEquals(SettingsEvent.NavigateToInviteRewards, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun helpSupportClick_emitsNavigateToHelpSupport() = runTest {
        val vm = buildSettingsVm()
        vm.events.test {
            vm.onAction(SettingsAction.OnHelpSupportClick)
            assertEquals(SettingsEvent.NavigateToHelpSupport, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun legalAboutClick_emitsNavigateToLegalAbout() = runTest {
        val vm = buildSettingsVm()
        vm.events.test {
            vm.onAction(SettingsAction.OnLegalAboutClick)
            assertEquals(SettingsEvent.NavigateToLegalAbout, awaitItem())
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
        entitlementsProvider = HubNavFakeEntitlementsProvider(),
        customerRepository = FakeCustomerRepository(),
        measurementPreferencesStore = HubNavFakeMeasurementPreferencesStore(),
        themePreferencesStore = HubNavFakeThemePreferencesStore(),
        receiptImagePreferencesStore = HubNavFakeReceiptImagePreferencesStore(),
        smartUsageStore = HubNavFakeSmartUsageStore(),
        smartUsageDocSource = HubNavFakeSmartUsageDocSource(),
        signOutUseCase = SignOutUseCase(authRepo, HubNavNoOpPushTokenRegistrar(), PendingDeepLinkHolder(), FakeStaffMembershipPrefsStore(), NoOpRemoteSyncGate()),
        pushPermissionController = HubNavNoOpPushPermissionController(),
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

private class HubNavNoOpPushTokenRegistrar : PushTokenRegistrar {
    override suspend fun registerForUser(userId: String) {}
    override suspend fun register(userId: String, token: String) {}
    override suspend fun unregisterForUser(userId: String) {}
    override suspend fun invalidateToken() {}
}

private class HubNavNoOpPushPermissionController : PushPermissionController {
    override suspend fun shouldRequest(): Boolean = false
    override suspend fun requestPermission(): Boolean = false
}

private class HubNavFakeEntitlementsProvider : EntitlementsProvider {
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

private class HubNavFakeMeasurementPreferencesStore : MeasurementPreferencesStore {
    override suspend fun getUnit(): MeasurementUnit = MeasurementUnit.INCHES
    override suspend fun setUnit(unit: MeasurementUnit) = Unit
}

private class HubNavFakeThemePreferencesStore : ThemePreferencesStore {
    override fun observeTheme(): Flow<ThemePreference> = flowOf(ThemePreference.SYSTEM)
    override suspend fun getTheme(): ThemePreference = ThemePreference.SYSTEM
    override suspend fun setTheme(theme: ThemePreference) = Unit
}

private class HubNavFakeReceiptImagePreferencesStore(
    initial: ReceiptImageStyle = ReceiptImageStyle.LIGHT,
) : ReceiptImagePreferencesStore {
    private val _flow = MutableStateFlow(initial)
    override fun observeStyle(): Flow<ReceiptImageStyle> = _flow
    override suspend fun getStyle(): ReceiptImageStyle = _flow.value
    override suspend fun setStyle(style: ReceiptImageStyle) {
        _flow.value = style
    }
}

private class HubNavFakeSmartUsageStore : SmartUsageStore {
    private val _flow = MutableStateFlow<Int?>(null)
    override val remainingFreeQuota: StateFlow<Int?> = _flow
    override fun update(remaining: Int?) { _flow.value = remaining }
}

private class HubNavFakeSmartUsageDocSource : SmartUsageDocSource {
    override fun observeSnapshot(userId: String): Flow<SmartUsageSnapshot> =
        flowOf(SmartUsageSnapshot.Empty)
}
