package com.danzucker.stitchpad.feature.settings.presentation.home

import com.danzucker.stitchpad.core.data.repository.FirebaseUserRepository
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for [computeAiDisplay] — the pure function PlanCard reads for AI usage.
 *
 * Covers all three spec branches (Atelier / First Month / Post-First-Month) plus the
 * edge cases the PR-2 review caught: Pro must NOT be lumped with Free (PlanCard short-
 * circuits on tier != FREE upstream of this function, so Pro's numeric output here is
 * dead-arg, but we still test it to lock the contract); First Month with bonusCoins=null
 * must render "0 used" not "30 used"; post-First-Month with remainingMonthlyQuota=null
 * must render "0 used".
 */
class SettingsViewModelAiDisplayTest {

    // ── Atelier ──────────────────────────────────────────────────────────────

    @Test
    fun atelier_returns_null_limit_regardless_of_other_inputs() {
        val d = computeAiDisplay(
            tier = SubscriptionTier.ATELIER,
            isInWelcomeWindow = true,
            smartCoinAllowance = 500,
            bonusCoinsRemaining = 5,
            remainingMonthlyQuota = 10,
        )
        assertNull(d.limit)
        assertEquals(0, d.used)
    }

    @Test
    fun atelier_post_first_month_still_returns_null_limit() {
        val d = computeAiDisplay(
            tier = SubscriptionTier.ATELIER,
            isInWelcomeWindow = false,
            smartCoinAllowance = 500,
            bonusCoinsRemaining = null,
            remainingMonthlyQuota = 250,
        )
        assertNull(d.limit)
        assertEquals(0, d.used)
    }

    // ── First Month ──────────────────────────────────────────────────────────

    @Test
    fun first_month_uses_welcome_bonus_count_as_limit() {
        val d = computeAiDisplay(
            tier = SubscriptionTier.FREE,
            isInWelcomeWindow = true,
            smartCoinAllowance = 5,
            bonusCoinsRemaining = 30,
            remainingMonthlyQuota = 5,
        )
        assertEquals(FirebaseUserRepository.WELCOME_BONUS_COIN_COUNT, d.limit)
        assertEquals(30, d.limit) // pin the literal too so silent constant bumps fail this test
        assertEquals(0, d.used)
    }

    @Test
    fun first_month_used_count_is_bonus_consumed() {
        val d = computeAiDisplay(
            tier = SubscriptionTier.FREE,
            isInWelcomeWindow = true,
            smartCoinAllowance = 5,
            bonusCoinsRemaining = 24,
            remainingMonthlyQuota = 5,
        )
        assertEquals(30, d.limit)
        assertEquals(6, d.used) // 30 - 24 = 6 bonus coins consumed
    }

    @Test
    fun first_month_with_null_bonus_coins_falls_back_to_full_balance() {
        // Pre-V1.0 accounts don't have bonusCoins yet — must NOT render "30 of 30 used".
        val d = computeAiDisplay(
            tier = SubscriptionTier.FREE,
            isInWelcomeWindow = true,
            smartCoinAllowance = 5,
            bonusCoinsRemaining = null,
            remainingMonthlyQuota = 5,
        )
        assertEquals(30, d.limit)
        assertEquals(0, d.used)
    }

    @Test
    fun first_month_with_zero_bonus_means_30_used() {
        val d = computeAiDisplay(
            tier = SubscriptionTier.FREE,
            isInWelcomeWindow = true,
            smartCoinAllowance = 5,
            bonusCoinsRemaining = 0,
            remainingMonthlyQuota = 5,
        )
        assertEquals(30, d.limit)
        assertEquals(30, d.used)
    }

    @Test
    fun first_month_post_first_month_branch_is_NOT_taken_for_free_tier_in_welcome() {
        // Regression guard for the PR-2 review bug: the old code used smartCoinAllowance
        // (= 5) for First Month users, which made the PlanCard show "5 of 5 used" instead
        // of "X of 30 used". This test fails fast if anyone reverts the bug.
        val d = computeAiDisplay(
            tier = SubscriptionTier.FREE,
            isInWelcomeWindow = true,
            smartCoinAllowance = 5,
            bonusCoinsRemaining = 30,
            remainingMonthlyQuota = 5,
        )
        // The OLD wrong behavior would have produced limit = 5.
        assertEquals(30, d.limit, "First Month must use 30-coin bonus limit, not the 5-draft monthly quota")
    }

    // ── Post-First-Month ─────────────────────────────────────────────────────

    @Test
    fun post_first_month_free_uses_smart_coin_allowance() {
        val d = computeAiDisplay(
            tier = SubscriptionTier.FREE,
            isInWelcomeWindow = false,
            smartCoinAllowance = 5,
            bonusCoinsRemaining = null,
            remainingMonthlyQuota = 3,
        )
        assertEquals(5, d.limit)
        assertEquals(2, d.used) // 5 - 3
    }

    @Test
    fun post_first_month_pro_uses_smart_coin_allowance() {
        // Pro values are dead-arg in PlanCard (it short-circuits to PlanCardPaid), but
        // the contract here still needs to be correct in case PlanCard wiring changes.
        val d = computeAiDisplay(
            tier = SubscriptionTier.PRO,
            isInWelcomeWindow = false,
            smartCoinAllowance = 50,
            bonusCoinsRemaining = null,
            remainingMonthlyQuota = 35,
        )
        assertEquals(50, d.limit)
        assertEquals(15, d.used) // 50 - 35
    }

    @Test
    fun post_first_month_with_null_quota_falls_back_to_zero_used() {
        // Documented V1.0 chip-staleness quirk: cache hasn't been hydrated, so we
        // display "0 of 5 used" rather than guess at a number.
        val d = computeAiDisplay(
            tier = SubscriptionTier.FREE,
            isInWelcomeWindow = false,
            smartCoinAllowance = 5,
            bonusCoinsRemaining = null,
            remainingMonthlyQuota = null,
        )
        assertEquals(5, d.limit)
        assertEquals(0, d.used)
    }

    @Test
    fun post_first_month_used_count_clamps_at_zero() {
        // If remaining somehow exceeds limit (e.g. server lag after a tier upgrade),
        // don't render a negative "used" count.
        val d = computeAiDisplay(
            tier = SubscriptionTier.FREE,
            isInWelcomeWindow = false,
            smartCoinAllowance = 5,
            bonusCoinsRemaining = null,
            remainingMonthlyQuota = 7,
        )
        assertEquals(5, d.limit)
        assertEquals(0, d.used)
    }
}
