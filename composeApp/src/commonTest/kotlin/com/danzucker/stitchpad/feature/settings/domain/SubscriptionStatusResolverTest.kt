package com.danzucker.stitchpad.feature.settings.domain

import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val DUMMY_ENDS_AT = Instant.fromEpochMilliseconds(1_750_000_000_000)

/** Builds a [UserEntitlements] with sensible defaults; override only what each test cares about. */
private fun entitlements(
    tier: SubscriptionTier = SubscriptionTier.PRO,
    subscriptionEndsAt: Instant? = DUMMY_ENDS_AT,
    subscriptionRenews: Boolean = false,
    subscriptionDaysLeft: Int? = null,
    subscriptionMonthsLeft: Int? = null,
) = UserEntitlements(
    tier = tier,
    customerCap = Int.MAX_VALUE,
    smartCoinAllowance = 5,
    isInWelcomeWindow = false,
    welcomeEndsAt = null,
    isWithinWelcomeEndingWarning = false,
    welcomeDaysLeft = null,
    canUseCustomMeasurements = true,
    subscriptionEndsAt = subscriptionEndsAt,
    subscriptionRenews = subscriptionRenews,
    subscriptionDaysLeft = subscriptionDaysLeft,
    subscriptionMonthsLeft = subscriptionMonthsLeft,
)

class SubscriptionStatusResolverTest {

    @Test
    fun freeTierWithEndsAt_returnsNull() {
        val result = resolveSubscriptionStatus(entitlements(tier = SubscriptionTier.FREE))
        assertNull(result)
    }

    @Test
    fun paidWithNullEndsAt_returnsNull() {
        val result = resolveSubscriptionStatus(entitlements(subscriptionEndsAt = null))
        assertNull(result)
    }

    @Test
    fun paidRenewing_returnsRenews() {
        val result = resolveSubscriptionStatus(entitlements(subscriptionRenews = true))
        assertEquals(SubscriptionStatusKind.RENEWS, result?.kind)
        assertEquals(DUMMY_ENDS_AT, result?.endsAt)
    }

    @Test
    fun nonRenewing_95days_3months_returnsExpiresMonthsCount3() {
        val result = resolveSubscriptionStatus(
            entitlements(subscriptionDaysLeft = 95, subscriptionMonthsLeft = 3),
        )
        assertEquals(SubscriptionStatusKind.EXPIRES_MONTHS, result?.kind)
        assertEquals(3, result?.count)
    }

    @Test
    fun nonRenewing_12days_0months_returnsExpiresDaysCount12() {
        val result = resolveSubscriptionStatus(
            entitlements(subscriptionDaysLeft = 12, subscriptionMonthsLeft = 0),
        )
        assertEquals(SubscriptionStatusKind.EXPIRES_DAYS, result?.kind)
        assertEquals(12, result?.count)
    }

    @Test
    fun nonRenewing_1day_returnsExpiresTomorrow() {
        val result = resolveSubscriptionStatus(entitlements(subscriptionDaysLeft = 1))
        assertEquals(SubscriptionStatusKind.EXPIRES_TOMORROW, result?.kind)
    }

    @Test
    fun nonRenewing_0days_returnsExpiresToday() {
        val result = resolveSubscriptionStatus(entitlements(subscriptionDaysLeft = 0))
        assertEquals(SubscriptionStatusKind.EXPIRES_TODAY, result?.kind)
    }

    @Test
    fun nonRenewing_negativeDays_returnsExpiresToday() {
        val result = resolveSubscriptionStatus(entitlements(subscriptionDaysLeft = -2))
        assertEquals(SubscriptionStatusKind.EXPIRES_TODAY, result?.kind)
    }

    @Test
    fun nonRenewing_31days_1month_returnsExpiresMonthsCount1() {
        val result = resolveSubscriptionStatus(
            entitlements(subscriptionDaysLeft = 31, subscriptionMonthsLeft = 1),
        )
        assertEquals(SubscriptionStatusKind.EXPIRES_MONTHS, result?.kind)
        assertEquals(1, result?.count)
    }
}
