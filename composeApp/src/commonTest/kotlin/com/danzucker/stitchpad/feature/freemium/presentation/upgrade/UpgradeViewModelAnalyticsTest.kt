package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.config.FakeAppConfigRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence
import com.danzucker.stitchpad.feature.freemium.domain.CheckoutOutcome
import com.danzucker.stitchpad.feature.freemium.domain.PaymentError
import com.danzucker.stitchpad.feature.freemium.domain.PaymentRepository
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class UpgradeViewModelAnalyticsTest {

    private lateinit var entitlements: FakeEntitlementsProvider
    private lateinit var payments: FakePaymentRepository
    private lateinit var pendingDeepLink: PendingDeepLinkHolder
    private lateinit var analytics: FakeAnalytics

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        entitlements = FakeEntitlementsProvider(SubscriptionTier.FREE)
        payments = FakePaymentRepository()
        pendingDeepLink = PendingDeepLinkHolder()
        analytics = FakeAnalytics()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `upgrade_completed is logged once when tier rises from FREE to PRO`() = runTest {
        val vm = newVm()

        entitlements.emit(SubscriptionTier.PRO)
        runCurrent()

        val upgradeEvents = analytics.events.filterIsInstance<AnalyticsEvent.UpgradeCompleted>()
        assertEquals(1, upgradeEvents.size, "Expected exactly one UpgradeCompleted event but got: ${analytics.events}")
        assertEquals("pro", upgradeEvents.first().tier)
    }

    @Test
    fun `upgrade_completed is NOT fired again when same higher tier is re-emitted`() = runTest {
        val vm = newVm()

        entitlements.emit(SubscriptionTier.PRO)
        runCurrent()
        // Re-emit the same PRO tier — should NOT produce a second event
        entitlements.emit(SubscriptionTier.PRO)
        runCurrent()

        val upgradeEvents = analytics.events.filterIsInstance<AnalyticsEvent.UpgradeCompleted>()
        assertEquals(1, upgradeEvents.size, "Re-emitting the same tier must not fire a second event, got: ${analytics.events}")
    }

    @Test
    fun `upgrade_completed is NOT fired when tier does not rise`() = runTest {
        val vm = newVm()

        // Emit the same FREE tier (no rise)
        entitlements.emit(SubscriptionTier.FREE)
        runCurrent()

        val upgradeEvents = analytics.events.filterIsInstance<AnalyticsEvent.UpgradeCompleted>()
        assertEquals(0, upgradeEvents.size, "No UpgradeCompleted expected when tier does not rise, got: ${analytics.events}")
    }

    @Test
    fun `upgrade_completed logs atelier when tier rises to ATELIER`() = runTest {
        val vm = newVm()

        entitlements.emit(SubscriptionTier.ATELIER)
        runCurrent()

        val upgradeEvents = analytics.events.filterIsInstance<AnalyticsEvent.UpgradeCompleted>()
        assertEquals(1, upgradeEvents.size)
        assertEquals("atelier", upgradeEvents.first().tier)
    }

    private fun newVm(): UpgradeViewModel = UpgradeViewModel(
        entitlements = entitlements,
        paymentRepository = payments,
        pendingDeepLink = pendingDeepLink,
        analytics = analytics,
        appConfigRepository = FakeAppConfigRepository(),
    )

    private class FakePaymentRepository : PaymentRepository {
        override suspend fun startCheckout(
            tier: SubscriptionTier,
            cadence: BillingCadence,
        ): Result<CheckoutOutcome, PaymentError> = Result.Success(
            CheckoutOutcome.Redirect(
                authorizationUrl = "https://checkout.paystack.com/stitchpad",
                reference = "ref",
            )
        )

        override suspend fun productCatalog(): Result<Map<String, String>, PaymentError> =
            Result.Success(emptyMap())

        override suspend fun restorePurchases(): Result<CheckoutOutcome, PaymentError> =
            Result.Success(CheckoutOutcome.Cancelled)
    }

    private class FakeEntitlementsProvider(initialTier: SubscriptionTier) : EntitlementsProvider {
        private val _flow = MutableStateFlow(entitlements(initialTier))
        override val flow: StateFlow<UserEntitlements> = _flow
        override fun current(): UserEntitlements = _flow.value
        override suspend fun awaitHydrated(): UserEntitlements = _flow.value

        fun emit(tier: SubscriptionTier) {
            _flow.value = entitlements(tier)
        }

        private fun entitlements(tier: SubscriptionTier) = UserEntitlements(
            tier = tier,
            customerCap = if (tier == SubscriptionTier.FREE) 15 else Int.MAX_VALUE,
            smartCoinAllowance = 5,
            isInWelcomeWindow = false,
            welcomeEndsAt = Instant.fromEpochMilliseconds(0),
            isWithinWelcomeEndingWarning = false,
            welcomeDaysLeft = null,
            canUseCustomMeasurements = tier != SubscriptionTier.FREE,
        )
    }
}
