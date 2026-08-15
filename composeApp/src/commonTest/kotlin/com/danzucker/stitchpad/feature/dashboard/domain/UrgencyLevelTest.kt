package com.danzucker.stitchpad.feature.dashboard.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class UrgencyLevelTest {

    @Test
    fun positiveDaysLateIsLate() {
        assertEquals(UrgencyLevel.LATE, resolveUrgencyLevel(daysLate = 1, daysUntilDeadline = null))
        assertEquals(UrgencyLevel.LATE, resolveUrgencyLevel(daysLate = 7, daysUntilDeadline = null))
    }

    @Test
    fun dueTodayIsSoon() {
        assertEquals(UrgencyLevel.SOON, resolveUrgencyLevel(daysLate = null, daysUntilDeadline = 0))
    }

    @Test
    fun dueWithinThreeDaysIsSoon() {
        assertEquals(UrgencyLevel.SOON, resolveUrgencyLevel(daysLate = null, daysUntilDeadline = 1))
        assertEquals(UrgencyLevel.SOON, resolveUrgencyLevel(daysLate = null, daysUntilDeadline = 3))
    }

    @Test
    fun dueBeyondThreeDaysIsOk() {
        assertEquals(UrgencyLevel.OK, resolveUrgencyLevel(daysLate = null, daysUntilDeadline = 4))
    }

    @Test
    fun noDeadlineIsOk() {
        assertEquals(UrgencyLevel.OK, resolveUrgencyLevel(daysLate = null, daysUntilDeadline = null))
    }

    @Test
    fun lateAlwaysBeatsSoonEvenIfBothFieldsSomehowSet() {
        // daysLate and daysUntilDeadline are mutually exclusive by construction
        // (BucketCalculator.toQueueRow), but the mapping still resolves LATE
        // first defensively.
        assertEquals(UrgencyLevel.LATE, resolveUrgencyLevel(daysLate = 2, daysUntilDeadline = 1))
    }

    @Test
    fun zeroOrNegativeDaysLateIsNotLate() {
        assertEquals(UrgencyLevel.OK, resolveUrgencyLevel(daysLate = 0, daysUntilDeadline = null))
    }
}
