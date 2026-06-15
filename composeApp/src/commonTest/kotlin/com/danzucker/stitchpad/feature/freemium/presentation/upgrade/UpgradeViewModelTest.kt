package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence
import com.danzucker.stitchpad.feature.freemium.domain.CheckoutSession
import com.danzucker.stitchpad.feature.freemium.domain.PaymentError
import com.danzucker.stitchpad.feature.freemium.domain.PaymentRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UpgradeViewModelTest {

    private lateinit var entitlements: FakeEntitlementsProvider
    private lateinit var payments: FakePaymentRepository
    private lateinit var pendingDeepLink: PendingDeepLinkHolder

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        entitlements = FakeEntitlementsProvider(SubscriptionTier.FREE)
        payments = FakePaymentRepository()
        pendingDeepLink = PendingDeepLinkHolder()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun payWithPaystack_opensConfirmSheet_doesNotStartCheckout() = runTest {
        val vm = newVm() // FREE → defaults to PRO / MONTHLY

        vm.onAction(UpgradeAction.PayWithPaystack)
        runCurrent()

        assertTrue(vm.state.value.showCheckoutConfirmSheet)
        assertFalse(vm.state.value.isStartingCheckout)
        assertEquals(null, payments.lastTier) // repo not called
    }

    @Test
    fun confirmCheckout_startsCheckout_emitsOpenBrowser() = runTest {
        val vm = newVm()
        val eventDeferred = async { vm.events.first() }

        vm.onAction(UpgradeAction.PayWithPaystack)
        runCurrent()
        assertTrue(vm.state.value.showCheckoutConfirmSheet)

        vm.onAction(UpgradeAction.ConfirmCheckout)
        runCurrent()

        val event = eventDeferred.await()
        assertIs<UpgradeEvent.OpenExternalBrowser>(event)
        assertEquals("https://checkout.paystack.com/stitchpad", event.url)
        assertFalse(vm.state.value.showCheckoutConfirmSheet)
        assertFalse(vm.state.value.isStartingCheckout)
        assertEquals(SubscriptionTier.PRO, payments.lastTier)
    }

    @Test
    fun dismissCheckoutSheet_hidesSheet_noCheckout() = runTest {
        val vm = newVm()

        vm.onAction(UpgradeAction.PayWithPaystack)
        runCurrent()
        assertTrue(vm.state.value.showCheckoutConfirmSheet)

        vm.onAction(UpgradeAction.DismissCheckoutSheet)
        runCurrent()

        assertFalse(vm.state.value.showCheckoutConfirmSheet)
        assertFalse(vm.state.value.isStartingCheckout)
        assertEquals(null, payments.lastTier) // repo not called
    }

    @Test
    fun confirmCheckout_sets_loading_while_checkout_starts() = runTest {
        val checkout = CompletableDeferred<Result<CheckoutSession, PaymentError>>()
        payments.deferred = checkout
        val vm = newVm()

        vm.onAction(UpgradeAction.PayWithPaystack)
        runCurrent()
        vm.onAction(UpgradeAction.ConfirmCheckout)
        runCurrent()

        assertTrue(vm.state.value.isStartingCheckout)
        checkout.complete(
            Result.Success(
                CheckoutSession(
                    authorizationUrl = "https://checkout.paystack.com/done",
                    reference = "ref",
                )
            )
        )
        runCurrent()

        assertFalse(vm.state.value.isStartingCheckout)
    }

    @Test
    fun plan_selection_is_ignored_while_checkout_is_starting() = runTest {
        val checkout = CompletableDeferred<Result<CheckoutSession, PaymentError>>()
        payments.deferred = checkout
        val vm = newVm() // FREE → defaults to PRO / MONTHLY

        vm.onAction(UpgradeAction.PayWithPaystack)
        runCurrent()
        vm.onAction(UpgradeAction.ConfirmCheckout)
        runCurrent()
        assertTrue(vm.state.value.isStartingCheckout)

        // Switching plans while the spinner shows must be ignored, so the UI can't
        // drift from the plan the in-flight Paystack session was opened for.
        vm.onAction(UpgradeAction.SelectTier(SubscriptionTier.ATELIER))
        vm.onAction(UpgradeAction.SelectCadence(BillingCadence.ANNUAL))
        runCurrent()

        assertEquals(SubscriptionTier.PRO, vm.state.value.selectedTier)
        assertEquals(BillingCadence.MONTHLY, vm.state.value.billingCadence)

        checkout.complete(Result.Success(CheckoutSession("https://checkout.paystack.com/x", "ref")))
        runCurrent()

        // Once checkout settles, selection is editable again.
        vm.onAction(UpgradeAction.SelectTier(SubscriptionTier.ATELIER))
        runCurrent()
        assertEquals(SubscriptionTier.ATELIER, vm.state.value.selectedTier)
    }

    @Test
    fun confirmCheckout_on_error_emits_snackbar() = runTest {
        payments.result = Result.Error(PaymentError.PROVIDER_UNAVAILABLE)
        val vm = newVm()
        val eventDeferred = async { vm.events.first() }

        vm.onAction(UpgradeAction.PayWithPaystack)
        runCurrent()
        vm.onAction(UpgradeAction.ConfirmCheckout)
        runCurrent()

        val event = eventDeferred.await()
        assertIs<UpgradeEvent.ShowSnackbar>(event)
        assertIs<UiText.StringResourceText>(event.message)
        assertFalse(vm.state.value.isStartingCheckout)
    }

    @Test
    fun tier_upgrade_emits_upgradeDetected() = runTest {
        val vm = newVm()
        val eventDeferred = async { vm.events.first() }

        entitlements.emit(SubscriptionTier.PRO)
        runCurrent()

        assertEquals(UpgradeEvent.UpgradeDetected, eventDeferred.await())
    }

    @Test
    fun renewal_deep_link_preselects_the_tier_and_cadence() = runTest {
        pendingDeepLink.setUpgrade(tier = "atelier", cadence = "annual")

        val vm = newVm()

        assertEquals(SubscriptionTier.ATELIER, vm.state.value.selectedTier)
        assertEquals(BillingCadence.ANNUAL, vm.state.value.billingCadence)
        // Consumed once — a second screen open falls back to the default.
        assertEquals(SubscriptionTier.PRO, newVm().state.value.selectedTier)
    }

    @Test
    fun no_deep_link_uses_the_default_selection() = runTest {
        val vm = newVm() // FREE → PRO / MONTHLY
        assertEquals(SubscriptionTier.PRO, vm.state.value.selectedTier)
        assertEquals(BillingCadence.MONTHLY, vm.state.value.billingCadence)
    }

    @Test
    fun preselected_free_is_ignored_and_falls_back_to_default() = runTest {
        pendingDeepLink.setUpgrade(tier = "free", cadence = null)
        val vm = newVm()
        assertEquals(SubscriptionTier.PRO, vm.state.value.selectedTier)
    }

    private fun newVm(): UpgradeViewModel = UpgradeViewModel(
        entitlements = entitlements,
        paymentRepository = payments,
        pendingDeepLink = pendingDeepLink,
    )

    private class FakePaymentRepository : PaymentRepository {
        var result: Result<CheckoutSession, PaymentError> = Result.Success(
            CheckoutSession(
                authorizationUrl = "https://checkout.paystack.com/stitchpad",
                reference = "ref",
            )
        )
        var deferred: CompletableDeferred<Result<CheckoutSession, PaymentError>>? = null
        var lastTier: SubscriptionTier? = null
        var lastCadence: BillingCadence? = null

        override suspend fun initializeSubscriptionCheckout(
            tier: SubscriptionTier,
            cadence: BillingCadence,
        ): Result<CheckoutSession, PaymentError> {
            lastTier = tier
            lastCadence = cadence
            return deferred?.await() ?: result
        }
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
