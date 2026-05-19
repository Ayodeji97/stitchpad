package com.danzucker.stitchpad.core.domain.entitlement

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlinx.datetime.Instant

/**
 * Snapshot of what the current user is entitled to RIGHT NOW. Derived
 * purely from Firestore user-doc fields by [EntitlementsCalculator] — no
 * I/O, no time-of-call dependencies beyond the `now` parameter.
 *
 * Treat this as a value object: produce a fresh one whenever the user
 * doc changes or the welcome window crosses a boundary.
 */
data class UserEntitlements(
    val tier: SubscriptionTier,
    /** Max active customers. `Int.MAX_VALUE` for Pro and Atelier. */
    val customerCap: Int,
    /** Monthly Smart-coin allowance (excluding bonus pool). */
    val smartCoinAllowance: Int,
    /** True when the user is still inside their signup welcome window. */
    val isInWelcomeWindow: Boolean,
    /**
     * When the welcome window expires (start of the next calendar month
     * after signup). `null` once the window has ended or was never
     * applied.
     */
    val welcomeEndsAt: Instant?,
    /**
     * True when [welcomeEndsAt] is non-null AND within 3 days. Drives
     * the "your welcome is ending" dashboard banner.
     */
    val isWithinWelcomeEndingWarning: Boolean,
    /**
     * Calendar days (Africa/Lagos) until [welcomeEndsAt]. Null when no
     * welcome window applies or once it has ended. Always computed from
     * the same Lagos calendar math as [isWithinWelcomeEndingWarning] so
     * banner copy ("N days left") and the show/hide flag never disagree.
     */
    val welcomeDaysLeft: Int?,
)
