package com.danzucker.stitchpad.feature.dashboard.presentation.model

import androidx.compose.runtime.Composable
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.feature.dashboard.domain.model.GarmentSummary
import com.danzucker.stitchpad.feature.order.presentation.garmentDisplayName
import com.danzucker.stitchpad.feature.order.presentation.garmentDisplayNameAsync
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_garment_with_qty

private fun GarmentSummary.customNameOrNull(): String? =
    customName?.takeIf { type == GarmentType.OTHER && it.isNotBlank() }

/** Localized garment name only ("Shirt" / "Chemise"); empty for a null summary. */
@Composable
fun garmentSummaryName(summary: GarmentSummary?): String {
    if (summary == null) return ""
    return summary.customNameOrNull() ?: garmentDisplayName(summary.type)
}

/**
 * Localized garment label with the quantity suffix when quantity > 1:
 * "Shirt · Qty 2" (EN) / "Chemise · Qté 2" (FR). Empty for a null summary.
 */
@Composable
fun garmentSummaryLabel(summary: GarmentSummary?): String {
    if (summary == null) return ""
    val name = garmentSummaryName(summary)
    return if (summary.quantity > 1) {
        stringResource(Res.string.dashboard_garment_with_qty, name, summary.quantity)
    } else {
        name
    }
}

/** Non-Composable twin of [garmentSummaryName] for suspend call sites (e.g. WhatsApp copy). */
suspend fun garmentSummaryNameAsync(summary: GarmentSummary?): String {
    if (summary == null) return ""
    return summary.customNameOrNull() ?: garmentDisplayNameAsync(summary.type)
}
