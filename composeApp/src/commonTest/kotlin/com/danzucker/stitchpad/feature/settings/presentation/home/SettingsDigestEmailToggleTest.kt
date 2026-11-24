package com.danzucker.stitchpad.feature.settings.presentation.home

import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsCalculator
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The weekly digest email is paid-only, so only paid owners see its switch. */
class SettingsDigestEmailToggleTest {
    private val tz = TimeZone.of("Africa/Lagos")
    private val now = Instant.parse("2026-11-24T08:00:00Z")
    private val nextMonth = Instant.parse("2026-12-24T08:00:00Z")

    private fun entitlements(tier: SubscriptionTier, endsAt: Instant?) =
        EntitlementsCalculator.calculate(
            tier = tier, welcomeBonusAppliedAt = null, now = now, timeZone = tz,
            subscriptionEndsAt = endsAt,
        )

    @Test
    fun paid_plan_with_end_date_shows_the_switch() {
        assertTrue(showsDigestEmailToggle(entitlements(SubscriptionTier.PRO, nextMonth)))
        assertTrue(showsDigestEmailToggle(entitlements(SubscriptionTier.ATELIER, nextMonth)))
    }

    @Test
    fun launch_grant_without_end_date_hides_the_switch() {
        assertFalse(showsDigestEmailToggle(entitlements(SubscriptionTier.ATELIER, null)))
    }

    @Test
    fun free_owner_hides_the_switch() {
        assertFalse(showsDigestEmailToggle(entitlements(SubscriptionTier.FREE, null)))
    }
}
