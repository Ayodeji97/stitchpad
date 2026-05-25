@file:Suppress("TooManyFunctions")
// PlanCard renders three tier-specific cards (Free / First Month / Paid) plus the
// preview surface, each composed of small private composables. Splitting the file
// further would obscure the shared design tokens; @Suppress documents this is
// deliberate.

package com.danzucker.stitchpad.feature.settings.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.plan_card_ai_resets_caption
import stitchpad.composeapp.generated.resources.plan_card_atelier_state
import stitchpad.composeapp.generated.resources.plan_card_count
import stitchpad.composeapp.generated.resources.plan_card_cta_compare
import stitchpad.composeapp.generated.resources.plan_card_cta_upgrade
import stitchpad.composeapp.generated.resources.plan_card_cta_upgrade_now
import stitchpad.composeapp.generated.resources.plan_card_cta_upgrade_priced
import stitchpad.composeapp.generated.resources.plan_card_customer_unlimited
import stitchpad.composeapp.generated.resources.plan_card_first_month_ai_headline
import stitchpad.composeapp.generated.resources.plan_card_first_month_ai_progress
import stitchpad.composeapp.generated.resources.plan_card_first_month_customers_no_limits
import stitchpad.composeapp.generated.resources.plan_card_first_month_help
import stitchpad.composeapp.generated.resources.plan_card_first_month_pill_one
import stitchpad.composeapp.generated.resources.plan_card_first_month_pill_other
import stitchpad.composeapp.generated.resources.plan_card_free_state
import stitchpad.composeapp.generated.resources.plan_card_pill_ai_almost_out_first_month
import stitchpad.composeapp.generated.resources.plan_card_pill_ai_almost_out_free
import stitchpad.composeapp.generated.resources.plan_card_pill_ai_used_up_first_month
import stitchpad.composeapp.generated.resources.plan_card_pill_ai_used_up_free
import stitchpad.composeapp.generated.resources.plan_card_pill_almost_full
import stitchpad.composeapp.generated.resources.plan_card_pill_free
import stitchpad.composeapp.generated.resources.plan_card_pill_limit_reached
import stitchpad.composeapp.generated.resources.plan_card_pill_maxed_out
import stitchpad.composeapp.generated.resources.plan_card_pro_state
import stitchpad.composeapp.generated.resources.plan_card_subtitle_ai_locked
import stitchpad.composeapp.generated.resources.plan_card_subtitle_ai_warn_first_month
import stitchpad.composeapp.generated.resources.plan_card_subtitle_ai_warn_free
import stitchpad.composeapp.generated.resources.plan_card_subtitle_inline
import stitchpad.composeapp.generated.resources.plan_card_subtitle_locked
import stitchpad.composeapp.generated.resources.plan_card_subtitle_maxed_out
import stitchpad.composeapp.generated.resources.plan_card_subtitle_warn
import stitchpad.composeapp.generated.resources.plan_card_title_ai_locked
import stitchpad.composeapp.generated.resources.plan_card_title_ai_locked_minutes
import stitchpad.composeapp.generated.resources.plan_card_title_ai_warn_one
import stitchpad.composeapp.generated.resources.plan_card_title_ai_warn_other
import stitchpad.composeapp.generated.resources.plan_card_title_locked
import stitchpad.composeapp.generated.resources.plan_card_title_maxed_out
import stitchpad.composeapp.generated.resources.plan_card_title_warn_one
import stitchpad.composeapp.generated.resources.plan_card_title_warn_other

// Hero-warn variant kicks in at 80% of the cap (e.g. 12/15 customers, 4/5 AI drafts).
// Tuned with the freemium V1.0 decisions (see docs/design/freemium-v1.0-design-spec.md).
private const val WARN_THRESHOLD_RATIO = 0.80f

// Time-saved heuristic per AI draft. Pulled into a constant so the value is the
// single source of truth — when better data lands in V1.1 we re-tune here once
// and every surface inherits it (per V1.0 design spec decision #3).
private const val MINUTES_SAVED_PER_AI_DRAFT = 1.5f

