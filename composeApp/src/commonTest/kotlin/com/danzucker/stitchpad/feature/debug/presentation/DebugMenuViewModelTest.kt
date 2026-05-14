package com.danzucker.stitchpad.feature.debug.presentation

import com.danzucker.stitchpad.core.debug.DebugSeeder
import com.danzucker.stitchpad.core.debug.DebugSessionActions
import com.danzucker.stitchpad.core.debug.SeedResult
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DebugMenuViewModelTest {

    private lateinit var seeder: FakeDebugSeeder
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var fakeOnboarding: FakeOnboardingPreferences
    private lateinit var sessionActions: DebugSessionActions

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        seeder = FakeDebugSeeder()
        fakeAuth = FakeAuthRepository().apply {
            currentUser = User(
                id = "test-uid", email = "test@example.com", displayName = "Test",
                businessName = null, phoneNumber = null, whatsappNumber = null,
                avatarColorIndex = 0,
            )
        }
        fakeOnboarding = FakeOnboardingPreferences()
        sessionActions = DebugSessionActions(fakeAuth, fakeOnboarding)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(testAccountsConfigured: Boolean = true): DebugMenuViewModel {
        val vm = DebugMenuViewModel(
            seeder = seeder,
            sessionActions = sessionActions,
            testAccountsConfigured = testAccountsConfigured,
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    @Test
    fun `initial state has testAccountsConfigured propagated`() = runTest {
        val vm = createViewModel(testAccountsConfigured = false)
        val state = vm.state.first()
        assertFalse(state.testAccountsConfigured)
    }

    @Test
    fun `OnSeedActiveWorkshopClick delegates to seeder and emits Snackbar on success`() = runTest {
        val vm = createViewModel()
        seeder.seedActiveWorkshopResult = SeedResult.Success

        vm.onAction(DebugMenuAction.OnSeedActiveWorkshopClick)

        assertEquals(1, seeder.seedActiveWorkshopCalls)
        assertNotNull(vm.state.first().lastResult)
    }

    @Test
    fun `OnSeedBrandNewClick reports failure via Snackbar`() = runTest {
        val vm = createViewModel()
        seeder.seedBrandNewResult = SeedResult.Failure("boom")

        vm.onAction(DebugMenuAction.OnSeedBrandNewClick)

        assertNotNull(vm.state.first().lastResult)
    }

    @Test
    fun `OnResetOnboardingClick clears onboarding flags`() = runTest {
        fakeOnboarding.onboardingSeen = true
        fakeOnboarding.workshopSetupCompleted = true
        val vm = createViewModel()

        vm.onAction(DebugMenuAction.OnResetOnboardingClick)

        assertFalse(fakeOnboarding.onboardingSeen)
        assertFalse(fakeOnboarding.workshopSetupCompleted)
    }

    @Test
    fun `OnSignOutClick on success emits NavigateToLogin event`() = runTest {
        val vm = createViewModel()

        val events = mutableListOf<DebugMenuEvent>()
        backgroundScope.launch(Dispatchers.Main) {
            vm.events.collect { events.add(it) }
        }
        vm.onAction(DebugMenuAction.OnSignOutClick)

        assertTrue(events.any { it is DebugMenuEvent.NavigateToLogin })
    }

    private class FakeDebugSeeder : DebugSeeder {
        var seedBrandNewResult: SeedResult = SeedResult.Success
        var seedActiveWorkshopResult: SeedResult = SeedResult.Success
        var seedAllReconnectResult: SeedResult = SeedResult.Success
        var wipeResult: SeedResult = SeedResult.Success
        var seedBrandNewCalls = 0
        var seedActiveWorkshopCalls = 0

        override suspend fun seedBrandNew(): SeedResult { seedBrandNewCalls++; return seedBrandNewResult }
        override suspend fun seedActiveWorkshop(): SeedResult { seedActiveWorkshopCalls++; return seedActiveWorkshopResult }
        override suspend fun seedAllReconnect(): SeedResult = seedAllReconnectResult
        override suspend fun wipeAllData(): SeedResult = wipeResult
    }
}
