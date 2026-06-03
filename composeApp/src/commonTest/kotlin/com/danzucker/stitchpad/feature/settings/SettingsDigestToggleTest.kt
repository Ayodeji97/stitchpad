package com.danzucker.stitchpad.feature.settings

import app.cash.turbine.test
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
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
    fun toggleOff_optimisticallyDisables_andPersists() = runTest {
        val (vm, repo) = buildSettingsVmForDigest(initialEnabled = true)
        vm.state.test {
            awaitItem() // initial
            vm.onAction(SettingsAction.OnDailyDigestToggle(false))
            assertFalse(awaitItem().dailyDigestEmailEnabled)
            assertEquals(false, repo.lastDigestEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun toggleOff_revertsOnRepositoryError() = runTest {
        val (vm, _) = buildSettingsVmForDigest(
            initialEnabled = true,
            setterResult = Result.Error(DataError.Network.UNKNOWN),
        )
        vm.state.test {
            awaitItem()
            vm.onAction(SettingsAction.OnDailyDigestToggle(false))
            // optimistic false, then reverted to true
            assertFalse(awaitItem().dailyDigestEmailEnabled)
            assertEquals(true, awaitItem().dailyDigestEmailEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun buildSettingsVmForDigest(
    initialEnabled: Boolean = true,
    setterResult: EmptyResult<DataError.Network> = Result.Success(Unit),
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
        digestSetterResult = setterResult
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
    )
    return vm to userRepo
}

// ── Minimal inline fakes ──────────────────────────────────────────────────────

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
