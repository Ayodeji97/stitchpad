@file:Suppress("MatchingDeclarationName")

package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.onboarding_measurements

/**
 * Slots for empty-state illustrations. One drawable per slot, mirrored
 * from the spec's illustration slugs.
 */
enum class EmptyIllustrationSlot {
    Pipeline,
    Nba,
    Customers,
}

/**
 * Maps a FocusVariant to its hero illustration drawable.
 *
 * V1 placeholder strategy: every branch returns the existing onboarding
 * placeholder. When the V2 illustrations are generated, replace each
 * branch with the variant-specific drawable
 * (e.g. Res.drawable.dashboard_hero_busy).
 */
fun heroIllustrationFor(variant: FocusVariant): DrawableResource = when (variant) {
    FocusVariant.FirstOrder -> Res.drawable.onboarding_measurements
    FocusVariant.Quiet -> Res.drawable.onboarding_measurements
    FocusVariant.Steady -> Res.drawable.onboarding_measurements
    FocusVariant.Earn -> Res.drawable.onboarding_measurements
    FocusVariant.Focus -> Res.drawable.onboarding_measurements
    FocusVariant.Pickup -> Res.drawable.onboarding_measurements
}

/**
 * Maps an empty-state slot to its illustration drawable. Same placeholder
 * strategy as [heroIllustrationFor].
 */
fun emptyIllustrationFor(slot: EmptyIllustrationSlot): DrawableResource = when (slot) {
    EmptyIllustrationSlot.Pipeline -> Res.drawable.onboarding_measurements
    EmptyIllustrationSlot.Nba -> Res.drawable.onboarding_measurements
    EmptyIllustrationSlot.Customers -> Res.drawable.onboarding_measurements
}

@Composable
fun DashboardIllustration(
    drawable: DrawableResource,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
) {
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}