/**
 * Plan card — the customer + AI usage summary on Settings.
 *
 * Drives a 3-state visual machine (inline / warn / locked) from two signals: customer
 * count vs cap, and AI drafts used vs monthly limit. The state-machine rule depends on
 * whether the user is in their First Month (days 1–30) or post-First-Month:
 *
 * - **First Month** — only AI drives state. Customer count is hidden as a fraction
 *   (rendered as "12 customers added · no limits this month"). 200-cap is invisible to
 *   the user per the V1.0 design spec.
 * - **Post-First-Month** — `max(aiRatio, customerRatio)` drives state. AI wins ties
 *   because it's the higher-priority conversion lever. If BOTH ratios are at 100% the
 *   rare worst case `PlanCardMaxedOut` fires.
 *
 * @param tier Current subscription tier
 * @param customerCount Active customer count
 * @param customerLimit Customer cap; null = unlimited (Pro / Atelier)
 * @param aiDraftsUsed AI drafts consumed this month
 * @param aiDraftLimit Monthly AI draft cap; null = unlimited (Atelier)
 * @param isFirstMonth True while the user is in their First Month window
 * @param welcomeDaysLeft Days remaining in First Month; only used when isFirstMonth=true
 */
@Composable
@Suppress("LongParameterList", "CyclomaticComplexMethod")
fun PlanCard(
    tier: SubscriptionTier,
    customerCount: Int,
    customerLimit: Int?,
    aiDraftsUsed: Int,
    aiDraftLimit: Int?,
    isFirstMonth: Boolean,
    welcomeDaysLeft: Int?,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier,
    upgradePriceNgn: String = "2,000",
) {
    // Any paid tier short-circuits to the "you're on Pro/Atelier" card regardless of
    // their AI quota state. The previous gate required BOTH customer- and AI-limits to
    // be null, which broke Pro (customerLimit = null, aiDraftLimit = 50) — Pro fell
    // through to the Free state machine and rendered "42 of 0 customers" garbage at the
    // PlanCardInline branch. Pro/Atelier AI exhaustion surfaces via a snackbar from
    // DraftMessageViewModel (pro_quota_exhausted marker), not the PlanCard.
    if (tier != SubscriptionTier.FREE) {
        PlanCardPaid(tier = tier, modifier = modifier)
        return
    }

    val aiRatio = ratioOf(aiDraftsUsed, aiDraftLimit)
    val customerRatio = ratioOf(customerCount, customerLimit)

    if (isFirstMonth) {
        // First Month: only AI ratio drives state. Customer count appears as a secondary line.
        when {
            aiRatio >= 1f -> PlanCardAiLocked(
                isFirstMonth = true,
                aiDraftLimit = aiDraftLimit ?: 0,
                onUpgradeClick = onUpgradeClick,
                modifier = modifier,
            )
            aiRatio > WARN_THRESHOLD_RATIO -> PlanCardAiWarn(
                isFirstMonth = true,
                aiDraftsLeft = (aiDraftLimit ?: 0) - aiDraftsUsed,
                upgradePriceNgn = upgradePriceNgn,
                onUpgradeClick = onUpgradeClick,
                modifier = modifier,
            )
            else -> PlanCardFirstMonthInline(
                customerCount = customerCount,
                welcomeDaysLeft = welcomeDaysLeft,
                aiDraftsUsed = aiDraftsUsed,
                aiDraftLimit = aiDraftLimit ?: 0,
                onUpgradeClick = onUpgradeClick,
                modifier = modifier,
            )
        }
        return
    }

    // Post-First-Month: both signals competing. Pre-compute the locked flags so the
    // priority rule reads cleanly without re-evaluating ratios per branch.
    val customerLocked = customerLimit != null && customerRatio >= 1f
    val aiLocked = aiDraftLimit != null && aiRatio >= 1f

    when {
        customerLocked && aiLocked -> PlanCardMaxedOut(
            onUpgradeClick = onUpgradeClick,
            modifier = modifier,
        )
        customerLocked -> PlanCardCustomerLocked(
            onUpgradeClick = onUpgradeClick,
            modifier = modifier,
        )
        aiLocked -> PlanCardAiLocked(
            isFirstMonth = false,
            aiDraftLimit = aiDraftLimit ?: 0,
            onUpgradeClick = onUpgradeClick,
            modifier = modifier,
        )
        // Warn: surface whichever ratio is higher. Ties go to AI (higher-priority conversion lever).
        aiRatio > WARN_THRESHOLD_RATIO && aiRatio >= customerRatio -> PlanCardAiWarn(
            isFirstMonth = false,
            aiDraftsLeft = (aiDraftLimit ?: 0) - aiDraftsUsed,
            upgradePriceNgn = upgradePriceNgn,
            onUpgradeClick = onUpgradeClick,
            modifier = modifier,
        )
        customerRatio > WARN_THRESHOLD_RATIO -> PlanCardCustomerWarn(
            customersLeft = (customerLimit ?: 0) - customerCount,
            onUpgradeClick = onUpgradeClick,
            upgradePriceNgn = upgradePriceNgn,
            modifier = modifier,
        )
        else -> PlanCardInline(
            customerCount = customerCount,
            customerLimit = customerLimit ?: 0,
            onUpgradeClick = onUpgradeClick,
            modifier = modifier,
        )
    }
}

