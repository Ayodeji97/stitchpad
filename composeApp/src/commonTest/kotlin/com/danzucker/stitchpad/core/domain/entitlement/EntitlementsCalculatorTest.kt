package com.danzucker.stitchpad.core.domain.entitlement

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class EntitlementsCalculatorTest {

    private val tz = TimeZone.of("Africa/Lagos")

    @Test
    fun free_user_no_welcome_has_15_customer_cap_and_5_coins() {
        val now = Instant.parse("2026-05-17T08:00:00Z")
        val e = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = null,
            now = now,
            timeZone = tz,
        )
        assertEquals(SubscriptionTier.FREE, e.tier)
        assertEquals(15, e.customerCap)
        assertEquals(5, e.smartCoinAllowance)
        assertFalse(e.isInWelcomeWindow)
    }

    @Test
    fun free_user_in_welcome_window_has_first_month_cap_and_welcomeEndsAt_is_30_days_after_signup() {
        // Rolling 30-day welcome (per V1.0 design spec, updated 2026-05-22).
        // Signed up May 5; window ends exactly June 4 at the same UTC time.
        val signedUp = Instant.parse("2026-05-05T10:00:00Z")
        val now = Instant.parse("2026-05-17T08:00:00Z")
        val e = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = signedUp,
            now = now,
            timeZone = tz,
        )
        // First-month cap = WELCOME_CUSTOMER_CAP (200). The exact value matters
        // because reconcileSlots.ts:effectiveCap reads the same constant; if
        // either side drifts, customers get locked the client just allowed.
        assertEquals(EntitlementsCalculator.WELCOME_CUSTOMER_CAP, e.customerCap)
        assertEquals(200, e.customerCap)
        assertTrue(e.isInWelcomeWindow)
        // Rolling 30 days: signedUp + WELCOME_WINDOW_DAYS = welcomeEndsAt.
        // Pin both the named constant and the literal so silent constant bumps fail.
        assertEquals(30, EntitlementsCalculator.WELCOME_WINDOW_DAYS)
        val expectedEnd = Instant.parse("2026-06-04T10:00:00Z")
        assertEquals(expectedEnd, e.welcomeEndsAt)
    }

    @Test
    fun late_month_signup_still_gets_full_30_days_first_month() {
        // The bug the rolling-window model fixes: under calendar-month logic, a
        // May 28 signup only got 3 days of First Month. Under rolling-30, they
        // get 30 days same as anyone.
        val signedUp = Instant.parse("2026-05-28T10:00:00Z")
        val now = Instant.parse("2026-06-15T10:00:00Z") // 18 days post-signup
        val e = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = signedUp,
            now = now,
            timeZone = tz,
        )
        assertTrue(e.isInWelcomeWindow, "May 28 signup must still be in First Month 18 days later")
        assertEquals(200, e.customerCap)
        // Window ends June 27, not June 1 (which calendar-month logic would have given).
        assertEquals(Instant.parse("2026-06-27T10:00:00Z"), e.welcomeEndsAt)
    }

    @Test
    fun free_user_past_welcome_window_drops_back_to_15_cap() {
        val signedUp = Instant.parse("2026-04-10T10:00:00Z")
        // Exactly 31 days later — window closed at day 30.
        val now = Instant.parse("2026-05-11T10:00:01Z")
        val e = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = signedUp,
            now = now,
            timeZone = tz,
        )
        assertEquals(15, e.customerCap)
        assertFalse(e.isInWelcomeWindow)
    }

    @Test
    fun welcome_window_is_exclusive_at_exact_boundary_instant() {
        // Mirrors the TS test `isInWelcomeWindow returns false exactly 30 days after
        // signup`. The boundary is exclusive (`now < welcomeEndsAt`), so at the
        // exact 30-day instant the window is closed.
        val signedUp = Instant.parse("2026-04-10T10:00:00Z")
        val exactlyAtBoundary = Instant.parse("2026-05-10T10:00:00Z") // signedUp + 30 days
        val justInside = Instant.parse("2026-05-10T09:59:59.999Z")

        val atBoundary = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = signedUp,
            now = exactlyAtBoundary,
            timeZone = tz,
        )
        val insideBoundary = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = signedUp,
            now = justInside,
            timeZone = tz,
        )

        assertFalse(atBoundary.isInWelcomeWindow, "At exact 30-day instant, window is closed")
        assertTrue(insideBoundary.isInWelcomeWindow, "1ms before the boundary is still inside")
    }

    @Test
    fun pro_user_has_unlimited_customers_and_50_coins() {
        val e = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.PRO,
            welcomeBonusAppliedAt = null,
            now = Instant.parse("2026-05-17T08:00:00Z"),
            timeZone = tz,
        )
        assertEquals(Int.MAX_VALUE, e.customerCap)
        assertEquals(50, e.smartCoinAllowance)
    }

    @Test
    fun atelier_user_has_unlimited_customers_and_500_coins() {
        val e = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.ATELIER,
            welcomeBonusAppliedAt = null,
            now = Instant.parse("2026-05-17T08:00:00Z"),
            timeZone = tz,
        )
        assertEquals(Int.MAX_VALUE, e.customerCap)
        assertEquals(500, e.smartCoinAllowance)
    }

    @Test
    fun isWithinWelcomeEndingWarning_true_when_three_days_or_less_remain() {
        // Signed up May 5 → window ends June 4 → 2 days before that = June 2.
        val signedUp = Instant.parse("2026-05-05T10:00:00Z")
        val now = Instant.parse("2026-06-02T10:00:00Z")
        val e = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = signedUp,
            now = now,
            timeZone = tz,
        )
        assertTrue(e.isWithinWelcomeEndingWarning)
    }

    @Test
    fun isWithinWelcomeEndingWarning_false_when_more_than_three_days_remain() {
        // Signed up May 5 → window ends June 4 → May 20 = 15 days before end.
        val signedUp = Instant.parse("2026-05-05T10:00:00Z")
        val now = Instant.parse("2026-05-20T08:00:00Z")
        val e = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = signedUp,
            now = now,
            timeZone = tz,
        )
        assertFalse(e.isWithinWelcomeEndingWarning)
    }

    // --- canUseCustomMeasurements ---

    @Test
    fun canUseCustomMeasurements_isTrue_forPro() {
        val result = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.PRO,
            welcomeBonusAppliedAt = null,
            now = Instant.fromEpochMilliseconds(1_748_275_200_000L),
            timeZone = TimeZone.of("Africa/Lagos"),
        )
        assertTrue(result.canUseCustomMeasurements)
    }

    @Test
    fun canUseCustomMeasurements_isTrue_forAtelier() {
        val result = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.ATELIER,
            welcomeBonusAppliedAt = null,
            now = Instant.fromEpochMilliseconds(1_748_275_200_000L),
            timeZone = TimeZone.of("Africa/Lagos"),
        )
        assertTrue(result.canUseCustomMeasurements)
    }

    @Test
    fun canUseCustomMeasurements_isTrue_forFreeInsideWelcomeWindow() {
        val signup = Instant.fromEpochMilliseconds(1_748_275_200_000L)
        val result = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = signup,
            now = signup.plus(5.days),
            timeZone = TimeZone.of("Africa/Lagos"),
        )
        assertTrue(result.canUseCustomMeasurements)
    }

    @Test
    fun canUseCustomMeasurements_isFalse_forFreePostWelcome() {
        val signup = Instant.fromEpochMilliseconds(1_748_275_200_000L)
        val result = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = signup,
            now = signup.plus(40.days),  // welcome window has ended
            timeZone = TimeZone.of("Africa/Lagos"),
        )
        assertFalse(result.canUseCustomMeasurements)
    }

    @Test
    fun canUseCustomMeasurements_isFalse_forFreeWithNoWelcome() {
        val result = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = null,
            now = Instant.fromEpochMilliseconds(1_748_275_200_000L),
            timeZone = TimeZone.of("Africa/Lagos"),
        )
        assertFalse(result.canUseCustomMeasurements)
    }
}
