package com.danzucker.stitchpad.feature.gift.domain

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence

/** A gift the caller has just claimed onto their own account. */
data class RedeemedGift(
    val tier: SubscriptionTier,
    val cadence: BillingCadence,
)

/** The signed-in tailor's personal "Gift me" link (and its raw token). */
data class GiftLink(
    val token: String,
    val url: String,
)

/**
 * Client side of the gift feature. Buying a gift happens on the web; the app only
 * claims a bearer code and mints/shares the tailor's own "Gift me" link.
 */
interface GiftRepository {
    /** Redeems a bearer gift [code] onto the signed-in account. */
    suspend fun redeemGift(code: String): Result<RedeemedGift, GiftError>

    /** Returns the tailor's gift link, minting it on first call (idempotent server-side). */
    suspend fun getOrCreateGiftLink(): Result<GiftLink, GiftError>
}
