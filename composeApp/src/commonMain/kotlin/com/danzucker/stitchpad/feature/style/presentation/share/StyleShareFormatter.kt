package com.danzucker.stitchpad.feature.style.presentation.share

import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier

/**
 * Builds the WhatsApp/share caption for a style. Pure and unit-testable.
 * Free tier carries a StitchPad attribution line (free distribution); paid
 * tiers get a clean caption. Mirrors MeasurementShareFormatter's role.
 */
object StyleShareFormatter {

    private const val ATTRIBUTION = "Shared via StitchPad · getstitchpad.com"

    fun caption(style: Style, tier: SubscriptionTier): String? {
        val description = style.description.trim().takeIf { it.isNotBlank() }
        val attribution = ATTRIBUTION.takeIf { tier == SubscriptionTier.FREE }
        return when {
            description != null && attribution != null -> "$description\n\n$attribution"
            description != null -> description
            else -> attribution
        }
    }
}
