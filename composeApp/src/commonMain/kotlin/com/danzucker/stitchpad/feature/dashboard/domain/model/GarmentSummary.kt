package com.danzucker.stitchpad.feature.dashboard.domain.model

import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.OrderItem

/**
 * Structured description of an order's lead garment for dashboard surfaces.
 *
 * The domain layer deliberately carries data, not a display string: the
 * presentation layer resolves [type] through the localized garment resources
 * (or shows [customName] for custom garments) and appends the localized
 * quantity suffix when [quantity] > 1. See `GarmentSummaryLabel.kt`.
 */
data class GarmentSummary(
    val type: GarmentType,
    val customName: String? = null,
    val quantity: Int = 1,
)

fun OrderItem.toGarmentSummary(): GarmentSummary = GarmentSummary(
    type = garmentType,
    customName = customGarmentName,
    quantity = quantity,
)
