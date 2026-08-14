package com.danzucker.stitchpad.feature.dashboard.domain

/** Deadline within this many days (inclusive, 0 = today) counts as [UrgencyLevel.SOON]. */
private const val SOON_WINDOW_DAYS = 3

/**
 * Calibrated urgency tier for the staff dashboard focus-queue's chip (hero +
 * every ticket card). Replaces the old "every card red" alarm-fatigue design —
 * only genuinely late orders read as critical.
 */
enum class UrgencyLevel { LATE, SOON, OK }

/**
 * Maps a row's deadline fields to its [UrgencyLevel]:
 *  - `LATE`: [daysLate] is positive (the order is actually overdue).
 *  - `SOON`: not late, and [daysUntilDeadline] is within [SOON_WINDOW_DAYS]
 *    (0 = due today).
 *  - `OK`: everything else — no deadline, or a deadline further out.
 *
 * [daysLate] and [daysUntilDeadline] are mutually exclusive by construction
 * (see `BucketCalculator.toQueueRow`), but LATE is still checked first so the
 * mapping stays correct even if a caller somehow supplies both.
 */
fun resolveUrgencyLevel(daysLate: Int?, daysUntilDeadline: Int?): UrgencyLevel = when {
    (daysLate ?: 0) > 0 -> UrgencyLevel.LATE
    daysUntilDeadline != null && daysUntilDeadline in 0..SOON_WINDOW_DAYS -> UrgencyLevel.SOON
    else -> UrgencyLevel.OK
}
