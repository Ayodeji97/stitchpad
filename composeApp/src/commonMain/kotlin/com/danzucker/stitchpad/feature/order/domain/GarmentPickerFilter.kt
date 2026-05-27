@file:Suppress("MatchingDeclarationName", "Filename") // file holds both the result type and the filter function

package com.danzucker.stitchpad.feature.order.domain

import com.danzucker.stitchpad.core.domain.model.CustomGarmentType
import com.danzucker.stitchpad.core.domain.model.GarmentType

/**
 * Result of running the picker's search query against the available garment options.
 *
 * @property matchingCustoms tailor-defined customs whose name contains the query (case-insensitive)
 * @property matchingPresets preset enum entries whose label contains the query (case-insensitive)
 * @property showAddCustomCta whether the green "Add '<query>' as a new garment type" affordance
 *   should appear at the top. True only when the query is non-blank AND nothing matches
 *   in either list (preset or custom).
 */
data class GarmentPickerFilterResult(
    val matchingCustoms: List<CustomGarmentType>,
    val matchingPresets: List<GarmentType>,
    val showAddCustomCta: Boolean,
)

/**
 * Pure-domain filter for the garment picker. Used by both the ViewModel
 * (to drive `OrderFormState.pickerSearchQuery` projections) and the UI
 * (to render section visibility) — keeping the algorithm here keeps both
 * consistent and unit-testable.
 *
 * Behavior:
 * - Empty or blank query: return all customs + all presets, no Add CTA.
 * - Non-blank query: case-insensitive substring filter against each list.
 *   Add CTA appears unless the query is an EXACT case-insensitive match of an
 *   existing option (preset or custom). Substring matches alone don't suppress
 *   the CTA — a user typing "Iro" can still add it even if "Iro and Buba" exists.
 */
fun filterGarmentOptions(
    query: String,
    customs: List<CustomGarmentType>,
    presets: List<GarmentType>,
    resolvePresetLabel: (GarmentType) -> String,
): GarmentPickerFilterResult {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) {
        return GarmentPickerFilterResult(customs, presets, showAddCustomCta = false)
    }
    val matchingCustoms = customs.filter { it.name.lowercase().contains(normalized) }
    val matchingPresets = presets.filter { resolvePresetLabel(it).lowercase().contains(normalized) }
    // Add CTA is hidden ONLY on EXACT case-insensitive equality with an existing
    // option (preset or custom). Substring matches alone shouldn't suppress it —
    // a user typing "Iro" can still add it as new even if "Iro and Buba" exists.
    val exactMatch = customs.any { it.name.equals(query.trim(), ignoreCase = true) } ||
        presets.any { resolvePresetLabel(it).equals(query.trim(), ignoreCase = true) }
    val showAddCustomCta = !exactMatch
    return GarmentPickerFilterResult(matchingCustoms, matchingPresets, showAddCustomCta)
}
