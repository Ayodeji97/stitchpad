package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.config.FakeAppConfigRepository
import com.danzucker.stitchpad.core.config.domain.model.AppConfig
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.session.FakeCurrentCountryProvider
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence
import com.danzucker.stitchpad.feature.freemium.domain.CheckoutOutcome
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
import kotlin.test.assertNull
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
    fun startCheckout_opens_returned_checkout_url_on_redirect() = runTest {
        val vm = newVm()
        val eventDeferred = async { vm.events.first() }

        vm.onAction(UpgradeAction.StartCheckout)
        runCurrent()

        val event = eventDeferred.await()
        assertIs<UpgradeEvent.OpenExternalBrowser>(event)
        assertEquals("https://checkout.paystack.com/stitchpad", event.url)
        assertEquals(SubscriptionTier.PRO, payments.lastTier)
        assertEquals(BillingCadence.MONTHLY, payments.lastCadence)
        assertFalse(vm.state.value.isStartingCheckout)
    }

    @Test
    fun startCheckout_is_blocked_when_paystack_billing_not_live() = runTest {
        // Android pre-go-live: billingEnabled=false → the Paystack CTA is "Coming
        // soon". StartCheckout must be an inert no-op: no repo call, no spinner, no
        // browser redirect — so a user can never hit a dead test-mode checkout.
        val vm = newVm(billingEnabled = false)
        runCurrent()
        assertFalse(vm.state.value.canCheckout)

        vm.onAction(UpgradeAction.StartCheckout)
        runCurrent()

        assertNull(payments.lastTier)
        assertFalse(vm.state.value.isStartingCheckout)
    }

    @Test
    fun checkout_is_enabled_once_paystack_billing_goes_live() = runTest {
        // Flipping config/app.billingEnabled=true turns the Android CTA live with no
        // app release — canCheckout becomes true and checkout proceeds.
        val vm = newVm(billingEnabled = true)
        runCurrent()
        assertTrue(vm.state.value.canCheckout)

        vm.onAction(UpgradeAction.StartCheckout)
        runCurrent()

        assertEquals(SubscriptionTier.PRO, payments.lastTier)
    }

    @Test
    fun startCheckout_purchasedAndGranted_emits_no_event_and_clears_loading() = runTest {
        // Apple path: the grant arrives as a tier change (UpgradeDetected), not a
        // checkout event — startCheckout itself just clears the spinner.
        payments.result = Result.Success(CheckoutOutcome.PurchasedAndGranted)
        val vm = newVm()

        vm.onAction(UpgradeAction.StartCheckout)
        runCurrent()

        assertFalse(vm.state.value.isStartingCheckout)
    }

    @Test
    fun startCheckout_pending_emits_snackbar() = runTest {
        payments.result = Result.Success(CheckoutOutcome.Pending)
        val vm = newVm()
        val eventDeferred = async { vm.events.first() }

        vm.onAction(UpgradeAction.StartCheckout)
        runCurrent()

        assertIs<UpgradeEvent.ShowSnackbar>(eventDeferred.await())
        assertFalse(vm.state.value.isStartingCheckout)
    }

    @Test
    fun startCheckout_sets_loading_while_checkout_starts() = runTest {
        val checkout = CompletableDeferred<Result<CheckoutOutcome, PaymentError>>()
        payments.deferred = checkout
        val vm = newVm()

        vm.onAction(UpgradeAction.StartCheckout)
        runCurrent()

        assertTrue(vm.state.value.isStartingCheckout)
        checkout.complete(
            Result.Success(
                CheckoutOutcome.Redirect(
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
        val checkout = CompletableDeferred<Result<CheckoutOutcome, PaymentError>>()
        payments.deferred = checkout
        val vm = newVm() // FREE → defaults to PRO / MONTHLY

        vm.onAction(UpgradeAction.StartCheckout)
        runCurrent()
        assertTrue(vm.state.value.isStartingCheckout)

        // Switching plans while the spinner shows must be ignored, so the UI can't
        // drift from the plan the in-flight checkout was started for.
        vm.onAction(UpgradeAction.SelectTier(SubscriptionTier.ATELIER))
        vm.onAction(UpgradeAction.SelectCadence(BillingCadence.ANNUAL))
        runCurrent()

        assertEquals(SubscriptionTier.PRO, vm.state.value.selectedTier)
        assertEquals(BillingCadence.MONTHLY, vm.state.value.billingCadence)

        checkout.complete(Result.Success(CheckoutOutcome.Redirect("https://checkout.paystack.com/x", "ref")))
        runCurrent()

        // Once checkout settles, selection is editable again.
        vm.onAction(UpgradeAction.SelectTier(SubscriptionTier.ATELIER))
        runCurrent()
        assertEquals(SubscriptionTier.ATELIER, vm.state.value.selectedTier)
    }

    @Test
    fun startCheckout_on_error_emits_snackbar() = runTest {
        payments.result = Result.Error(PaymentError.PROVIDER_UNAVAILABLE)
        val vm = newVm()
        val eventDeferred = async { vm.events.first() }

        vm.onAction(UpgradeAction.StartCheckout)
        runCurrent()

        val event = eventDeferred.await()
        assertIs<UpgradeEvent.ShowSnackbar>(event)
        assertIs<UiText.StringResourceText>(event.message)
        assertFalse(vm.state.value.isStartingCheckout)
    }

    @Test
    fun onPrivacyClick_opens_the_getstitchpad_privacy_url() = runTest {
        val vm = newVm()
        val eventDeferred = async { vm.events.first() }

        vm.onAction(UpgradeAction.OnPrivacyClick)
        runCurrent()

        val event = eventDeferred.await()
        assertIs<UpgradeEvent.OpenExternalBrowser>(event)
        assertEquals("https://getstitchpad.com/privacy", event.url)
    }

    @Test
    fun onTermsClick_opens_the_getstitchpad_terms_url() = runTest {
        val vm = newVm()
        val eventDeferred = async { vm.events.first() }

        vm.onAction(UpgradeAction.OnTermsClick)
        runCurrent()

        val event = eventDeferred.await()
        assertIs<UpgradeEvent.OpenExternalBrowser>(event)
        assertEquals("https://getstitchpad.com/terms", event.url)
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

    @Test
    fun selectTier_ignoresCurrentTier() = runTest {
        // PRO user → initialState defaults selectedTier to ATELIER.
        entitlements = FakeEntitlementsProvider(SubscriptionTier.PRO)
        val vm = newVm()

        // Tapping the already-owned PRO card must be a no-op.
        vm.onAction(UpgradeAction.SelectTier(SubscriptionTier.PRO))
        runCurrent()

        assertEquals(SubscriptionTier.ATELIER, vm.state.value.selectedTier)
    }

    @Test
    fun selectTier_allowsUpgradeTier() = runTest {
        // PRO user → initialState defaults selectedTier to ATELIER.
        entitlements = FakeEntitlementsProvider(SubscriptionTier.PRO)
        val vm = newVm()

        vm.onAction(UpgradeAction.SelectTier(SubscriptionTier.ATELIER))
        runCurrent()

        assertEquals(SubscriptionTier.ATELIER, vm.state.value.selectedTier)
    }

    @Test
    fun paystackPrices_nigerianWorkshop_showsNairaSymbolAndNoCurrencyNote() = runTest {
        val vm = newVm(country = Country.NG)
        runCurrent()

        val state = vm.state.value
        assertEquals("₦2,000", state.paystackPrices.getValue(SubscriptionTier.PRO).monthly)
        assertEquals("₦20,000", state.paystackPrices.getValue(SubscriptionTier.PRO).annual)
        assertEquals("₦4,000", state.paystackPrices.getValue(SubscriptionTier.PRO).annualSaving)
        assertEquals("₦4,000", state.paystackPrices.getValue(SubscriptionTier.ATELIER).monthly)
        assertNull(state.paystackPrices[SubscriptionTier.FREE])
        assertFalse(state.showBillingCurrencyNote)
    }

    @Test
    fun paystackPrices_ghanaianWorkshop_keepsRealNairaAmountLabelledWithIsoCode() = runTest {
        val vm = newVm(country = Country.GH)
        runCurrent()

        val state = vm.state.value
        // Never converted into cedis, never a bare ₦: the charge really is NGN 2,000.
        assertEquals("NGN 2,000", state.paystackPrices.getValue(SubscriptionTier.PRO).monthly)
        assertEquals("NGN 40,000", state.paystackPrices.getValue(SubscriptionTier.ATELIER).annual)
        assertEquals("NGN 8,000", state.paystackPrices.getValue(SubscriptionTier.ATELIER).annualSaving)
        assertTrue(state.showBillingCurrencyNote)
    }

    @Test
    fun paystackPrices_followWorkshopCountryChange() = runTest {
        val countries = FakeCurrentCountryProvider(Country.NG)
        val vm = newVm(countryProvider = countries)
        runCurrent()
        assertEquals("₦2,000", vm.state.value.paystackPrices.getValue(SubscriptionTier.PRO).monthly)

        countries.load(Country.SN)
        runCurrent()

        assertEquals("NGN 2,000", vm.state.value.paystackPrices.getValue(SubscriptionTier.PRO).monthly)
        assertTrue(vm.state.value.showBillingCurrencyNote)
    }

    // billingEnabled defaults true so the existing Paystack-checkout tests exercise
    // the live path; the gate tests pass false explicitly.
    private fun newVm(
        billingEnabled: Boolean = true,
        country: Country = Country.DEFAULT,
        countryProvider: FakeCurrentCountryProvider = FakeCurrentCountryProvider(country),
    ): UpgradeViewModel = UpgradeViewModel(
        entitlements = entitlements,
        paymentRepository = payments,
        pendingDeepLink = pendingDeepLink,
        analytics = FakeAnalytics(),
        appConfigRepository = FakeAppConfigRepository(
            AppConfig(
                communityEnabled = false,
                communityInviteUrl = null,
                billingEnabled = billingEnabled,
            ),
        ),
        currentCountryProvider = countryProvider,
    )

    private class FakePaymentRepository : PaymentRepository {
        var result: Result<CheckoutOutcome, PaymentError> = Result.Success(
            CheckoutOutcome.Redirect(
                authorizationUrl = "https://checkout.paystack.com/stitchpad",
                reference = "ref",
            )
        )
        var deferred: CompletableDeferred<Result<CheckoutOutcome, PaymentError>>? = null
        var lastTier: SubscriptionTier? = null
        var lastCadence: BillingCadence? = null

        var catalog: Result<Map<String, String>, PaymentError> = Result.Success(emptyMap())
        var restoreResult: Result<CheckoutOutcome, PaymentError> = Result.Success(CheckoutOutcome.Cancelled)

        override suspend fun startCheckout(
            tier: SubscriptionTier,
            cadence: BillingCadence,
        ): Result<CheckoutOutcome, PaymentError> {
            lastTier = tier
            lastCadence = cadence
            return deferred?.await() ?: result
        }

        override suspend fun productCatalog(): Result<Map<String, String>, PaymentError> = catalog

        override suspend fun restorePurchases(): Result<CheckoutOutcome, PaymentError> = restoreResult
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
