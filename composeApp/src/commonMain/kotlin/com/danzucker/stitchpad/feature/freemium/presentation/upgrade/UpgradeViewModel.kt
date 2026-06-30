package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.config.domain.repository.AppConfigRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.legal.LegalUrls
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence
import com.danzucker.stitchpad.feature.freemium.domain.CheckoutOutcome
import com.danzucker.stitchpad.feature.freemium.domain.PaymentRepository
import com.danzucker.stitchpad.feature.freemium.presentation.toUiText
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import com.danzucker.stitchpad.util.Platform
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.upgrade_purchase_pending

class UpgradeViewModel(
    private val entitlements: EntitlementsProvider,
    private val paymentRepository: PaymentRepository,
    pendingDeepLink: PendingDeepLinkHolder,
    private val analytics: Analytics,
    appConfigRepository: AppConfigRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState(entitlements, pendingDeepLink))
    val state: StateFlow<UpgradeState> = _state.asStateFlow()

    private val _events = Channel<UpgradeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // Keep currentTier in sync with the provider's live flow so the
        // PlanCard-style header on this screen updates when the upgrade
        // lands (Paystack webhook → user-doc tier change → EntitlementsProvider
        // re-emit). Also fires a UpgradeDetected event when tier rises so the
        // Root composable can pop back to Settings instead of stranding the
        // user on a now-irrelevant upgrade picker.
        viewModelScope.launch {
            val startingTier = entitlements.current().tier
            var lastTier = startingTier
            entitlements.flow.collect { e ->
                _state.update { it.copy(currentTier = e.tier) }
                if (e.tier.isHigherThan(startingTier)) {
                    _events.send(UpgradeEvent.UpgradeDetected)
                }
                if (e.tier.isHigherThan(lastTier)) {
                    analytics.logEvent(AnalyticsEvent.UpgradeCompleted(tier = e.tier.name.lowercase()))
                }
                lastTier = e.tier
            }
        }

        // Load Apple's localized prices so the tier cards show what the App Store
        // actually charges. No-ops on Android (empty catalog → bundled NGN strings).
        viewModelScope.launch {
            (paymentRepository.productCatalog() as? Result.Success)?.let { catalog ->
                _state.update { it.copy(appleDisplayPrices = catalog.data) }
            }
        }

        // Track the server billing kill switch so the Paystack CTA flips from
        // "Coming soon" to live the moment config/app.billingEnabled is set true —
        // no app release. Default AppConfig.Disabled keeps billingEnabled false.
        viewModelScope.launch {
            appConfigRepository.config.collect { cfg ->
                _state.update { it.copy(billingEnabled = cfg.billingEnabled) }
            }
        }
    }

    private fun SubscriptionTier.isHigherThan(other: SubscriptionTier): Boolean =
        ordinal > other.ordinal

    private companion object {
        /**
         * A renewal deep link (stitchpad://upgrade?tier=&cadence=) carries the plan the
         * tailor is renewing, so pre-select that tier + cadence — one tap from paying.
         * Falls back to the upsell default (Free → Pro, Pro → Atelier) for a normal
         * upgrade with no pre-select. A pre-selected FREE is ignored (you don't renew to
         * Free). The pre-select is consumed once so it doesn't stick across navigations.
         */
        fun initialState(
            entitlements: EntitlementsProvider,
            pendingDeepLink: PendingDeepLinkHolder,
        ): UpgradeState {
            val preselect = pendingDeepLink.consumeUpgradePreselect()
            val currentTier = entitlements.current().tier
            val preselectedTier = preselect?.tier
                ?.let { SubscriptionTier.fromWire(it) }
                ?.takeIf { it != SubscriptionTier.FREE }
            val selectedTier = preselectedTier
                ?: if (currentTier == SubscriptionTier.FREE) SubscriptionTier.PRO else SubscriptionTier.ATELIER
            val cadence = preselect?.cadence?.let { BillingCadence.fromWire(it) } ?: BillingCadence.MONTHLY
            return UpgradeState(
                currentTier = currentTier,
                selectedTier = selectedTier,
                billingCadence = cadence,
                // iOS must purchase through Apple IAP (Guideline 3.1.1); Android
                // continues to use Paystack. The binding is platform-specific, but
                // the screen also needs the provider to pick the CTA copy.
                checkoutProvider = if (Platform.isIos) CheckoutProvider.APPLE else CheckoutProvider.PAYSTACK,
            )
        }
    }

    fun onAction(action: UpgradeAction) {
        // Ignore plan changes once a checkout is in flight: otherwise the user
        // could switch tier/cadence while the spinner shows and be charged for the
        // plan captured at tap time while the UI shows the new selection. The Pay
        // button is already disabled via isStartingCheckout.
        when (action) {
            is UpgradeAction.SelectTier ->
                if (!_state.value.isStartingCheckout && action.tier != _state.value.currentTier) {
                    _state.update { it.copy(selectedTier = action.tier) }
                }
            is UpgradeAction.SelectCadence ->
                if (!_state.value.isStartingCheckout) _state.update { it.copy(billingCadence = action.cadence) }
            UpgradeAction.StartCheckout -> startCheckout()
            UpgradeAction.RestorePurchases -> restorePurchases()
            UpgradeAction.OnPrivacyClick ->
                viewModelScope.launch { _events.send(UpgradeEvent.OpenExternalBrowser(LegalUrls.PRIVACY)) }
            UpgradeAction.OnTermsClick ->
                viewModelScope.launch { _events.send(UpgradeEvent.OpenExternalBrowser(LegalUrls.TERMS)) }
        }
    }

    private fun restorePurchases() {
        if (_state.value.isStartingCheckout) return
        _state.update { it.copy(isStartingCheckout = true) }
        viewModelScope.launch {
            val result = paymentRepository.restorePurchases()
            _state.update { it.copy(isStartingCheckout = false) }
            when (result) {
                // A restored grant flips the tier → the entitlements collector
                // fires UpgradeDetected and pops. Nothing restored / cancelled is a
                // silent no-op; a real failure shows a snackbar.
                is Result.Success -> Unit
                is Result.Error -> _events.send(UpgradeEvent.ShowSnackbar(result.error.toUiText()))
            }
        }
    }

    private fun startCheckout() {
        val s = _state.value
        // Don't start when a checkout is already in flight, the selected plan is
        // Free (nothing to buy), or the Paystack CTA isn't live yet (the "Coming
        // soon" gate; Apple/iOS is always allowed). The Pay button already enforces
        // all three in the UI — this is the backstop for any other trigger.
        if (s.isStartingCheckout || s.selectedTier == SubscriptionTier.FREE || !s.canCheckout) return
        _state.update { it.copy(isStartingCheckout = true) }
        viewModelScope.launch {
            when (
                val result = paymentRepository.startCheckout(
                    tier = s.selectedTier,
                    cadence = s.billingCadence,
                )
            ) {
                is Result.Success -> {
                    _state.update { it.copy(isStartingCheckout = false) }
                    when (val outcome = result.data) {
                        is CheckoutOutcome.Redirect ->
                            _events.send(UpgradeEvent.OpenExternalBrowser(outcome.authorizationUrl))
                        // Apple grant lands as a user-doc tier change; the
                        // entitlements collector above fires UpgradeDetected and
                        // pops back, so no extra event is needed here.
                        CheckoutOutcome.PurchasedAndGranted -> Unit
                        CheckoutOutcome.Pending ->
                            _events.send(
                                UpgradeEvent.ShowSnackbar(
                                    UiText.StringResourceText(Res.string.upgrade_purchase_pending),
                                ),
                            )
                        // User dismissed the native sheet — no charge, no feedback.
                        CheckoutOutcome.Cancelled -> Unit
                    }
                }
                is Result.Error -> {
                    _state.update { it.copy(isStartingCheckout = false) }
                    _events.send(UpgradeEvent.ShowSnackbar(result.error.toUiText()))
                }
            }
        }
    }
}