private fun ratioOf(used: Int, limit: Int?): Float = limit?.let {
    if (it > 0) used.toFloat() / it else 0f
} ?: 0f

// ── First Month inline ────────────────────────────────────────────────────────

@Composable
private fun PlanCardFirstMonthInline(
    customerCount: Int,
    welcomeDaysLeft: Int?,
    aiDraftsUsed: Int,
    aiDraftLimit: Int,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val daysLeft = welcomeDaysLeft ?: 0
    val pillTextRes = if (daysLeft == 1) {
        Res.string.plan_card_first_month_pill_one
    } else {
        Res.string.plan_card_first_month_pill_other
    }
    val aiRatio = if (aiDraftLimit > 0) aiDraftsUsed.toFloat() / aiDraftLimit else 0f

    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onUpgradeClick),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            PlanPill(
                text = stringResource(pillTextRes, daysLeft),
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(modifier = Modifier.height(DesignTokens.space3))

            // AI usage as the headline (per V1.0 design spec decision #3).
            Text(
                text = stringResource(Res.string.plan_card_first_month_ai_headline),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { aiRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    Res.string.plan_card_first_month_ai_progress,
                    aiDraftsUsed,
                    aiDraftLimit,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(DesignTokens.space3))

            // Customer count as the secondary line — no fraction shown ("unlimited" framing).
            Text(
                text = stringResource(
                    Res.string.plan_card_first_month_customers_no_limits,
                    customerCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(DesignTokens.space2))

            Text(
                text = stringResource(Res.string.plan_card_first_month_help),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ── Post-First-Month inline (Free, both signals < 80%) ────────────────────────

@Composable
private fun PlanCardInline(
    customerCount: Int,
    customerLimit: Int,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onUpgradeClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                PlanPill(
                    text = stringResource(Res.string.plan_card_pill_free),
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(DesignTokens.space2))
                Text(
                    text = stringResource(Res.string.plan_card_count, customerCount, customerLimit),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.plan_card_subtitle_inline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(Res.string.plan_card_cta_upgrade),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(DesignTokens.space2))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ── Paid tier (Pro / Atelier — both signals unlimited) ────────────────────────

@Composable
private fun PlanCardPaid(
    tier: SubscriptionTier,
    modifier: Modifier = Modifier,
) {
    val tierLabel = stringResource(
        when (tier) {
            SubscriptionTier.PRO -> Res.string.plan_card_pro_state
            SubscriptionTier.ATELIER -> Res.string.plan_card_atelier_state
            SubscriptionTier.FREE -> Res.string.plan_card_free_state
        }
    )
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                PlanPill(
                    text = tierLabel,
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(DesignTokens.space2))
                Text(
                    text = stringResource(Res.string.plan_card_customer_unlimited),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ── Customer warn (post-First-Month, customer ratio > 80%) ────────────────────

@Composable
private fun PlanCardCustomerWarn(
    customersLeft: Int,
    onUpgradeClick: () -> Unit,
    upgradePriceNgn: String,
    modifier: Modifier = Modifier,
) {
    PlanHeroFrame(
        backgroundBrush = Brush.radialGradient(
            colors = listOf(Color(0xFF2A1F00), Color(0xFF181615)),
            radius = 800f,
        ),
        accentBrush = Brush.radialGradient(
            colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), Color.Transparent),
            radius = 320f,
        ),
        pillText = stringResource(Res.string.plan_card_pill_almost_full),
        pillBorderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.40f),
        pillContentColor = MaterialTheme.colorScheme.tertiary,
        title = stringResource(
            if (customersLeft == 1) {
                Res.string.plan_card_title_warn_one
            } else {
                Res.string.plan_card_title_warn_other
            },
            customersLeft,
        ),
        subtitle = stringResource(Res.string.plan_card_subtitle_warn),
        primaryCtaText = stringResource(Res.string.plan_card_cta_upgrade_priced, upgradePriceNgn),
        onPrimaryCta = onUpgradeClick,
        ghostCtaText = stringResource(Res.string.plan_card_cta_compare),
        onGhostCta = onUpgradeClick,
        modifier = modifier,
    )
}

// ── Customer locked (post-First-Month, customer ratio = 100%) ─────────────────

@Composable
private fun PlanCardCustomerLocked(
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val errorRed = MaterialTheme.colorScheme.error
    PlanHeroFrame(
        backgroundBrush = Brush.radialGradient(
            colors = listOf(Color(0xFF3B1410), Color(0xFF181615)),
            radius = 800f,
        ),
        accentBrush = Brush.radialGradient(
            colors = listOf(errorRed.copy(alpha = 0.45f), Color.Transparent),
            radius = 320f,
        ),
        pillText = stringResource(Res.string.plan_card_pill_limit_reached),
        pillBorderColor = Color(0xFFFFB4A8).copy(alpha = 0.45f),
        pillContentColor = Color(0xFFFFB4A8),
        title = stringResource(Res.string.plan_card_title_locked),
        subtitle = stringResource(Res.string.plan_card_subtitle_locked),
        primaryCtaText = stringResource(Res.string.plan_card_cta_upgrade_now),
        onPrimaryCta = onUpgradeClick,
        ghostCtaText = stringResource(Res.string.plan_card_cta_compare),
        onGhostCta = onUpgradeClick,
        modifier = modifier,
    )
}

// ── AI warn (fires in both First Month and post-First-Month Free) ─────────────

@Composable
private fun PlanCardAiWarn(
    isFirstMonth: Boolean,
    aiDraftsLeft: Int,
    upgradePriceNgn: String,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlanHeroFrame(
        backgroundBrush = Brush.radialGradient(
            colors = listOf(Color(0xFF2A1F00), Color(0xFF181615)),
            radius = 800f,
        ),
        accentBrush = Brush.radialGradient(
            colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), Color.Transparent),
            radius = 320f,
        ),
        pillText = stringResource(
            if (isFirstMonth) {
                Res.string.plan_card_pill_ai_almost_out_first_month
            } else {
                Res.string.plan_card_pill_ai_almost_out_free
            },
        ),
        pillBorderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.40f),
        pillContentColor = MaterialTheme.colorScheme.tertiary,
        title = stringResource(
            if (aiDraftsLeft == 1) {
                Res.string.plan_card_title_ai_warn_one
            } else {
                Res.string.plan_card_title_ai_warn_other
            },
            aiDraftsLeft,
        ),
        subtitle = stringResource(
            if (isFirstMonth) {
                Res.string.plan_card_subtitle_ai_warn_first_month
            } else {
                Res.string.plan_card_subtitle_ai_warn_free
            },
        ),
        primaryCtaText = stringResource(Res.string.plan_card_cta_upgrade_priced, upgradePriceNgn),
        onPrimaryCta = onUpgradeClick,
        ghostCtaText = stringResource(Res.string.plan_card_cta_compare),
        onGhostCta = onUpgradeClick,
        modifier = modifier,
    )
}

// ── AI locked (success-framed exhaustion) ─────────────────────────────────────

@Composable
private fun PlanCardAiLocked(
    isFirstMonth: Boolean,
    aiDraftLimit: Int,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val minutesSaved = (aiDraftLimit * MINUTES_SAVED_PER_AI_DRAFT).toInt()
    PlanHeroFrame(
        backgroundBrush = Brush.radialGradient(
            colors = listOf(Color(0xFF1F2A00), Color(0xFF181615)),
            radius = 800f,
        ),
        accentBrush = Brush.radialGradient(
            colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), Color.Transparent),
            radius = 320f,
        ),
        pillText = stringResource(
            if (isFirstMonth) {
                Res.string.plan_card_pill_ai_used_up_first_month
            } else {
                Res.string.plan_card_pill_ai_used_up_free
            },
        ),
        pillBorderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.40f),
        pillContentColor = MaterialTheme.colorScheme.tertiary,
        title = stringResource(Res.string.plan_card_title_ai_locked, aiDraftLimit),
        titleSecondLine = stringResource(Res.string.plan_card_title_ai_locked_minutes, minutesSaved),
        subtitle = stringResource(Res.string.plan_card_subtitle_ai_locked),
        primaryCtaText = stringResource(Res.string.plan_card_cta_upgrade_now),
        onPrimaryCta = onUpgradeClick,
        ghostCtaText = stringResource(Res.string.plan_card_ai_resets_caption),
        onGhostCta = onUpgradeClick,
        modifier = modifier,
    )
}

