package com.danzucker.stitchpad.feature.dashboard.presentation.model

import com.danzucker.stitchpad.core.presentation.UiText

/**
 * Drives [IllustratedFocusCard]'s illustration, accent colour, and copy template.
 * Resolved by the ViewModel based on the current state of orders, customers, NBAs, and pipeline.
 *
 * Priority order (first match wins): FirstOrder → Focus → Pickup → Earn → Steady → Quiet.
 * The brand-new state (0 customers, 0 orders) renders no focus card — no variant.
 */
enum class FocusVariant {
    FirstOrder,
    Quiet,
    Steady,
    Earn,
    Focus,
    Pickup
}

/**
 * UI-layer rendering bundle for [IllustratedFocusCard]. The screen is dumb — it renders
 * whatever copy the resolver produced and emits a single [DashboardAction.OnFocusCtaClick]
 * that the ViewModel routes based on the current [variant].
 */
data class FocusResolution(
    val variant: FocusVariant,
    val headline: UiText,
    val supporting: UiText?,
    val ctaLabel: UiText?
)
