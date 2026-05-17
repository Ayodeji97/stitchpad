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
import stitchpad.composeapp.generated.resources.plan_card_atelier_state
import stitchpad.composeapp.generated.resources.plan_card_count
import stitchpad.composeapp.generated.resources.plan_card_cta_compare
import stitchpad.composeapp.generated.resources.plan_card_cta_upgrade
import stitchpad.composeapp.generated.resources.plan_card_cta_upgrade_now
import stitchpad.composeapp.generated.resources.plan_card_cta_upgrade_priced
import stitchpad.composeapp.generated.resources.plan_card_customer_unlimited
import stitchpad.composeapp.generated.resources.plan_card_free_state
import stitchpad.composeapp.generated.resources.plan_card_pill_almost_full
import stitchpad.composeapp.generated.resources.plan_card_pill_free
import stitchpad.composeapp.generated.resources.plan_card_pill_limit_reached
import stitchpad.composeapp.generated.resources.plan_card_pro_state
import stitchpad.composeapp.generated.resources.plan_card_subtitle_inline
import stitchpad.composeapp.generated.resources.plan_card_subtitle_locked
import stitchpad.composeapp.generated.resources.plan_card_subtitle_warn
import stitchpad.composeapp.generated.resources.plan_card_title_locked
import stitchpad.composeapp.generated.resources.plan_card_title_warn

// Hero-warn variant kicks in at 80% of the cap (e.g. 12/15). Tuned with the
// freemium decision when PlanCard is re-wired into Settings.
private const val WARN_THRESHOLD_RATIO = 0.80f

/**
 * Three visual states picked from [tier] + [customerCount] / [customerLimit]:
 * - For Pro / Atelier (customerLimit == null): inline "unlimited" state, no Upgrade CTA.
 * - For Free: inline ≤80%, hero-warn >80%, hero-locked =100%.
 */
@Composable
fun PlanCard(
    tier: SubscriptionTier,
    customerCount: Int,
    /** null means unlimited (Pro / Atelier). */
    customerLimit: Int?,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier,
    upgradePriceNgn: String = "2,000",
) {
    // Paid tiers: show a confirming "You're on Pro/Atelier" inline card.
    if (customerLimit == null) {
        PlanCardPaid(
            tier = tier,
            modifier = modifier,
        )
        return
    }

    val ratio = if (customerLimit > 0) customerCount.toFloat() / customerLimit else 0f
    val remaining = (customerLimit - customerCount).coerceAtLeast(0)

    when {
        ratio >= 1f -> PlanCardLocked(
            onUpgradeClick = onUpgradeClick,
            onComparePlansClick = onUpgradeClick,
            modifier = modifier,
        )
        ratio > WARN_THRESHOLD_RATIO -> PlanCardWarn(
            customersLeft = remaining,
            onUpgradeClick = onUpgradeClick,
            onComparePlansClick = onUpgradeClick,
            upgradePriceNgn = upgradePriceNgn,
            modifier = modifier,
        )
        else -> PlanCardInline(
            customerCount = customerCount,
            customerLimit = customerLimit,
            onUpgradeClick = onUpgradeClick,
            modifier = modifier,
        )
    }
}

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

@Composable
private fun PlanCardWarn(
    customersLeft: Int,
    onUpgradeClick: () -> Unit,
    onComparePlansClick: () -> Unit,
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
        title = stringResource(Res.string.plan_card_title_warn, customersLeft),
        subtitle = stringResource(Res.string.plan_card_subtitle_warn),
        primaryCtaText = stringResource(Res.string.plan_card_cta_upgrade_priced, upgradePriceNgn),
        onPrimaryCta = onUpgradeClick,
        ghostCtaText = stringResource(Res.string.plan_card_cta_compare),
        onGhostCta = onComparePlansClick,
        modifier = modifier,
    )
}

@Composable
private fun PlanCardLocked(
    onUpgradeClick: () -> Unit,
    onComparePlansClick: () -> Unit,
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
        onGhostCta = onComparePlansClick,
        modifier = modifier,
    )
}

@Composable
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
                        Text(
                            text = ghostCtaText,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold,
                        )
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

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PlanCardInlinePreview() {
    StitchPadTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlanCard(
                tier = SubscriptionTier.FREE,
                customerCount = 8,
                customerLimit = 15,
                onUpgradeClick = {},
                modifier = Modifier.padding(DesignTokens.space3),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PlanCardWarnPreview() {
    StitchPadTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlanCard(
                tier = SubscriptionTier.FREE,
                customerCount = 13,
                customerLimit = 15,
                onUpgradeClick = {},
                modifier = Modifier.padding(DesignTokens.space3),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PlanCardLockedPreview() {
    StitchPadTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlanCard(
                tier = SubscriptionTier.FREE,
                customerCount = 15,
                customerLimit = 15,
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
                onUpgradeClick = {},
                modifier = Modifier.padding(DesignTokens.space3),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PlanCardDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(DesignTokens.space3),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            ) {
                PlanCard(
                    tier = SubscriptionTier.FREE,
                    customerCount = 8,
                    customerLimit = 15,
                    onUpgradeClick = {},
                )
                PlanCard(
                    tier = SubscriptionTier.FREE,
                    customerCount = 13,
                    customerLimit = 15,
                    onUpgradeClick = {},
                )
            }
        }
    }
}
