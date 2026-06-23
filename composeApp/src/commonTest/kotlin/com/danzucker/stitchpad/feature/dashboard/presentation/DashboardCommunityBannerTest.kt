package com.danzucker.stitchpad.feature.dashboard.presentation

import app.cash.turbine.test
import com.danzucker.stitchpad.core.config.FakeAppConfigRepository
import com.danzucker.stitchpad.core.config.FakeCommunityJoinTracker
import com.danzucker.stitchpad.core.config.domain.CommunityBannerDismissal
import com.danzucker.stitchpad.core.config.domain.model.AppConfig
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.model.Notification
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.NotificationRepository
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageStore
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.goals.data.FakeWeeklyGoalRepository
import com.danzucker.stitchpad.feature.notification.push.PushTokenRegistrar
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardCommunityBannerTest {

    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var userRepository: FakeUserRepository
    private lateinit var weeklyGoalRepository: FakeWeeklyGoalRepository
    private lateinit var smartUsageStore: FakeSmartUsageStoreCommunity
    private lateinit var notificationRepository: FakeNotificationRepositoryCommunity
    private lateinit var appConfig: FakeAppConfigRepository
    private lateinit var communityJoinTracker: FakeCommunityJoinTracker
    private lateinit var prefs: FakeOnboardingPreferences
    private lateinit var dismissal: CommunityBannerDismissal

    private val testTimeZone = TimeZone.UTC
    private val today = LocalDate(2026, 4, 22)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        orderRepository = FakeOrderRepository()
        customerRepository = FakeCustomerRepository()
        authRepository = FakeAuthRepository()
        userRepository = FakeUserRepository()
        weeklyGoalRepository = FakeWeeklyGoalRepository()
        smartUsageStore = FakeSmartUsageStoreCommunity()
        notificationRepository = FakeNotificationRepositoryCommunity()
        appConfig = FakeAppConfigRepository()
        communityJoinTracker = FakeCommunityJoinTracker()
        prefs = FakeOnboardingPreferences()
        dismissal = CommunityBannerDismissal(prefs)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun millisAt(date: LocalDate, hour: Int): Long =
        LocalDateTime(date, LocalTime(hour, 0)).toInstant(testTimeZone).toEpochMilliseconds()

    private fun TestScope.createViewModel(): DashboardViewModel {
        val vm = DashboardViewModel(
            orderRepository = orderRepository,
            customerRepository = customerRepository,
            authRepository = authRepository,
            userRepository = userRepository,
            weeklyGoalRepository = weeklyGoalRepository,
            smartUsageStore = smartUsageStore,
            entitlements = FakeEntitlementsProviderCommunity(),
            notificationRepository = notificationRepository,
            pushTokenRegistrar = NoOpPushTokenRegistrarCommunity(),
            nowMillis = { millisAt(today, hour = 9) },
            timeZone = testTimeZone,
            appConfigRepository = appConfig,
            communityJoinTracker = communityJoinTracker,
            dismissal = dismissal,
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    @Test
    fun configEnabledAndNotDismissed_showsCommunityBanner() = runTest {
        prefs.clearCommunityBannerDismissed()
        appConfig.emit(AppConfig(communityEnabled = true, communityInviteUrl = "https://chat.whatsapp.com/X"))
        val vm = createViewModel()
        assertTrue(vm.state.value.showCommunityBanner)
        assertEquals("https://chat.whatsapp.com/X", vm.state.value.communityUrl)
    }

    @Test
    fun dismissed_hidesCommunityBanner() = runTest {
        prefs.setCommunityBannerDismissed()
        appConfig.emit(AppConfig(communityEnabled = true, communityInviteUrl = "https://chat.whatsapp.com/X"))
        val vm = createViewModel()
        assertFalse(vm.state.value.showCommunityBanner)
    }

    @Test
    fun onDismiss_setsFlagAndHides() = runTest {
        appConfig.emit(AppConfig(communityEnabled = true, communityInviteUrl = "https://chat.whatsapp.com/X"))
        val vm = createViewModel()
        vm.onAction(DashboardAction.OnDismissCommunityBanner)
        assertFalse(vm.state.value.showCommunityBanner)
        assertTrue(prefs.hasDismissedCommunityBanner())
    }

    @Test
    fun communityEnabledWithInvalidUrl_hidesBanner() = runTest {
        prefs.clearCommunityBannerDismissed()
        // Malformed URL — predicate must reject this.
        appConfig.emit(AppConfig(communityEnabled = true, communityInviteUrl = "chat.whatsapp.com/foo"))
        val vm = createViewModel()
        assertFalse(vm.state.value.showCommunityBanner)
    }

    @Test
    fun onJoin_emitsOpenLinkTracksAndDismisses() = runTest {
        appConfig.emit(AppConfig(communityEnabled = true, communityInviteUrl = "https://chat.whatsapp.com/X"))
        val vm = createViewModel()
        vm.events.test {
            vm.onAction(DashboardAction.OnJoinCommunity)
            assertEquals(DashboardEvent.OpenCommunityLink("https://chat.whatsapp.com/X"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, communityJoinTracker.tapCount)
        assertTrue(prefs.hasDismissedCommunityBanner())
        assertFalse(vm.state.value.showCommunityBanner)
    }

    /**
     * Reactivity test: a Settings-side join (simulated by calling
     * [CommunityBannerDismissal.markDismissed] directly on the shared singleton)
     * must hide the banner on an already-alive DashboardViewModel WITHOUT recreating it.
     *
     * RED before the fix (dismissal was a local var read once at init);
     * GREEN after (combine reacts to the dismissal.dismissed StateFlow).
     */
    @Test
    fun settingsJoin_hidesLiveDashboardBannerWithoutVmRecreation() = runTest {
        appConfig.emit(AppConfig(communityEnabled = true, communityInviteUrl = "https://chat.whatsapp.com/X"))
        val vm = createViewModel()
        assertTrue(vm.state.value.showCommunityBanner, "precondition: banner visible before Settings join")

        // Simulate the Settings ViewModel calling dismissal.markDismissed() on the shared singleton.
        dismissal.markDismissed()

        assertFalse(vm.state.value.showCommunityBanner, "banner must hide on live VM after Settings join")
        assertTrue(prefs.hasDismissedCommunityBanner(), "persist flag must also be set")
    }

    private class FakeSmartUsageStoreCommunity : SmartUsageStore {
        private val flow = MutableStateFlow<Int?>(null)
        override val remainingFreeQuota: StateFlow<Int?> = flow
        override fun update(remaining: Int?) { flow.value = remaining }
    }

    private class FakeNotificationRepositoryCommunity : NotificationRepository {
        override fun observeNotifications(userId: String): Flow<Result<List<Notification>, DataError.Network>> =
            flowOf(Result.Success(emptyList()))

        override fun observeUnreadCount(userId: String): Flow<Int> = flowOf(0)

        override suspend fun markAsRead(userId: String, notificationId: String): EmptyResult<DataError.Network> =
            Result.Success(Unit)

        override suspend fun markAllRead(userId: String, notificationIds: List<String>): EmptyResult<DataError.Network> =
            Result.Success(Unit)
    }

    private class NoOpPushTokenRegistrarCommunity : PushTokenRegistrar {
        override suspend fun registerForUser(userId: String) {}
        override suspend fun register(userId: String, token: String) {}
        override suspend fun unregisterForUser(userId: String) {}
        override suspend fun invalidateToken() {}
    }

    private class FakeEntitlementsProviderCommunity : EntitlementsProvider {
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
}
