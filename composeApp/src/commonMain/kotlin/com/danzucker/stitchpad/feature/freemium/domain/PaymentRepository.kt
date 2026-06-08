package com.danzucker.stitchpad.feature.freemium.domain

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier

interface PaymentRepository {
    suspend fun initializeSubscriptionCheckout(
        tier: SubscriptionTier,
        cadence: BillingCadence,
    ): Result<CheckoutSession, PaymentError>
}

data class CheckoutSession(
    val authorizationUrl: String,
    val reference: String,
)
