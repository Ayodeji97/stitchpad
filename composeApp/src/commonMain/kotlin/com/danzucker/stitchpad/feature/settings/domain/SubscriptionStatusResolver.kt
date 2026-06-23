package com.danzucker.stitchpad.feature.settings.domain

import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlinx.datetime.Instant

enum class SubscriptionStatusKind { RENEWS, EXPIRES_MONTHS, EXPIRES_DAYS, EXPIRES_TOMORROW, EXPIRES_TODAY }

data class SubscriptionStatus(
    val kind: SubscriptionStatusKind,
    val endsAt: Instant,
    val count: Int = 0,
)

/** Resolves the Settings plan-card status line from entitlements; null = show nothing (Free / no end date). */
@Suppress("ReturnCount")
fun resolveSubscriptionStatus(entitlements: UserEntitlements): SubscriptionStatus? {
    val endsAt = entitlements.subscriptionEndsAt ?: return null
    if (entitlements.tier == SubscriptionTier.FREE) return null
    if (entitlements.subscriptionRenews) {
        return SubscriptionStatus(SubscriptionStatusKind.RENEWS, endsAt)
    }
    val days = entitlements.subscriptionDaysLeft ?: return null
    val months = entitlements.subscriptionMonthsLeft ?: 0
    return when {
        days <= 0 -> SubscriptionStatus(SubscriptionStatusKind.EXPIRES_TODAY, endsAt)
        days == 1 -> SubscriptionStatus(SubscriptionStatusKind.EXPIRES_TOMORROW, endsAt)
        months >= 1 -> SubscriptionStatus(SubscriptionStatusKind.EXPIRES_MONTHS, endsAt, months)
        else -> SubscriptionStatus(SubscriptionStatusKind.EXPIRES_DAYS, endsAt, days)
    }
}
