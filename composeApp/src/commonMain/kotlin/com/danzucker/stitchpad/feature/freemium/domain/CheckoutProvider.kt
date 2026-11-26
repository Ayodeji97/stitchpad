package com.danzucker.stitchpad.feature.freemium.domain

import com.danzucker.stitchpad.core.domain.model.AndroidBilling
import com.danzucker.stitchpad.core.domain.model.Country

/** Which checkout path a purchase takes. */
enum class CheckoutProvider { PAYSTACK, APPLE, PLAY }

/**
 * iOS always buys through Apple. Android follows the workshop country:
 * [AndroidBilling.PAYSTACK] (Nigeria) uses Paystack, [AndroidBilling.PLAY]
 * (every other wave-1 market) uses Google Play Billing.
 */
fun checkoutProviderFor(isIos: Boolean, country: Country): CheckoutProvider = when {
    isIos -> CheckoutProvider.APPLE
    country.androidBilling == AndroidBilling.PLAY -> CheckoutProvider.PLAY
    else -> CheckoutProvider.PAYSTACK
}
