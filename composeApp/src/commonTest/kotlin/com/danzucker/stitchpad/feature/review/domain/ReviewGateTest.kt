package com.danzucker.stitchpad.feature.review.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewGateTest {
    private val dayMs = 24L * 60 * 60 * 1000
    private val now = 1_000L * dayMs // day 1000 in epoch millis

    private fun eligibleSignals() = ReviewSignals(
        installedAtMillis = now - 5 * dayMs,
        distinctOpenDays = 3,
        ordersCreated = 4,
        lastPromptAtMillis = 0L,
        lastOutcome = ReviewOutcome.NONE,
    )

    @Test
    fun fully_qualified_user_is_eligible() {
        assertTrue(ReviewGate.isEligible(eligibleSignals(), now))
    }

    @Test
    fun brand_new_install_is_not_eligible() {
        val s = eligibleSignals().copy(installedAtMillis = now - 1 * dayMs)
        assertFalse(ReviewGate.isEligible(s, now))
    }

    @Test
    fun unstamped_install_is_not_eligible() {
        val s = eligibleSignals().copy(installedAtMillis = 0L)
        assertFalse(ReviewGate.isEligible(s, now))
    }

    @Test
    fun single_open_day_is_not_eligible() {
        val s = eligibleSignals().copy(distinctOpenDays = 1)
        assertFalse(ReviewGate.isEligible(s, now))
    }

    @Test
    fun too_few_orders_is_not_eligible() {
        val s = eligibleSignals().copy(ordersCreated = 2)
        assertFalse(ReviewGate.isEligible(s, now))
    }

    @Test
    fun rated_user_within_cooldown_is_not_eligible() {
        val s = eligibleSignals().copy(
            lastOutcome = ReviewOutcome.RATED,
            lastPromptAtMillis = now - 100 * dayMs,
        )
        assertFalse(ReviewGate.isEligible(s, now))
    }

    @Test
    fun rated_user_past_cooldown_is_eligible_again() {
        val s = eligibleSignals().copy(
            lastOutcome = ReviewOutcome.RATED,
            lastPromptAtMillis = now - 121 * dayMs,
        )
        assertTrue(ReviewGate.isEligible(s, now))
    }

    @Test
    fun dismissed_user_past_short_cooldown_is_eligible() {
        val s = eligibleSignals().copy(
            lastOutcome = ReviewOutcome.DISMISSED,
            lastPromptAtMillis = now - 31 * dayMs,
        )
        assertTrue(ReviewGate.isEligible(s, now))
    }

    @Test
    fun cooldown_lengths_are_ordered_rated_longest() {
        assertTrue(ReviewGate.cooldownMillisFor(ReviewOutcome.RATED) > ReviewGate.cooldownMillisFor(ReviewOutcome.GAVE_FEEDBACK))
        assertTrue(ReviewGate.cooldownMillisFor(ReviewOutcome.GAVE_FEEDBACK) > ReviewGate.cooldownMillisFor(ReviewOutcome.DISMISSED))
        assertEquals(0L, ReviewGate.cooldownMillisFor(ReviewOutcome.NONE))
    }
}