// ── Maxed out (rare worst-case: customer and AI both locked) ──────────────────

@Composable
private fun PlanCardMaxedOut(
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val errorRed = MaterialTheme.colorScheme.error
    PlanHeroFrame(
        backgroundBrush = Brush.radialGradient(
            colors = listOf(Color(0xFF3B1410), Color(0xFF181615)),
            radius = 800f,
        ),
        accentBrush = Brush.radialGradient(
            colors = listOf(errorRed.copy(alpha = 0.45f), Color.Transparent),
            radius = 320f,
        ),
        pillText = stringResource(Res.string.plan_card_pill_maxed_out),
        pillBorderColor = Color(0xFFFFB4A8).copy(alpha = 0.45f),
        pillContentColor = Color(0xFFFFB4A8),
        title = stringResource(Res.string.plan_card_title_maxed_out),
        subtitle = stringResource(Res.string.plan_card_subtitle_maxed_out),
        primaryCtaText = stringResource(Res.string.plan_card_cta_upgrade_now),
        onPrimaryCta = onUpgradeClick,
        ghostCtaText = stringResource(Res.string.plan_card_cta_compare),
        onGhostCta = onUpgradeClick,
        modifier = modifier,
    )
}

// ── Shared hero frame ─────────────────────────────────────────────────────────

