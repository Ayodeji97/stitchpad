package com.danzucker.stitchpad.feature.debug.presentation

import com.danzucker.stitchpad.core.debug.DebugActionResult
import com.danzucker.stitchpad.core.debug.DebugSeeder
import com.danzucker.stitchpad.core.debug.DebugSessionActions
import com.danzucker.stitchpad.core.debug.DigestDebugActions
import com.danzucker.stitchpad.core.debug.DigestSendResult
import com.danzucker.stitchpad.core.debug.FreemiumDebugActions
import com.danzucker.stitchpad.core.debug.SeedResult
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SignOutUseCase
import com.danzucker.stitchpad.feature.notification.push.PushTokenRegistrar
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
        sessionActions = DebugSessionActions(
            authRepository = fakeAuth,
            onboardingPreferences = fakeOnboarding,
            signOutUseCase = SignOutUseCase(fakeAuth, NoOpPushTokenRegistrar()),
        )
    }

    private class NoOpPushTokenRegistrar : PushTokenRegistrar {
        override suspend fun registerForUser(userId: String) {}
        override suspend fun register(userId: String, token: String) {}
        override suspend fun unregisterForUser(userId: String) {}
        override suspend fun invalidateToken() {}
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(
        testAccountsConfigured: Boolean = true,
        digestActions: DigestDebugActions = NoopDigestDebugActions,
    ): DebugMenuViewModel {
        val vm = DebugMenuViewModel(
            seeder = seeder,
            sessionActions = sessionActions,
            freemiumActions = NoopFreemiumDebugActions,
            digestActions = digestActions,
            now = { 0L },
            testAccountsConfigured = testAccountsConfigured,
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    private class FakeDigestDebugActions(
        var result: DigestSendResult = DigestSendResult.Empty,
    ) : DigestDebugActions {
        override suspend fun sendNow(): DigestSendResult = result
    }

    /** Convenience factory for the common "both channels delivered" case. */
    private fun sentBoth() = DigestSendResult.Sent(emailSent = true, pushSent = true)
    private fun sentPushOnly() = DigestSendResult.Sent(emailSent = false, pushSent = true)
    private fun sentEmailOnly() = DigestSendResult.Sent(emailSent = true, pushSent = false)

    // Backward-compat alias used by createViewModel default arg
    private val NoopDigestDebugActions get() = FakeDigestDebugActions()

    private object NoopFreemiumDebugActions : FreemiumDebugActions {
        override suspend fun setTier(tier: SubscriptionTier): DebugActionResult = DebugActionResult.Success
        override suspend fun expireWelcomeWindow(nowMs: Long): DebugActionResult = DebugActionResult.Success
        override suspend fun resetWelcomeWindow(): DebugActionResult = DebugActionResult.Success
        override suspend fun setWelcomeDaysLeft(
            daysLeft: Int,
            nowMs: Long,
        ): DebugActionResult = DebugActionResult.Success
        override suspend fun setBonusCoins(coins: Int): DebugActionResult = DebugActionResult.Success
        override suspend fun resetSmartUsage(): DebugActionResult = DebugActionResult.Success
        override suspend fun setSmartUsage(
            monthlyCount: Int,
            bonusBalance: Int,
            nowMs: Long,
        ): DebugActionResult = DebugActionResult.Success
        override suspend fun reconcileSlots(): DebugActionResult = DebugActionResult.Success
    }

    @Test
    fun `initial state has testAccountsConfigured propagated`() = runTest {
        val vm = createViewModel(testAccountsConfigured = false)
        val state = vm.state.first()
        assertFalse(state.testAccountsConfigured)
    }

    @Test
    fun `OnSeedActiveWorkshopClick sets activeScenario to ActiveWorkshop on success`() = runTest {
        val vm = createViewModel()
        seeder.seedActiveWorkshopResult = SeedResult.Success

        val events = mutableListOf<DebugMenuEvent>()
        backgroundScope.launch(Dispatchers.Main) {
            vm.events.collect { events.add(it) }
        }
        vm.onAction(DebugMenuAction.OnSeedActiveWorkshopClick)

        assertEquals(1, seeder.seedActiveWorkshopCalls)
        assertTrue(events.any { it is DebugMenuEvent.ShowSnackbar })
        assertEquals(DebugScenario.ActiveWorkshop, vm.state.first().activeScenario)
    }

    @Test
    fun `seed failure leaves activeScenario unchanged`() = runTest {
        val vm = createViewModel()
        // First a successful seed to set activeScenario
        seeder.seedActiveWorkshopResult = SeedResult.Success
        vm.onAction(DebugMenuAction.OnSeedActiveWorkshopClick)

        // Then a failed seed
        seeder.seedBrandNewResult = SeedResult.Failure("network down")
        vm.onAction(DebugMenuAction.OnSeedBrandNewClick)

        assertEquals(DebugScenario.ActiveWorkshop, vm.state.first().activeScenario)
    }

    @Test
    fun `OnClearActiveScenarioClick clears activeScenario`() = runTest {
        val vm = createViewModel()
        seeder.seedActiveWorkshopResult = SeedResult.Success
        vm.onAction(DebugMenuAction.OnSeedActiveWorkshopClick)
        // Sanity: activeScenario set
        assertEquals(DebugScenario.ActiveWorkshop, vm.state.first().activeScenario)

        vm.onAction(DebugMenuAction.OnClearActiveScenarioClick)

        assertEquals(null, vm.state.first().activeScenario)
    }

    @Test
    fun `OnSeedBrandNewClick reports failure via Snackbar`() = runTest {
        val vm = createViewModel()
        seeder.seedBrandNewResult = SeedResult.Failure("boom")

        val events = mutableListOf<DebugMenuEvent>()
        backgroundScope.launch(Dispatchers.Main) {
            vm.events.collect { events.add(it) }
        }
        vm.onAction(DebugMenuAction.OnSeedBrandNewClick)

        assertTrue(events.any { it is DebugMenuEvent.ShowSnackbar })
    }

    @Test
    fun `OnResetOnboardingClick clears flags and emits NavigateToSplash`() = runTest {
        fakeOnboarding.onboardingSeen = true
        fakeOnboarding.workshopSetupCompleted = true
        val vm = createViewModel()

        val events = mutableListOf<DebugMenuEvent>()
        backgroundScope.launch(Dispatchers.Main) {
            vm.events.collect { events.add(it) }
        }
        vm.onAction(DebugMenuAction.OnResetOnboardingClick)

        assertFalse(fakeOnboarding.onboardingSeen)
        assertFalse(fakeOnboarding.workshopSetupCompleted)
        assertTrue(events.any { it is DebugMenuEvent.NavigateToSplash })
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

    @Test
    fun `OnWipeDataClick wipes data and clears activeScenario`() = runTest {
        val vm = createViewModel()
        // Seed first so there's an active scenario to clear
        seeder.seedActiveWorkshopResult = SeedResult.Success
        vm.onAction(DebugMenuAction.OnSeedActiveWorkshopClick)
        assertEquals(DebugScenario.ActiveWorkshop, vm.state.first().activeScenario)

        val events = mutableListOf<DebugMenuEvent>()
        backgroundScope.launch(Dispatchers.Main) { vm.events.collect { events.add(it) } }

        vm.onAction(DebugMenuAction.OnWipeDataClick)

        assertEquals(null, vm.state.first().activeScenario)
        assertTrue(events.any { it is DebugMenuEvent.ShowSnackbar })
    }

    @Test
    fun `OnBulkSeedClick opens dialog with defaults`() = runTest {
        val vm = createViewModel()

        vm.onAction(DebugMenuAction.OnBulkSeedClick)

        val dialog = vm.state.first().bulkSeed
        assertTrue(dialog != null)
        assertEquals("30", dialog.totalInput)
    }

    @Test
    fun `OnBulkSeedConfirm forwards count to seeder and closes dialog`() = runTest {
        val vm = createViewModel()
        seeder.seedBulkCustomersResult = SeedResult.Success

        vm.onAction(DebugMenuAction.OnBulkSeedClick)
        vm.onAction(DebugMenuAction.OnBulkSeedTotalChange("29"))
        vm.onAction(DebugMenuAction.OnBulkSeedMeasurementsChange("5"))
        vm.onAction(DebugMenuAction.OnBulkSeedOrdersChange("3"))
        vm.onAction(DebugMenuAction.OnBulkSeedConfirm)

        assertEquals(1, seeder.seedBulkCustomersCalls)
        assertEquals(Triple(29, 5, 3), seeder.lastBulkSeedArgs)
        assertEquals(null, vm.state.first().bulkSeed)
    }

    @Test
    fun `OnSetSmartUsageClick opens dialog with defaults`() = runTest {
        val vm = createViewModel()

        vm.onAction(DebugMenuAction.OnSetSmartUsageClick)

        val dialog = vm.state.first().smartUsage
        assertTrue(dialog != null)
        assertEquals("5", dialog.countInput)
    }

    @Test
    fun `OnSetSmartUsageConfirm closes dialog on success`() = runTest {
        val vm = createViewModel()

        vm.onAction(DebugMenuAction.OnSetSmartUsageClick)
        vm.onAction(DebugMenuAction.OnSetSmartUsageCountChange("4"))
        vm.onAction(DebugMenuAction.OnSetSmartUsageBonusUsedChange("0"))
        vm.onAction(DebugMenuAction.OnSetSmartUsageConfirm)

        assertEquals(null, vm.state.first().smartUsage)
    }

    @Test
    fun `OnBulkSeedConfirm is no-op when input invalid`() = runTest {
        val vm = createViewModel()

        vm.onAction(DebugMenuAction.OnBulkSeedClick)
        vm.onAction(DebugMenuAction.OnBulkSeedTotalChange("0"))
        vm.onAction(DebugMenuAction.OnBulkSeedConfirm)

        assertEquals(0, seeder.seedBulkCustomersCalls)
        // Dialog remains open so user can fix input
        assertTrue(vm.state.first().bulkSeed != null)
    }

    @Test
    fun `OnSendDailyDigestClick emits ShowSnackbar when digest is sent`() = runTest {
        val fake = FakeDigestDebugActions(result = sentBoth())
        val vm = createViewModel(digestActions = fake)

        val events = mutableListOf<DebugMenuEvent>()
        backgroundScope.launch(Dispatchers.Main) {
            vm.events.collect { events.add(it) }
        }
        vm.onAction(DebugMenuAction.OnSendDailyDigestClick)

        val snackbar = events.filterIsInstance<DebugMenuEvent.ShowSnackbar>().first()
        val messageText = (snackbar.message as UiText.DynamicString).value
        assertTrue(messageText.contains("sent", ignoreCase = true))
    }

    @Test
    fun `OnSendDailyDigestClick emits snackbar containing 'suppressed' when digest is empty`() = runTest {
        val fake = FakeDigestDebugActions(result = DigestSendResult.Empty)
        val vm = createViewModel(digestActions = fake)

        val events = mutableListOf<DebugMenuEvent>()
        backgroundScope.launch(Dispatchers.Main) {
            vm.events.collect { events.add(it) }
        }
        vm.onAction(DebugMenuAction.OnSendDailyDigestClick)

        val snackbar = events.filterIsInstance<DebugMenuEvent.ShowSnackbar>().first()
        val messageText = (snackbar.message as UiText.DynamicString).value
        assertTrue(messageText.contains("suppressed", ignoreCase = true))
    }

    @Test
    fun `OnSendDailyDigestClick emits snackbar containing 'off' when digest is disabled`() = runTest {
        val fake = FakeDigestDebugActions(result = DigestSendResult.Disabled)
        val vm = createViewModel(digestActions = fake)

        val events = mutableListOf<DebugMenuEvent>()
        backgroundScope.launch(Dispatchers.Main) {
            vm.events.collect { events.add(it) }
        }
        vm.onAction(DebugMenuAction.OnSendDailyDigestClick)

        val snackbar = events.filterIsInstance<DebugMenuEvent.ShowSnackbar>().first()
        val messageText = (snackbar.message as UiText.DynamicString).value
        assertTrue(messageText.contains("off", ignoreCase = true))
    }

    @Test
    fun `OnSendDailyDigestClick emits snackbar containing failure reason on Failure`() = runTest {
        val fake = FakeDigestDebugActions(result = DigestSendResult.Failure("boom"))
        val vm = createViewModel(digestActions = fake)

        val events = mutableListOf<DebugMenuEvent>()
        backgroundScope.launch(Dispatchers.Main) {
            vm.events.collect { events.add(it) }
        }
        vm.onAction(DebugMenuAction.OnSendDailyDigestClick)

        val snackbar = events.filterIsInstance<DebugMenuEvent.ShowSnackbar>().first()
        val messageText = (snackbar.message as UiText.DynamicString).value
        assertTrue(messageText.contains("boom", ignoreCase = true))
    }

    @Test
    fun `OnSendTestPushClick emits ShowSnackbar mentioning 'push' when sent`() = runTest {
        val fake = FakeDigestDebugActions(result = sentBoth())
        val vm = createViewModel(digestActions = fake)

        val events = mutableListOf<DebugMenuEvent>()
        backgroundScope.launch(Dispatchers.Main) {
            vm.events.collect { events.add(it) }
        }
        vm.onAction(DebugMenuAction.OnSendTestPushClick)

        val snackbar = events.filterIsInstance<DebugMenuEvent.ShowSnackbar>().first()
        val messageText = (snackbar.message as UiText.DynamicString).value
        assertTrue(messageText.contains("push", ignoreCase = true))
    }

    @Test
    fun `OnSendTestPushClick emits snackbar containing failure reason on Failure`() = runTest {
        val fake = FakeDigestDebugActions(result = DigestSendResult.Failure("timeout"))
        val vm = createViewModel(digestActions = fake)

        val events = mutableListOf<DebugMenuEvent>()
        backgroundScope.launch(Dispatchers.Main) {
            vm.events.collect { events.add(it) }
        }
        vm.onAction(DebugMenuAction.OnSendTestPushClick)

        val snackbar = events.filterIsInstance<DebugMenuEvent.ShowSnackbar>().first()
        val messageText = (snackbar.message as UiText.DynamicString).value
        assertTrue(messageText.contains("timeout", ignoreCase = true))
    }

    // --- Cursor finding #6: both channels off → Disabled, not generic failure ---

    @Test
    fun `OnSendDailyDigestClick shows opt-out message when both channels disabled`() = runTest {
        val fake = FakeDigestDebugActions(result = DigestSendResult.Disabled)
        val vm = createViewModel(digestActions = fake)

        val events = mutableListOf<DebugMenuEvent>()
        backgroundScope.launch(Dispatchers.Main) { vm.events.collect { events.add(it) } }
        vm.onAction(DebugMenuAction.OnSendDailyDigestClick)

        val messageText = (events.filterIsInstance<DebugMenuEvent.ShowSnackbar>().first().message as UiText.DynamicString).value
        // Must NOT say "failed" — it's an intentional opt-out
        assertFalse(messageText.contains("failed", ignoreCase = true), "Expected opt-out message, got: $messageText")
        assertTrue(messageText.contains("off", ignoreCase = true), "Expected 'off' in opt-out message, got: $messageText")
    }

    @Test
    fun `OnSendTestPushClick shows no-push opt-out message when both channels disabled`() = runTest {
        val fake = FakeDigestDebugActions(result = DigestSendResult.Disabled)
        val vm = createViewModel(digestActions = fake)

        val events = mutableListOf<DebugMenuEvent>()
        backgroundScope.launch(Dispatchers.Main) { vm.events.collect { events.add(it) } }
        vm.onAction(DebugMenuAction.OnSendTestPushClick)

        val messageText = (events.filterIsInstance<DebugMenuEvent.ShowSnackbar>().first().message as UiText.DynamicString).value
        assertFalse(messageText.contains("failed", ignoreCase = true), "Expected opt-out message, got: $messageText")
        assertTrue(messageText.contains("push", ignoreCase = true), "Expected 'push' in message, got: $messageText")
    }

    // --- Cursor finding #7: per-channel accuracy ---

    @Test
    fun `OnSendDailyDigestClick shows email-only message when only email sent`() = runTest {
        val fake = FakeDigestDebugActions(result = sentEmailOnly())
        val vm = createViewModel(digestActions = fake)

        val events = mutableListOf<DebugMenuEvent>()
        backgroundScope.launch(Dispatchers.Main) { vm.events.collect { events.add(it) } }
        vm.onAction(DebugMenuAction.OnSendDailyDigestClick)

        val messageText = (events.filterIsInstance<DebugMenuEvent.ShowSnackbar>().first().message as UiText.DynamicString).value
        assertTrue(messageText.contains("email", ignoreCase = true), "Expected 'email' in message, got: $messageText")
        assertFalse(messageText.contains("push", ignoreCase = true), "Should not claim push, got: $messageText")
    }

    @Test
    fun `OnSendDailyDigestClick shows push-only message when only push sent`() = runTest {
        val fake = FakeDigestDebugActions(result = sentPushOnly())
        val vm = createViewModel(digestActions = fake)

        val events = mutableListOf<DebugMenuEvent>()
        backgroundScope.launch(Dispatchers.Main) { vm.events.collect { events.add(it) } }
        vm.onAction(DebugMenuAction.OnSendDailyDigestClick)

        val messageText = (events.filterIsInstance<DebugMenuEvent.ShowSnackbar>().first().message as UiText.DynamicString).value
        assertTrue(messageText.contains("push", ignoreCase = true), "Expected 'push' in message, got: $messageText")
        assertFalse(messageText.contains("email", ignoreCase = true), "Should not claim email, got: $messageText")
    }

    @Test
    fun `OnSendTestPushClick shows push-specific success when push sent`() = runTest {
        val fake = FakeDigestDebugActions(result = sentPushOnly())
        val vm = createViewModel(digestActions = fake)

        val events = mutableListOf<DebugMenuEvent>()
        backgroundScope.launch(Dispatchers.Main) { vm.events.collect { events.add(it) } }
        vm.onAction(DebugMenuAction.OnSendTestPushClick)

        val messageText = (events.filterIsInstance<DebugMenuEvent.ShowSnackbar>().first().message as UiText.DynamicString).value
        assertTrue(messageText.contains("push", ignoreCase = true), "Expected 'push' in message, got: $messageText")
        // Should be a success message, not a failure
        assertFalse(messageText.contains("failed", ignoreCase = true), "Should not say failed, got: $messageText")
    }

    @Test
    fun `OnSendTestPushClick reports no push when only email sent`() = runTest {
        val fake = FakeDigestDebugActions(result = sentEmailOnly())
        val vm = createViewModel(digestActions = fake)

        val events = mutableListOf<DebugMenuEvent>()
        backgroundScope.launch(Dispatchers.Main) { vm.events.collect { events.add(it) } }
        vm.onAction(DebugMenuAction.OnSendTestPushClick)

        val messageText = (events.filterIsInstance<DebugMenuEvent.ShowSnackbar>().first().message as UiText.DynamicString).value
        // Should mention push but NOT claim it was sent
        assertTrue(messageText.contains("push", ignoreCase = true), "Expected 'push' in message, got: $messageText")
        assertFalse(messageText.lowercase().startsWith("test push sent"), "Should not claim push was sent, got: $messageText")
    }

    private class FakeDebugSeeder : DebugSeeder {
        var seedBrandNewResult: SeedResult = SeedResult.Success
        var seedActiveWorkshopResult: SeedResult = SeedResult.Success
        var seedAllReconnectResult: SeedResult = SeedResult.Success
        var seedBulkCustomersResult: SeedResult = SeedResult.Success
        var seedBrandNewCalls = 0
        var seedActiveWorkshopCalls = 0
        var seedBulkCustomersCalls = 0
        var lastBulkSeedArgs: Triple<Int, Int, Int>? = null

        override suspend fun seedBrandNew(): SeedResult { seedBrandNewCalls++; return seedBrandNewResult }
        override suspend fun seedActiveWorkshop(): SeedResult { seedActiveWorkshopCalls++; return seedActiveWorkshopResult }
        override suspend fun seedAllReconnect(): SeedResult = seedAllReconnectResult
        override suspend fun seedBulkCustomers(
            count: Int,
            withMeasurementsCount: Int,
            withOrdersCount: Int,
        ): SeedResult {
            seedBulkCustomersCalls++
            lastBulkSeedArgs = Triple(count, withMeasurementsCount, withOrdersCount)
            return seedBulkCustomersResult
        }
        override suspend fun wipeAllData(): SeedResult = SeedResult.Success
    }
}
