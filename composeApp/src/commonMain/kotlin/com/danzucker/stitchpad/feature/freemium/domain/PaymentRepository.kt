package com.danzucker.stitchpad.feature.freemium.domain

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier

interface PaymentRepository {
    /**
     * Starts a subscription purchase for [tier]/[cadence]. The outcome is
     * provider-specific:
     *  - Paystack (Android) returns [CheckoutOutcome.Redirect]; the caller opens
     *    the authorization URL in a browser to complete payment.
     *  - Apple IAP (iOS) completes the purchase in the native sheet and verifies
     *    it server-side, returning [CheckoutOutcome.PurchasedAndGranted], or
     *    [CheckoutOutcome.Pending] for Ask-to-Buy / SCA approval, or
     *    [CheckoutOutcome.Cancelled] if the user dismissed the sheet.
     */
    suspend fun startCheckout(
        tier: SubscriptionTier,
        cadence: BillingCadence,
    ): Result<CheckoutOutcome, PaymentError>

    /**
     * Localized display prices for the subscription products, keyed by
     * [AppleProductIds]. Apple IAP (iOS) must show the price Apple actually
     * charges; Paystack (Android) shows its own NGN price strings, so the Android
     * impl returns an empty map and the UI keeps the bundled price resources.
     */
    suspend fun productCatalog(): Result<Map<String, String>, PaymentError>

    /**
     * Restores previously purchased subscriptions (Apple's required "Restore"
     * affordance). On iOS this re-verifies App Store entitlements server-side; on
     * Android there is nothing to restore (entitlement is account-based), so it
     * returns [CheckoutOutcome.Cancelled].
     */
    suspend fun restorePurchases(): Result<CheckoutOutcome, PaymentError>
}

sealed interface CheckoutOutcome {
    /** Paystack: open [authorizationUrl] in a browser to complete payment. */
    data class Redirect(val authorizationUrl: String, val reference: String) : CheckoutOutcome

    /** Apple IAP: the native purchase completed and the server granted the tier. */
    data object PurchasedAndGranted : CheckoutOutcome

    /** Apple IAP: the purchase is pending external approval (Ask-to-Buy / SCA). */
    data object Pending : CheckoutOutcome

    /** Apple IAP: the user dismissed the native purchase sheet — no error, no grant. */
    data object Cancelled : CheckoutOutcome
}
