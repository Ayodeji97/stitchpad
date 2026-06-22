package com.danzucker.stitchpad.feature.settings

import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder

import app.cash.turbine.test
import com.danzucker.stitchpad.core.config.FakeAppConfigRepository
import com.danzucker.stitchpad.core.config.FakeCommunityJoinTracker
import com.danzucker.stitchpad.core.config.domain.CommunityBannerDismissal
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.domain.preferences.ThemePreference
import com.danzucker.stitchpad.core.domain.preferences.ThemePreferencesStore
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageDocSource
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageSnapshot
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageStore
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SignOutUseCase
import com.danzucker.stitchpad.feature.notification.push.PushPermissionController
import com.danzucker.stitchpad.feature.notification.push.PushTokenRegistrar
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsAction
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsViewModel
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

class SettingsDigestToggleTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun toggleOff_snapshotDriven_disablesAndPersists() = runTest {
        val (vm, repo) = buildSettingsVmForDigest(initialEnabled = true)
        vm.state.test {
            awaitItem() // drain the settled initial state
            vm.onAction(SettingsAction.OnDailyDigestToggle(false))
            assertFalse(awaitItem().dailyDigestEmailEnabled)
            assertEquals(false, repo.lastDigestEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun toggleOn_reflectsInStateAndPersists() = runTest {
        val (vm, repo) = buildSettingsVmForDigest(initialEnabled = false)
        vm.state.test {
            awaitItem() // drain the settled initial state
            vm.onAction(SettingsAction.OnDailyDigestToggle(true))
            assertTrue(awaitItem().dailyDigestEmailEnabled)
            assertEquals(true, repo.lastDigestEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

class SettingsPushToggleTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun enablePushToggle_whenPermissionMissing_callsRequestPermission() = runTest {
        val fakePermissionController = RecordingPushPermissionController(shouldRequestResult = true)
        val (vm, _) = buildSettingsVmForDigest(
            initialEnabled = false,
            pushPermissionController = fakePermissionController,
        )
        vm.state.test {
            awaitItem() // settle initial state
            vm.onAction(SettingsAction.OnDailyPushToggle(true))
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(fakePermissionController.requestPermissionCalled, "requestPermission() should be called when permission is missing")
    }

    @Test
    fun enablePushToggle_whenPermissionAlreadyGranted_doesNotCallRequestPermission() = runTest {
        val fakePermissionController = RecordingPushPermissionController(shouldRequestResult = false)
        val (vm, _) = buildSettingsVmForDigest(
            initialEnabled = false,
            pushPermissionController = fakePermissionController,
        )
        vm.state.test {
            awaitItem()
            vm.onAction(SettingsAction.OnDailyPushToggle(true))
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(fakePermissionController.requestPermissionCalled, "requestPermission() should NOT be called when permission is already granted")
    }

    @Test
    fun disablePushToggle_whenPermissionMissing_doesNotCallRequestPermission() = runTest {
        val fakePermissionController = RecordingPushPermissionController(shouldRequestResult = true)
        val (vm, _) = buildSettingsVmForDigest(
            initialEnabled = true,
            pushPermissionController = fakePermissionController,
        )
        vm.state.test {
            awaitItem()
            vm.onAction(SettingsAction.OnDailyPushToggle(false))
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(fakePermissionController.requestPermissionCalled, "requestPermission() should NOT be called when disabling the toggle")
    }

    @Test
    fun pushReminderSupported_isTrue_onAllPlatforms() = runTest {
        val (vm, _) = buildSettingsVmForDigest()
        vm.state.test {
            assertTrue(awaitItem().pushReminderSupported, "Daily push toggle must be visible on both Android and iOS")
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun buildSettingsVmForDigest(
    initialEnabled: Boolean = true,
    pushPermissionController: PushPermissionController = NoOpPushPermissionController(),
): Pair<SettingsViewModel, FakeUserRepository> {
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
            dailyDigestEmailEnabled = initialEnabled,
        )
    }

    val vm = SettingsViewModel(
        authRepository = authRepo,
        userRepository = userRepo,
        entitlementsProvider = FakeEntitlementsProvider(),
        customerRepository = FakeCustomerRepository(),
        measurementPreferencesStore = FakeMeasurementPreferencesStore(),
        themePreferencesStore = FakeThemePreferencesStore(),
        smartUsageStore = FakeSmartUsageStore(),
        smartUsageDocSource = FakeSmartUsageDocSource(),
        signOutUseCase = SignOutUseCase(authRepo, NoOpPushTokenRegistrar(), PendingDeepLinkHolder()),
        pushPermissionController = pushPermissionController,
        appConfigRepository = FakeAppConfigRepository(),
        communityJoinTracker = FakeCommunityJoinTracker(),
        dismissal = CommunityBannerDismissal(FakeOnboardingPreferences()),
    )
    return vm to userRepo
}

// ── Minimal inline fakes ──────────────────────────────────────────────────────

private class NoOpPushTokenRegistrar : PushTokenRegistrar {
    override suspend fun registerForUser(userId: String) {}
    override suspend fun register(userId: String, token: String) {}
    override suspend fun unregisterForUser(userId: String) {}
    override suspend fun invalidateToken() {}
}

/** No-op fake: permission already granted (shouldRequest = false). Default for existing tests. */
private class NoOpPushPermissionController : PushPermissionController {
    override suspend fun shouldRequest(): Boolean = false
    override suspend fun requestPermission(): Boolean = false
}

/** Recording fake: captures whether requestPermission() was called. */
private class RecordingPushPermissionController(
    private val shouldRequestResult: Boolean,
) : PushPermissionController {
    var requestPermissionCalled: Boolean = false
        private set

    override suspend fun shouldRequest(): Boolean = shouldRequestResult
    override suspend fun requestPermission(): Boolean {
        requestPermissionCalled = true
        return true
    }
}

private class FakeEntitlementsProvider : EntitlementsProvider {
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

private class FakeMeasurementPreferencesStore : MeasurementPreferencesStore {
    override suspend fun getUnit(): MeasurementUnit = MeasurementUnit.INCHES
    override suspend fun setUnit(unit: MeasurementUnit) = Unit
}

private class FakeThemePreferencesStore : ThemePreferencesStore {
    override fun observeTheme(): Flow<ThemePreference> = flowOf(ThemePreference.SYSTEM)
    override suspend fun getTheme(): ThemePreference = ThemePreference.SYSTEM
    override suspend fun setTheme(theme: ThemePreference) = Unit
}

private class FakeSmartUsageStore : SmartUsageStore {
    private val _flow = MutableStateFlow<Int?>(null)
    override val remainingFreeQuota: StateFlow<Int?> = _flow
    override fun update(remaining: Int?) { _flow.value = remaining }
}

private class FakeSmartUsageDocSource : SmartUsageDocSource {
    override fun observeSnapshot(userId: String): Flow<SmartUsageSnapshot> =
        flowOf(SmartUsageSnapshot.Empty)
}
