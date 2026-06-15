package com.danzucker.stitchpad.feature.style.presentation.cap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalStitchPadColors
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.style_cap_dismiss
import stitchpad.composeapp.generated.resources.style_cap_folder_body
import stitchpad.composeapp.generated.resources.style_cap_folder_eyebrow
import stitchpad.composeapp.generated.resources.style_cap_folder_title
import stitchpad.composeapp.generated.resources.style_cap_got_it
import stitchpad.composeapp.generated.resources.style_cap_max_body
import stitchpad.composeapp.generated.resources.style_cap_max_title
import stitchpad.composeapp.generated.resources.style_cap_style_body
import stitchpad.composeapp.generated.resources.style_cap_style_eyebrow
import stitchpad.composeapp.generated.resources.style_cap_style_title
import stitchpad.composeapp.generated.resources.style_cap_upgrade_atelier
import stitchpad.composeapp.generated.resources.style_cap_upgrade_pro

/**
 * Modal bottom sheet shown when a tailor hits a folder or style cap.
 *
 * Visual structure (top → bottom):
 *  - Copper eyebrow: "FOLDER LIMIT REACHED" / "STYLE LIMIT REACHED"
 *  - Fraunces-serif headline tailored by [StyleCapKind]
 *  - Body copy explaining the cap and (where applicable) the next tier benefit
 *  - If a [nextTier] exists: primary "Upgrade to {tier}" Button (star icon) + "Not now" link
 *  - If already on ATELIER (top plan): single "Got it" dismiss button
 *
 * Navigation: [onUpgradeClick] fires BEFORE [onDismiss] so the caller can clear
 * the sheet state first, then route — avoids a visual flicker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleCapReachedSheet(
    info: StyleCapInfo,
    onUpgradeClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        StyleCapReachedSheetContent(
            info = info,
            onUpgradeClick = onUpgradeClick,
            onDismiss = onDismiss,
        )
    }
}

/**
 * Inner column extracted so @Preview can render it — [ModalBottomSheet] itself
 * does not lay out in preview mode (no host activity / sheet state).
 */
@Composable
internal fun StyleCapReachedSheetContent(
    info: StyleCapInfo,
    onUpgradeClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val upgradeTarget = nextTier(info.currentTier)

    val eyebrow = when (info.kind) {
        StyleCapKind.FOLDERS -> stringResource(Res.string.style_cap_folder_eyebrow)
        StyleCapKind.STYLES -> stringResource(Res.string.style_cap_style_eyebrow)
    }
    val headline = when (info.kind) {
        StyleCapKind.FOLDERS -> stringResource(Res.string.style_cap_folder_title)
        StyleCapKind.STYLES -> stringResource(Res.string.style_cap_style_title)
    }
    val body = when (info.kind) {
        StyleCapKind.FOLDERS -> stringResource(Res.string.style_cap_folder_body)
        StyleCapKind.STYLES -> stringResource(Res.string.style_cap_style_body)
    }
    val upgradeCta = when (upgradeTarget) {
        SubscriptionTier.PRO -> stringResource(Res.string.style_cap_upgrade_pro)
        SubscriptionTier.ATELIER -> stringResource(Res.string.style_cap_upgrade_atelier)
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.space4)
            .padding(bottom = DesignTokens.space5),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Eyebrow — copper accent, all caps, wide letter spacing.
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
            fontWeight = FontWeight.SemiBold,
            color = LocalStitchPadColors.current.brandAccent,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (upgradeTarget != null) {
            // Serif headline — reframe as opportunity.
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            // Body copy explaining the cap.
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(DesignTokens.space3))
            // Primary upgrade CTA.
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
                    text = upgradeCta,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            // Secondary "Not now" link.
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.style_cap_dismiss),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Already on top plan — no upgrade path available.
            Text(
                text = stringResource(Res.string.style_cap_max_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(Res.string.style_cap_max_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(DesignTokens.space3))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(DesignTokens.radiusLg),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(Res.string.style_cap_got_it),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────
// ModalBottomSheet doesn't render in @Preview; these preview the inner content.

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun StyleCapFolderProContentPreview() {
    StitchPadTheme {
        StyleCapReachedSheetContent(
            info = StyleCapInfo(kind = StyleCapKind.FOLDERS, currentTier = SubscriptionTier.PRO),
            onUpgradeClick = {},
            onDismiss = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun StyleCapStyleFreeContentPreview() {
    StitchPadTheme {
        StyleCapReachedSheetContent(
            info = StyleCapInfo(kind = StyleCapKind.STYLES, currentTier = SubscriptionTier.FREE),
            onUpgradeClick = {},
            onDismiss = {},
        )
    }
}
