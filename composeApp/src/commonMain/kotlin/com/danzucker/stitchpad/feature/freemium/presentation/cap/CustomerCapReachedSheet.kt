package com.danzucker.stitchpad.feature.freemium.presentation.cap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalStitchPadColors
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.cap_reached_sheet_benefit_cancel
import stitchpad.composeapp.generated.resources.cap_reached_sheet_benefit_drafts
import stitchpad.composeapp.generated.resources.cap_reached_sheet_benefit_unlimited
import stitchpad.composeapp.generated.resources.cap_reached_sheet_eyebrow
import stitchpad.composeapp.generated.resources.cap_reached_sheet_price
import stitchpad.composeapp.generated.resources.cap_reached_sheet_pro_tier
import stitchpad.composeapp.generated.resources.cap_reached_sheet_stat
import stitchpad.composeapp.generated.resources.cap_reached_sheet_swap_link
import stitchpad.composeapp.generated.resources.cap_reached_sheet_swap_prefix
import stitchpad.composeapp.generated.resources.cap_reached_sheet_swap_suffix
import stitchpad.composeapp.generated.resources.cap_reached_sheet_title
import stitchpad.composeapp.generated.resources.cap_reached_sheet_upgrade_cta

/**
 * Modal bottom sheet shown when a Free-tier tailor tries to add a customer
 * past their active cap. Replaces the previous dismissible snackbar so the
 * upgrade pitch + the secondary swap path are both reachable and visually
 * prominent. Mirrors design-explorer Variant E (Editorial + benefits + price).
 *
 * Visual structure (top → bottom):
 *  - Copper eyebrow: "CUSTOMER LIMIT REACHED"
 *  - Fraunces-serif headline: "Ready to grow your workshop?"
 *  - Mono stat line: "15 of 15 active customers"
 *  - Elevated card with Tailor Pro + ₦2,000/mo + 3 benefit rows
 *  - Primary CTA: star icon + "Upgrade to Pro"
 *  - Secondary text link: "or swap a customer to stay on Free"
 *
 * Navigation callbacks fire BEFORE [onDismiss] so the caller can pop the
 * sheet first, then route — avoids a flicker where the sheet animates out
 * after the next screen has already pushed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerCapReachedSheet(
    activeCount: Int,
    customerCap: Int,
    onUpgradeClick: () -> Unit,
    onSwapClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        CustomerCapReachedSheetContent(
            activeCount = activeCount,
            customerCap = customerCap,
            onUpgradeClick = onUpgradeClick,
            onSwapClick = onSwapClick,
        )
    }
}

/**
 * Inner column extracted so @Preview can render it — `ModalBottomSheet` itself
 * doesn't lay out in preview mode (no host activity / sheet state).
 */
@Composable
private fun CustomerCapReachedSheetContent(
    activeCount: Int,
    customerCap: Int,
    onUpgradeClick: () -> Unit,
    onSwapClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.space4)
            .padding(bottom = DesignTokens.space5),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        // Eyebrow — copper accent, all caps, wide letter spacing.
        Text(
            text = stringResource(Res.string.cap_reached_sheet_eyebrow),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
            fontWeight = FontWeight.SemiBold,
            color = LocalStitchPadColors.current.brandAccent,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // Serif headline reframes the cap as opportunity, not a block.
        Text(
            text = stringResource(Res.string.cap_reached_sheet_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // Current usage anchor so the user knows exactly where they stand.
        Text(
            text = stringResource(
                Res.string.cap_reached_sheet_stat,
                activeCount,
                customerCap,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(DesignTokens.space3))

        ProBenefitsCard()

        Spacer(Modifier.height(DesignTokens.space3))

        Button(
            onClick = onUpgradeClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(DesignTokens.radiusLg),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(DesignTokens.space2))
            Text(
                text = stringResource(Res.string.cap_reached_sheet_upgrade_cta),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(DesignTokens.space1))

        SwapTextLink(onSwapClick = onSwapClick)
    }
}

/**
 * Elevated card with Tailor Pro + ₦2,000/mo header and three benefit rows.
 * Borders + surface use Material3 outline / surfaceVariant so it inherits
 * the proper dark/light treatment without hardcoding hex.
 */
@Composable
private fun ProBenefitsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(DesignTokens.radiusLg),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(DesignTokens.radiusLg),
            )
            .padding(DesignTokens.space4),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.cap_reached_sheet_pro_tier),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(Res.string.cap_reached_sheet_price),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        BenefitRow(stringResource(Res.string.cap_reached_sheet_benefit_unlimited))
        BenefitRow(stringResource(Res.string.cap_reached_sheet_benefit_drafts))
        BenefitRow(stringResource(Res.string.cap_reached_sheet_benefit_cancel))
    }
}

@Composable
private fun BenefitRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        CheckBadge(icon = Icons.Outlined.Check)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CheckBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * "or [swap a customer] to stay on Free" — middle portion is the only
 * clickable region (full-line tap targets the whole row, with the link
 * portion typographically emphasised). buildAnnotatedString lets the
 * sentence wrap as a single flowing line on narrow screens.
 */
@Composable
private fun SwapTextLink(onSwapClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val prefix = stringResource(Res.string.cap_reached_sheet_swap_prefix)
    val link = stringResource(Res.string.cap_reached_sheet_swap_link)
    val suffix = stringResource(Res.string.cap_reached_sheet_swap_suffix)
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = muted)) {
            append(prefix)
        }
        withStyle(SpanStyle(color = primary, fontWeight = FontWeight.SemiBold)) {
            append(link)
        }
        withStyle(SpanStyle(color = muted)) {
            append(suffix)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSwapClick)
            .padding(DesignTokens.space2),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Previews ───────────────────────────────────────────────────────────────
// ModalBottomSheet doesn't render in @Preview (no sheet state / host); these
// preview the inner content composable directly. Mirrors the pattern used by
// ReauthBottomSheet and DeleteAccountReasonSheet.

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun CustomerCapReachedSheetContentPreview() {
    StitchPadTheme {
        CustomerCapReachedSheetContent(
            activeCount = 15,
            customerCap = 15,
            onUpgradeClick = {},
            onSwapClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun CustomerCapReachedSheetContentDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        CustomerCapReachedSheetContent(
            activeCount = 15,
            customerCap = 15,
            onUpgradeClick = {},
            onSwapClick = {},
        )
    }
}
