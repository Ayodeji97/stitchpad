package com.danzucker.stitchpad.feature.dashboard.presentation.model

import com.danzucker.stitchpad.core.presentation.UiText

/**
 * Drives [IllustratedFocusCard]'s illustration, accent colour, and copy template.
 * Resolved by the ViewModel based on the current state of orders, customers, NBAs, and pipeline.
 *
 * Priority order (first match wins):
 *   BrandNew → FirstOrder → Focus → Pickup → Earn → Steady → Quiet.
 * BrandNew is the most exclusive — the pre-customer, pre-order state, shown only
 * when the workshop is completely empty (0 customers, 0 orders).
 */
enum class FocusVariant {
    BrandNew,
    FirstOrder,
    Quiet,
    Steady,
    Earn,
    Focus,
    Pickup,
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
    val ctaLabel: UiText?,
    /**
     * Optional second line shown beneath the CTA, smaller and muted.
     * Used to split off a "for [customer name]" target from the action verb
     * — e.g. CTA reads "Create order →" with subtitle "for Omobolanle Johnson".
     */
    val ctaSubtitle: UiText? = null
)
