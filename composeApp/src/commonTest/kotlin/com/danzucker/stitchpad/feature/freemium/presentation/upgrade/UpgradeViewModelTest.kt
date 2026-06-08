package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
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

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        entitlements = FakeEntitlementsProvider(SubscriptionTier.FREE)
        payments = FakePaymentRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun payWithPaystack_opens_returned_checkout_url_on_success() = runTest {
        val vm = newVm()
        val eventDeferred = async { vm.events.first() }

        vm.onAction(UpgradeAction.PayWithPaystack)
        runCurrent()

        val event = eventDeferred.await()
        assertIs<UpgradeEvent.OpenExternalBrowser>(event)
        assertEquals("https://checkout.paystack.com/stitchpad", event.url)
        assertEquals(SubscriptionTier.PRO, payments.lastTier)
        assertEquals(BillingCadence.MONTHLY, payments.lastCadence)
        assertFalse(vm.state.value.isStartingCheckout)
    }

    @Test
    fun payWithPaystack_sets_loading_while_checkout_starts() = runTest {
        val checkout = CompletableDeferred<Result<CheckoutSession, PaymentError>>()
        payments.deferred = checkout
        val vm = newVm()

        vm.onAction(UpgradeAction.PayWithPaystack)
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
    fun payWithPaystack_on_error_emits_snackbar() = runTest {
        payments.result = Result.Error(PaymentError.PROVIDER_UNAVAILABLE)
        val vm = newVm()
        val eventDeferred = async { vm.events.first() }

        vm.onAction(UpgradeAction.PayWithPaystack)
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

    private fun newVm(): UpgradeViewModel = UpgradeViewModel(
        entitlements = entitlements,
        paymentRepository = payments,
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
