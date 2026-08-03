package com.danzucker.stitchpad.feature.review.domain

/**
 * Pure, testable eligibility for the review prompt. Asks a proven, engaged user at a
 * happy moment — never a brand-new install, never inside a per-outcome cooldown.
 */
object ReviewGate {
    const val MIN_DAYS_SINCE_INSTALL = 3
    const val MIN_DISTINCT_OPEN_DAYS = 2
    const val MIN_ORDERS_CREATED = 3

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val COOLDOWN_RATED_DAYS = 120
    private const val COOLDOWN_FEEDBACK_DAYS = 60
    private const val COOLDOWN_DISMISSED_DAYS = 30

    fun cooldownMillisFor(outcome: ReviewOutcome): Long = when (outcome) {
        ReviewOutcome.NONE -> 0L
        ReviewOutcome.RATED -> COOLDOWN_RATED_DAYS * DAY_MS
        ReviewOutcome.GAVE_FEEDBACK -> COOLDOWN_FEEDBACK_DAYS * DAY_MS
        ReviewOutcome.DISMISSED -> COOLDOWN_DISMISSED_DAYS * DAY_MS
    }

    fun isEligible(signals: ReviewSignals, nowMillis: Long): Boolean {
        if (signals.installedAtMillis <= 0L) return false
        val daysSinceInstall = (nowMillis - signals.installedAtMillis) / DAY_MS
        if (daysSinceInstall < MIN_DAYS_SINCE_INSTALL) return false
        if (signals.distinctOpenDays < MIN_DISTINCT_OPEN_DAYS) return false
        if (signals.ordersCreated < MIN_ORDERS_CREATED) return false
        if (signals.lastOutcome != ReviewOutcome.NONE) {
            val cooldown = cooldownMillisFor(signals.lastOutcome)
            if (nowMillis - signals.lastPromptAtMillis < cooldown) return false
        }
        return true
    }
}
