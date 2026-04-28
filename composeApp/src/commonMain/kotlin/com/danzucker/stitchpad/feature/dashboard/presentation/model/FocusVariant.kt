package com.danzucker.stitchpad.feature.dashboard.presentation.model

import com.danzucker.stitchpad.core.presentation.UiText

/**
 * Drives the FocusTodayCard's icon, accent colour, and copy template. Resolved by the
 * ViewModel based on the current state of orders, customers, NBAs, and pipeline.
 *
 * Priority order (first match wins): FirstOrder → Focus → Earn → Steady → Quiet.
 * The brand-new state (0 customers, 0 orders) renders WelcomeHero instead — no variant.
 */
enum class FocusVariant {
    FirstOrder,
    Quiet,
    Steady,
    Earn,
    Focus
}

/**
 * UI-layer rendering bundle for the FocusTodayCard. The screen is dumb — it renders
 * whatever copy the resolver produced and emits a single [DashboardAction.OnFocusCtaClick]
 * that the ViewModel routes based on the current [variant].
 */
data class FocusResolution(
    val variant: FocusVariant,
    val headline: UiText,
    val supporting: UiText?,
    val ctaLabel: UiText?
)
