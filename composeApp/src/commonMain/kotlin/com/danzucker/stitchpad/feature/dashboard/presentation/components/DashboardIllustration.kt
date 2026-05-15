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
import stitchpad.composeapp.generated.resources.dashboard_empty_customers
import stitchpad.composeapp.generated.resources.dashboard_empty_nba
import stitchpad.composeapp.generated.resources.dashboard_empty_pipeline
import stitchpad.composeapp.generated.resources.dashboard_hero_busy
import stitchpad.composeapp.generated.resources.dashboard_hero_first_order
import stitchpad.composeapp.generated.resources.dashboard_hero_money
import stitchpad.composeapp.generated.resources.dashboard_hero_pickup
import stitchpad.composeapp.generated.resources.dashboard_hero_quiet
import stitchpad.composeapp.generated.resources.dashboard_hero_steady
import stitchpad.composeapp.generated.resources.dashboard_hero_welcome

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
 * Each variant maps to its dedicated V2 illustration generated for the
 * dashboard redesign. Every branch is exhaustive — adding a new variant
 * without a corresponding drawable will fail at compile time.
 */
fun heroIllustrationFor(variant: FocusVariant): DrawableResource = when (variant) {
    FocusVariant.BrandNew -> Res.drawable.dashboard_hero_welcome
    FocusVariant.FirstOrder -> Res.drawable.dashboard_hero_first_order
    FocusVariant.Quiet -> Res.drawable.dashboard_hero_quiet
    FocusVariant.Steady -> Res.drawable.dashboard_hero_steady
    FocusVariant.Earn -> Res.drawable.dashboard_hero_money
    FocusVariant.Focus -> Res.drawable.dashboard_hero_busy
    FocusVariant.Pickup -> Res.drawable.dashboard_hero_pickup
}

/**
 * Maps an empty-state slot to its illustration drawable.
 */
fun emptyIllustrationFor(slot: EmptyIllustrationSlot): DrawableResource = when (slot) {
    EmptyIllustrationSlot.Pipeline -> Res.drawable.dashboard_empty_pipeline
    EmptyIllustrationSlot.Nba -> Res.drawable.dashboard_empty_nba
    EmptyIllustrationSlot.Customers -> Res.drawable.dashboard_empty_customers
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
