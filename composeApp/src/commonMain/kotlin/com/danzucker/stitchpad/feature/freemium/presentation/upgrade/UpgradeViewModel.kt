package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence
import com.danzucker.stitchpad.feature.freemium.domain.PaymentRepository
import com.danzucker.stitchpad.feature.freemium.presentation.toUiText
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UpgradeViewModel(
    private val entitlements: EntitlementsProvider,
    private val paymentRepository: PaymentRepository,
    pendingDeepLink: PendingDeepLinkHolder,
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
            entitlements.flow.collect { e ->
                _state.update { it.copy(currentTier = e.tier) }
                if (e.tier.isHigherThan(startingTier)) {
                    _events.send(UpgradeEvent.UpgradeDetected)
                }
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
            return UpgradeState(currentTier = currentTier, selectedTier = selectedTier, billingCadence = cadence)
        }
    }

    fun onAction(action: UpgradeAction) {
        // Ignore plan changes once a checkout is in flight: otherwise the user
        // could switch tier/cadence while the spinner shows and be sent to a
        // Paystack session for the plan captured at tap time while the UI shows
        // the new selection. The Pay button is already disabled via isStartingCheckout.
        when (action) {
            is UpgradeAction.SelectTier ->
                if (!_state.value.isStartingCheckout) _state.update { it.copy(selectedTier = action.tier) }
            is UpgradeAction.SelectCadence ->
                if (!_state.value.isStartingCheckout) _state.update { it.copy(billingCadence = action.cadence) }
            UpgradeAction.PayWithPaystack -> {
                if (_state.value.isStartingCheckout) return
                if (_state.value.selectedTier == SubscriptionTier.FREE) return
                _state.update { it.copy(showCheckoutConfirmSheet = true) }
            }
            UpgradeAction.ConfirmCheckout -> {
                _state.update { it.copy(showCheckoutConfirmSheet = false) }
                startCheckout()
            }
            UpgradeAction.DismissCheckoutSheet ->
                _state.update { it.copy(showCheckoutConfirmSheet = false) }
        }
    }

    private fun startCheckout() {
        val s = _state.value
        if (s.isStartingCheckout) return
        if (s.selectedTier == SubscriptionTier.FREE) return
        _state.update { it.copy(isStartingCheckout = true) }
        viewModelScope.launch {
            when (
                val result = paymentRepository.initializeSubscriptionCheckout(
                    tier = s.selectedTier,
                    cadence = s.billingCadence,
                )
            ) {
                is Result.Success -> {
                    _state.update { it.copy(isStartingCheckout = false) }
                    _events.send(UpgradeEvent.OpenExternalBrowser(result.data.authorizationUrl))
                }
                is Result.Error -> {
                    _state.update { it.copy(isStartingCheckout = false) }
                    _events.send(UpgradeEvent.ShowSnackbar(result.error.toUiText()))
                }
            }
        }
    }
}
