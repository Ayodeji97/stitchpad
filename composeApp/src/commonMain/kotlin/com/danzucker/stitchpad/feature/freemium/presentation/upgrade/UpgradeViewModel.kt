package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UpgradeViewModel(
    entitlements: EntitlementsProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(
        UpgradeState(
            currentTier = entitlements.current().tier,
            // Default selection: if currently Free → suggest Pro; if Pro → suggest Atelier.
            selectedTier = if (entitlements.current().tier == SubscriptionTier.FREE) {
                SubscriptionTier.PRO
            } else {
                SubscriptionTier.ATELIER
            },
        )
    )
    val state: StateFlow<UpgradeState> = _state.asStateFlow()

    private val _events = Channel<UpgradeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: UpgradeAction) {
        when (action) {
            is UpgradeAction.SelectTier -> _state.update { it.copy(selectedTier = action.tier) }
            is UpgradeAction.SelectCadence -> _state.update { it.copy(billingCadence = action.cadence) }
            UpgradeAction.PayWithPaystack -> launchPaystack()
        }
    }

    private fun launchPaystack() {
        val s = _state.value
        val amountKobo = when {
            s.selectedTier == SubscriptionTier.PRO && s.billingCadence == BillingCadence.MONTHLY ->
                PRO_MONTHLY_KOBO
            s.selectedTier == SubscriptionTier.PRO && s.billingCadence == BillingCadence.ANNUAL ->
                PRO_ANNUAL_KOBO
            s.selectedTier == SubscriptionTier.ATELIER && s.billingCadence == BillingCadence.MONTHLY ->
                ATELIER_MONTHLY_KOBO
            s.selectedTier == SubscriptionTier.ATELIER && s.billingCadence == BillingCadence.ANNUAL ->
                ATELIER_ANNUAL_KOBO
            else -> return
        }
        // V1.0 placeholder: open a generic Paystack URL with the amount.
        // V1.1 will replace this with a server-issued init that returns a
        // real subscription auth_url tied to the user's email + plan code.
        val url = "https://paystack.com/pay/stitchpad?amount=$amountKobo"
        viewModelScope.launch {
            _events.send(UpgradeEvent.OpenExternalBrowser(url))
        }
    }

    companion object {
        private const val PRO_MONTHLY_KOBO = 200_000
        private const val PRO_ANNUAL_KOBO = 2_000_000
        private const val ATELIER_MONTHLY_KOBO = 400_000
        private const val ATELIER_ANNUAL_KOBO = 4_000_000
    }
}
