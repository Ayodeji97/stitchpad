package com.danzucker.stitchpad.feature.settings

import app.cash.turbine.test
import com.danzucker.stitchpad.core.config.FakeAppConfigRepository
import com.danzucker.stitchpad.core.config.FakeCommunityJoinTracker
import com.danzucker.stitchpad.core.config.domain.CommunityBannerDismissal
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
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

class SettingsRedeemGiftTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onRedeemGiftClick_emitsNavigateToRedeemGift() = runTest {
        val vm = createViewModel()
        // Collect state concurrently so stateIn(WhileSubscribed) keeps the upstream
        // flow alive while we fire the action and observe the event.
        vm.state.test {
            awaitItem()
            vm.events.test {
                vm.onAction(SettingsAction.OnRedeemGiftClick)
                assertEquals(SettingsEvent.NavigateToRedeemGift, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createViewModel(): SettingsViewModel {
        val user = User(
            id = "u1",
            email = "u@x.com",
            displayName = "Ada",
            businessName = "Ada Couture",
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
        )
        val authRepo = FakeAuthRepository().apply { currentUser = user }
        val userRepo = FakeUserRepository().apply { userFlow.value = user }
        val prefs = FakeOnboardingPreferences()
        return SettingsViewModel(
            authRepository = authRepo,
            userRepository = userRepo,
            entitlementsProvider = RedeemFakeEntitlementsProvider(),
            customerRepository = FakeCustomerRepository(),
            measurementPreferencesStore = RedeemFakeMeasurementPreferencesStore(),
            themePreferencesStore = RedeemFakeThemePreferencesStore(),
            smartUsageStore = RedeemFakeSmartUsageStore(),
            smartUsageDocSource = RedeemFakeSmartUsageDocSource(),
            signOutUseCase = SignOutUseCase(authRepo, RedeemNoOpPushTokenRegistrar(), PendingDeepLinkHolder()),
            pushPermissionController = RedeemNoOpPushPermissionController(),
            appConfigRepository = FakeAppConfigRepository(),
            communityJoinTracker = FakeCommunityJoinTracker(),
            dismissal = CommunityBannerDismissal(prefs),
        )
    }
}

// ── Minimal inline fakes ──────────────────────────────────────────────────────

private class RedeemNoOpPushTokenRegistrar : PushTokenRegistrar {
    override suspend fun registerForUser(userId: String) {}
    override suspend fun register(userId: String, token: String) {}
    override suspend fun unregisterForUser(userId: String) {}
    override suspend fun invalidateToken() {}
}

private class RedeemNoOpPushPermissionController : PushPermissionController {
    override suspend fun shouldRequest(): Boolean = false
    override suspend fun requestPermission(): Boolean = false
}

private class RedeemFakeEntitlementsProvider : EntitlementsProvider {
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

private class RedeemFakeMeasurementPreferencesStore : MeasurementPreferencesStore {
    override suspend fun getUnit(): MeasurementUnit = MeasurementUnit.INCHES
    override suspend fun setUnit(unit: MeasurementUnit) = Unit
}

private class RedeemFakeThemePreferencesStore : ThemePreferencesStore {
    override fun observeTheme(): Flow<ThemePreference> = flowOf(ThemePreference.SYSTEM)
    override suspend fun getTheme(): ThemePreference = ThemePreference.SYSTEM
    override suspend fun setTheme(theme: ThemePreference) = Unit
}

private class RedeemFakeSmartUsageStore : SmartUsageStore {
    private val _flow = MutableStateFlow<Int?>(null)
    override val remainingFreeQuota: StateFlow<Int?> = _flow
    override fun update(remaining: Int?) { _flow.value = remaining }
}

private class RedeemFakeSmartUsageDocSource : SmartUsageDocSource {
    override fun observeSnapshot(userId: String): Flow<SmartUsageSnapshot> =
        flowOf(SmartUsageSnapshot.Empty)
}
