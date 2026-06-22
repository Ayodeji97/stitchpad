package com.danzucker.stitchpad.feature.settings

import app.cash.turbine.test
import com.danzucker.stitchpad.core.config.FakeAppConfigRepository
import com.danzucker.stitchpad.core.config.FakeCommunityJoinTracker
import com.danzucker.stitchpad.core.config.domain.model.AppConfig
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsCommunityTest {

    private lateinit var appConfigRepository: FakeAppConfigRepository
    private lateinit var communityJoinTracker: FakeCommunityJoinTracker
    private lateinit var prefs: FakeOnboardingPreferences

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        appConfigRepository = FakeAppConfigRepository()
        communityJoinTracker = FakeCommunityJoinTracker()
        prefs = FakeOnboardingPreferences()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun communityEnabledWithUrl_showsRow() = runTest {
        appConfigRepository.emit(
            AppConfig(communityEnabled = true, communityInviteUrl = "https://chat.whatsapp.com/X"),
        )
        val vm = createViewModel()
        vm.state.test {
            val state = awaitItem() // advance to the hydrated state
            assertTrue(state.showCommunityRow)
            assertEquals("https://chat.whatsapp.com/X", state.communityUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onCommunityClick_emitsOpenLinkAndTracks() = runTest {
        appConfigRepository.emit(
            AppConfig(communityEnabled = true, communityInviteUrl = "https://chat.whatsapp.com/X"),
        )
        val vm = createViewModel()
        // Collect state concurrently so stateIn(WhileSubscribed) keeps the upstream
        // flow alive while we fire the action and observe the event.
        vm.state.test {
            awaitItem() // settle the hydrated state (communityUrl now populated)
            vm.events.test {
                vm.onAction(SettingsAction.OnCommunityClick)
                assertEquals(SettingsEvent.OpenCommunityLink("https://chat.whatsapp.com/X"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, communityJoinTracker.tapCount)
        // Finding 1: joining from Settings must also persist the banner-dismiss flag.
        assertTrue(prefs.hasDismissedCommunityBanner())
    }

    @Test
    fun communityEnabledWithInvalidUrl_hidesRow() = runTest {
        appConfigRepository.emit(
            // Malformed: no https scheme — predicate must reject this.
            AppConfig(communityEnabled = true, communityInviteUrl = "chat.whatsapp.com/foo"),
        )
        val vm = createViewModel()
        vm.state.test {
            val state = awaitItem()
            assertFalse(state.showCommunityRow)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createViewModel(): SettingsViewModel {
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
            entitlementsProvider = CommunityFakeEntitlementsProvider(),
            customerRepository = FakeCustomerRepository(),
            measurementPreferencesStore = CommunityFakeMeasurementPreferencesStore(),
            themePreferencesStore = CommunityFakeThemePreferencesStore(),
            smartUsageStore = CommunityFakeSmartUsageStore(),
            smartUsageDocSource = CommunityFakeSmartUsageDocSource(),
            signOutUseCase = SignOutUseCase(authRepo, CommunityNoOpPushTokenRegistrar(), PendingDeepLinkHolder()),
            pushPermissionController = CommunityNoOpPushPermissionController(),
            appConfigRepository = appConfigRepository,
            communityJoinTracker = communityJoinTracker,
            onboardingPrefs = prefs,
        )
    }
}

// ── Minimal inline fakes ──────────────────────────────────────────────────────

private class CommunityNoOpPushTokenRegistrar : PushTokenRegistrar {
    override suspend fun registerForUser(userId: String) {}
    override suspend fun register(userId: String, token: String) {}
    override suspend fun unregisterForUser(userId: String) {}
    override suspend fun invalidateToken() {}
}

private class CommunityNoOpPushPermissionController : PushPermissionController {
    override suspend fun shouldRequest(): Boolean = false
    override suspend fun requestPermission(): Boolean = false
}

private class CommunityFakeEntitlementsProvider : EntitlementsProvider {
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

private class CommunityFakeMeasurementPreferencesStore : MeasurementPreferencesStore {
    override suspend fun getUnit(): MeasurementUnit = MeasurementUnit.INCHES
    override suspend fun setUnit(unit: MeasurementUnit) = Unit
}

private class CommunityFakeThemePreferencesStore : ThemePreferencesStore {
    override fun observeTheme(): Flow<ThemePreference> = flowOf(ThemePreference.SYSTEM)
    override suspend fun getTheme(): ThemePreference = ThemePreference.SYSTEM
    override suspend fun setTheme(theme: ThemePreference) = Unit
}

private class CommunityFakeSmartUsageStore : SmartUsageStore {
    private val _flow = MutableStateFlow<Int?>(null)
    override val remainingFreeQuota: StateFlow<Int?> = _flow
    override fun update(remaining: Int?) { _flow.value = remaining }
}

private class CommunityFakeSmartUsageDocSource : SmartUsageDocSource {
    override fun observeSnapshot(userId: String): Flow<SmartUsageSnapshot> =
        flowOf(SmartUsageSnapshot.Empty)
}