@Composable
@Suppress("LongParameterList")
private fun PlanHeroFrame(
    backgroundBrush: Brush,
    accentBrush: Brush,
    pillText: String,
    pillBorderColor: Color,
    pillContentColor: Color,
    title: String,
    subtitle: String,
    primaryCtaText: String,
    onPrimaryCta: () -> Unit,
    ghostCtaText: String,
    onGhostCta: () -> Unit,
    modifier: Modifier = Modifier,
    // Optional stat-style line rendered immediately under the title. Used by
    // PlanCardAiLocked for "~N minutes saved" so it gets its own line instead
    // of trailing the title with an em-dash that wraps awkwardly on narrow
    // device widths.
    titleSecondLine: String? = null,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DesignTokens.radiusLg))
                .background(backgroundBrush),
        ) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .background(accentBrush),
            )
            Column(modifier = Modifier.padding(DesignTokens.space4)) {
                Surface(
                    shape = RoundedCornerShape(DesignTokens.radiusFull),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, pillBorderColor),
                ) {
                    Text(
                        text = pillText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = pillContentColor,
                        modifier = Modifier.padding(
                            horizontal = DesignTokens.space2,
                            vertical = 4.dp,
                        ),
                    )
                }
                Spacer(modifier = Modifier.height(DesignTokens.space2))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 18.sp,
                )
                if (titleSecondLine != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = titleSecondLine,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 16.sp,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                )
                Spacer(modifier = Modifier.height(DesignTokens.space3))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                ) {
                    Button(
                        onClick = onPrimaryCta,
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            text = primaryCtaText,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    TextButton(onClick = onGhostCta) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = ghostCtaText,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanPill(
    text: String,
    backgroundColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusFull),
        color = backgroundColor,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = DesignTokens.space2, vertical = 3.dp),
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PlanCardFirstMonthInlinePreview() {
    StitchPadTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlanCard(
                tier = SubscriptionTier.FREE,
                customerCount = 12,
                customerLimit = 200,
                aiDraftsUsed = 5,
                aiDraftLimit = 30,
                isFirstMonth = true,
                welcomeDaysLeft = 23,
                onUpgradeClick = {},
                modifier = Modifier.padding(DesignTokens.space3),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PlanCardFirstMonthAiWarnPreview() {
    StitchPadTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlanCard(
                tier = SubscriptionTier.FREE,
                customerCount = 28,
                customerLimit = 200,
                aiDraftsUsed = 26,
                aiDraftLimit = 30,
                isFirstMonth = true,
                welcomeDaysLeft = 8,
                onUpgradeClick = {},
                modifier = Modifier.padding(DesignTokens.space3),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PlanCardFirstMonthAiLockedPreview() {
    StitchPadTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlanCard(
                tier = SubscriptionTier.FREE,
                customerCount = 35,
                customerLimit = 200,
                aiDraftsUsed = 30,
                aiDraftLimit = 30,
                isFirstMonth = true,
                welcomeDaysLeft = 5,
                onUpgradeClick = {},
                modifier = Modifier.padding(DesignTokens.space3),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PlanCardPostInlinePreview() {
    StitchPadTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlanCard(
                tier = SubscriptionTier.FREE,
                customerCount = 8,
                customerLimit = 15,
                aiDraftsUsed = 2,
                aiDraftLimit = 5,
                isFirstMonth = false,
                welcomeDaysLeft = null,
                onUpgradeClick = {},
                modifier = Modifier.padding(DesignTokens.space3),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PlanCardPostCustomerWarnPreview() {
    StitchPadTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlanCard(
                tier = SubscriptionTier.FREE,
                customerCount = 13,
                customerLimit = 15,
                aiDraftsUsed = 2,
                aiDraftLimit = 5,
                isFirstMonth = false,
                welcomeDaysLeft = null,
                onUpgradeClick = {},
                modifier = Modifier.padding(DesignTokens.space3),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PlanCardPostAiWarnPreview() {
    StitchPadTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlanCard(
                tier = SubscriptionTier.FREE,
                customerCount = 5,
                customerLimit = 15,
                aiDraftsUsed = 4,
                aiDraftLimit = 5,
                isFirstMonth = false,
                welcomeDaysLeft = null,
                onUpgradeClick = {},
                modifier = Modifier.padding(DesignTokens.space3),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PlanCardPostCustomerLockedPreview() {
    StitchPadTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlanCard(
                tier = SubscriptionTier.FREE,
                customerCount = 15,
                customerLimit = 15,
                aiDraftsUsed = 2,
                aiDraftLimit = 5,
                isFirstMonth = false,
                welcomeDaysLeft = null,
                onUpgradeClick = {},
                modifier = Modifier.padding(DesignTokens.space3),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PlanCardPostAiLockedPreview() {
    StitchPadTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlanCard(
                tier = SubscriptionTier.FREE,
                customerCount = 8,
                customerLimit = 15,
                aiDraftsUsed = 5,
                aiDraftLimit = 5,
                isFirstMonth = false,
                welcomeDaysLeft = null,
                onUpgradeClick = {},
                modifier = Modifier.padding(DesignTokens.space3),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PlanCardMaxedOutPreview() {
    StitchPadTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlanCard(
                tier = SubscriptionTier.FREE,
                customerCount = 15,
                customerLimit = 15,
                aiDraftsUsed = 5,
                aiDraftLimit = 5,
                isFirstMonth = false,
                welcomeDaysLeft = null,
                onUpgradeClick = {},
                modifier = Modifier.padding(DesignTokens.space3),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PlanCardProPreview() {
    StitchPadTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlanCard(
                tier = SubscriptionTier.PRO,
                customerCount = 42,
                customerLimit = null,
                aiDraftsUsed = 10,
                aiDraftLimit = null,
                isFirstMonth = false,
                welcomeDaysLeft = null,
                onUpgradeClick = {},
                modifier = Modifier.padding(DesignTokens.space3),
            )
        }
    }
}
