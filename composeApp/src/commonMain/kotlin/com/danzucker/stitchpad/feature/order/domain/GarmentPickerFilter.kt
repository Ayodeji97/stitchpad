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
 * - Empty or blank query: return all customs + all visible presets, no Add CTA.
 * - Non-blank query: case-insensitive substring filter against each list.
 *   Add CTA appears unless the query is an EXACT case-insensitive match of an
 *   existing option (preset or custom). Substring matches alone don't suppress
 *   the CTA — a user typing "Iro" can still add it even if "Iro and Buba" exists.
 *
 * @param presets gender-filtered presets shown in the picker — used for both
 *   substring matching (the list the user sees) and the visible result.
 * @param allPresets all presets eligible for dedupe checks (typically every
 *   [GarmentType] entry except [GarmentType.OTHER], regardless of gender). The
 *   exact-match dedupe scans this list so the user can't accidentally create a
 *   custom entry that duplicates a preset hidden by the current gender filter
 *   (e.g. typing "Trouser" while MALE is selected — Trouser is UNISEX and would
 *   otherwise be missing from `presets`).
 */
fun filterGarmentOptions(
    query: String,
    customs: List<CustomGarmentType>,
    presets: List<GarmentType>,
    allPresets: List<GarmentType>,
    resolvePresetLabel: (GarmentType) -> String,
): GarmentPickerFilterResult {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) {
        return GarmentPickerFilterResult(customs, presets, showAddCustomCta = false)
    }
    val matchingCustoms = customs.filter { it.name.lowercase().contains(normalized) }
    val visibleMatchingPresets = presets.filter { resolvePresetLabel(it).lowercase().contains(normalized) }
    // If the user's exact query matches a preset that the current gender filter
    // hides (e.g. typing "Trouser" while MALE is selected — Trouser is UNISEX),
    // surface that preset anyway so they have a row to pick. Without this, the
    // dedupe below would suppress the Add CTA AND the row wouldn't be visible,
    // leaving the user stuck. Only surface on EXACT match to avoid noisy
    // cross-gender rows on substring searches.
    val trimmed = query.trim()
    val crossGenderExactMatch = allPresets.firstOrNull { preset ->
        preset !in presets && resolvePresetLabel(preset).equals(trimmed, ignoreCase = true)
    }
    val matchingPresets = visibleMatchingPresets + listOfNotNull(crossGenderExactMatch)
    // Dedupe scans `allPresets` (not the gender-filtered `presets`) so that a
    // user typing the name of a preset hidden by the current gender filter still
    // gets the "Add" CTA suppressed — no duplicate-of-preset customs.
    val exactMatch = customs.any { it.name.equals(trimmed, ignoreCase = true) } ||
        allPresets.any { resolvePresetLabel(it).equals(trimmed, ignoreCase = true) }
    val showAddCustomCta = !exactMatch
    return GarmentPickerFilterResult(matchingCustoms, matchingPresets, showAddCustomCta)
}
