package com.danzucker.stitchpad.feature.reports.domain.model

/**
 * Tier badge displayed beside a top customer's name on the Reports tab.
 * Computed from lifetime stats, not period-scoped — badges describe the
 * overall customer relationship.
 */
enum class CustomerBadge {
    /** Lifetime orders >= 5 OR lifetime spend >= ₦200,000. */
    VIP,

    /** Lifetime orders >= 2 (and not VIP). */
    REPEAT,

    /** Below all thresholds. */
    NONE
}
