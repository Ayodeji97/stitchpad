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
 * must render "0 used" not "30 used"; First Month bonus precedence — `usageBonusBalance`
 * (server-side truth) wins over the user-doc `bonusCoinsRemaining` seed whenever it's
 * non-null; post-First-Month count precedence — `usageMonthlyCount` (server-side truth)
 * wins over the in-process `remainingMonthlyQuota` cache.
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
            usageBonusBalance = null,
            usageMonthlyCount = null,
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
            usageBonusBalance = null,
            usageMonthlyCount = 100,
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
            usageBonusBalance = null,
            usageMonthlyCount = null,
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
            usageBonusBalance = null,
            usageMonthlyCount = null,
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
            usageBonusBalance = null,
            usageMonthlyCount = null,
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
            usageBonusBalance = null,
            usageMonthlyCount = null,
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
            usageBonusBalance = null,
            usageMonthlyCount = null,
            remainingMonthlyQuota = 5,
        )
        // The OLD wrong behavior would have produced limit = 5.
        assertEquals(30, d.limit, "First Month must use 30-coin bonus limit, not the 5-draft monthly quota")
    }

    // ── First Month: usageBonusBalance precedence ────────────────────────────

    @Test
    fun first_month_usage_doc_balance_wins_over_user_doc_seed() {
        // Server-side truth: usage doc says 18 remaining (12 consumed). User doc still
        // shows 30 (the signup seed, which the server never decrements). PlanCard must
        // reflect the live server count, not the stale seed.
        val d = computeAiDisplay(
            tier = SubscriptionTier.FREE,
            isInWelcomeWindow = true,
            smartCoinAllowance = 5,
            bonusCoinsRemaining = 30,
            usageBonusBalance = 18,
            usageMonthlyCount = null,
            remainingMonthlyQuota = 5,
        )
        assertEquals(30, d.limit)
        assertEquals(12, d.used) // 30 - 18 = 12, NOT 0 (which would be the stale seed)
    }

    @Test
    fun first_month_usage_doc_balance_of_zero_means_30_used_even_if_user_doc_lies() {
        // Bonus fully consumed server-side. Must show 30/30 used regardless of any
        // stale value still sitting on the user doc.
        val d = computeAiDisplay(
            tier = SubscriptionTier.FREE,
            isInWelcomeWindow = true,
            smartCoinAllowance = 5,
            bonusCoinsRemaining = 30,
            usageBonusBalance = 0,
            usageMonthlyCount = null,
            remainingMonthlyQuota = 5,
        )
        assertEquals(30, d.limit)
        assertEquals(30, d.used)
    }

    @Test
    fun first_month_null_usage_doc_falls_back_to_user_doc_bonus() {
        // No Smart call has happened yet (or pre-V1.0 usage doc without bonusBalance).
        // The user-doc seed is the right answer until the server lifts the bonus.
        val d = computeAiDisplay(
            tier = SubscriptionTier.FREE,
            isInWelcomeWindow = true,
            smartCoinAllowance = 5,
            bonusCoinsRemaining = 24,
            usageBonusBalance = null,
            usageMonthlyCount = null,
            remainingMonthlyQuota = 5,
        )
        assertEquals(30, d.limit)
        assertEquals(6, d.used) // 30 - 24 = 6 (fallback to user doc)
    }

    // ── Post-First-Month ─────────────────────────────────────────────────────

    @Test
    fun post_first_month_free_falls_back_to_store_cache_when_usage_doc_count_null() {
        val d = computeAiDisplay(
            tier = SubscriptionTier.FREE,
            isInWelcomeWindow = false,
            smartCoinAllowance = 5,
            bonusCoinsRemaining = null,
            usageBonusBalance = null,
            usageMonthlyCount = null,
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
            usageBonusBalance = null,
            usageMonthlyCount = null,
            remainingMonthlyQuota = 35,
        )
        assertEquals(50, d.limit)
        assertEquals(15, d.used) // 50 - 35
    }

    @Test
    fun post_first_month_with_null_quota_and_null_usage_count_renders_zero_used() {
        // Cold-start: usage doc not hydrated AND in-process cache not populated.
        // Display "0 of 5 used" rather than guess at a number.
        val d = computeAiDisplay(
            tier = SubscriptionTier.FREE,
            isInWelcomeWindow = false,
            smartCoinAllowance = 5,
            bonusCoinsRemaining = null,
            usageBonusBalance = null,
            usageMonthlyCount = null,
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
            usageBonusBalance = null,
            usageMonthlyCount = null,
            remainingMonthlyQuota = 7,
        )
        assertEquals(5, d.limit)
        assertEquals(0, d.used)
    }

    // ── Post-First-Month: usageMonthlyCount precedence ───────────────────────

    @Test
    fun post_first_month_usage_doc_count_wins_over_store_cache() {
        // Server's count = 3 (3 drafts used). Store cache is stale (says 5 remaining = 0 used).
        // The doc is the truth — chip must render 3 used, not 0.
        val d = computeAiDisplay(
            tier = SubscriptionTier.FREE,
            isInWelcomeWindow = false,
            smartCoinAllowance = 5,
            bonusCoinsRemaining = null,
            usageBonusBalance = null,
            usageMonthlyCount = 3,
            remainingMonthlyQuota = 5,
        )
        assertEquals(5, d.limit)
        assertEquals(3, d.used)
    }

    @Test
    fun post_first_month_usage_doc_count_of_five_renders_locked() {
        // Server's count hit the cap → AI Locked state. Test pins that chip reads 5/5.
        val d = computeAiDisplay(
            tier = SubscriptionTier.FREE,
            isInWelcomeWindow = false,
            smartCoinAllowance = 5,
            bonusCoinsRemaining = null,
            usageBonusBalance = null,
            usageMonthlyCount = 5,
            remainingMonthlyQuota = null,
        )
        assertEquals(5, d.limit)
        assertEquals(5, d.used)
    }

    @Test
    fun post_first_month_usage_doc_count_clamps_at_allowance() {
        // Defense against tier downgrade: doc has count=12 from when user was on Pro,
        // they downgraded to Free (allowance=5). Don't render "12 of 5 used".
        val d = computeAiDisplay(
            tier = SubscriptionTier.FREE,
            isInWelcomeWindow = false,
            smartCoinAllowance = 5,
            bonusCoinsRemaining = null,
            usageBonusBalance = null,
            usageMonthlyCount = 12,
            remainingMonthlyQuota = null,
        )
        assertEquals(5, d.limit)
        assertEquals(5, d.used)
    }

    @Test
    fun post_first_month_zero_count_means_zero_used() {
        // Brand-new month: server reset count to 0. Cache may still be stale from
        // last month — usage doc wins.
        val d = computeAiDisplay(
            tier = SubscriptionTier.FREE,
            isInWelcomeWindow = false,
            smartCoinAllowance = 5,
            bonusCoinsRemaining = null,
            usageBonusBalance = null,
            usageMonthlyCount = 0,
            remainingMonthlyQuota = 1,
        )
        assertEquals(5, d.limit)
        assertEquals(0, d.used) // doc count wins; would have been 4 if cache won
    }
}
