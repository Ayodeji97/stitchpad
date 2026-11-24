package com.danzucker.stitchpad.feature.freemium.data

import com.danzucker.stitchpad.core.domain.session.CurrentCountryProvider
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.freemium.domain.CheckoutProvider
import com.danzucker.stitchpad.feature.freemium.domain.checkoutProviderFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Catches Play purchases that completed outside the purchase sheet: pending
 * cash/carrier payments that clear later (often with the app closed), and any
 * purchase whose first verify failed. Started by MainActivity: [sweep] on every
 * foreground, [observePurchaseUpdates] for updates while the app runs. Only acts
 * for a signed-in user whose workshop is on the Play path.
 */
internal class PlayPurchaseSweeper(
    private val repository: PlayBillingPaymentRepository,
    private val client: NativePlayBillingClient,
    private val countryProvider: CurrentCountryProvider,
    private val authRepository: AuthRepository,
) {

    suspend fun sweep() {
        if (!authRepository.isLoggedIn) return
        // Waits briefly for the workshop country; never falls back to NG.
        val country = countryProvider.countryForWrite()
        if (country != null && checkoutProviderFor(isIos = false, country = country) == CheckoutProvider.PLAY) {
            repository.verifyUnacknowledged()
        }
    }

    fun observePurchaseUpdates(scope: CoroutineScope): Job = scope.launch {
        client.purchaseUpdates.collect { sweep() }
    }
}
