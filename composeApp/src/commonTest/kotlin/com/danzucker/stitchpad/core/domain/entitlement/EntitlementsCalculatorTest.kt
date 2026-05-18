package com.danzucker.stitchpad.core.domain.entitlement

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun free_user_in_welcome_window_has_30_cap_and_welcomeEndsAt_is_end_of_signup_month() {
        // Signed up May 5 2026 in Lagos → welcome window covers all of May.
        val signedUp = Instant.parse("2026-05-05T10:00:00Z")
        val now = Instant.parse("2026-05-17T08:00:00Z")
        val e = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = signedUp,
            now = now,
            timeZone = tz,
        )
        assertEquals(30, e.customerCap)
        assertTrue(e.isInWelcomeWindow)
        // Welcome window ends at the END of the calendar month (last instant of May).
        // Equivalent to "start of June" in Lagos zone.
        val expectedEnd = LocalDate(2026, 6, 1).atTime(0, 0)
            .toInstant(tz)
        assertEquals(expectedEnd, e.welcomeEndsAt)
    }

    @Test
    fun free_user_past_welcome_window_drops_back_to_15_cap() {
        val signedUp = Instant.parse("2026-04-10T10:00:00Z")
        val now = Instant.parse("2026-05-01T00:00:01Z") // 1 second into May
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
        val signedUp = Instant.parse("2026-05-05T10:00:00Z")
        // 2 days before end of May
        val now = Instant.parse("2026-05-29T20:00:00Z")
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
}
