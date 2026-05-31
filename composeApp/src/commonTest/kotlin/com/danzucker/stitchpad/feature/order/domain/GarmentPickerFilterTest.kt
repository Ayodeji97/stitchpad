package com.danzucker.stitchpad.feature.order.domain

import com.danzucker.stitchpad.core.domain.model.CustomGarmentType
import com.danzucker.stitchpad.core.domain.model.GarmentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GarmentPickerFilterTest {

    private val customs = listOf(
        CustomGarmentType("c1", "Iro and Buba", 1L, 1L),
        CustomGarmentType("c2", "Senator cape", 2L, 2L),
    )
    private val presets = listOf(GarmentType.AGBADA, GarmentType.SENATOR, GarmentType.KAFTAN)
    private fun resolvePreset(type: GarmentType): String = type.name.lowercase()

    @Test
    fun `empty query returns all customs and all presets — addCta hidden`() {
        val result = filterGarmentOptions(
            query = "",
            customs = customs,
            presets = presets,
            allPresets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        assertEquals(customs, result.matchingCustoms)
        assertEquals(presets, result.matchingPresets)
        assertFalse(result.showAddCustomCta)
    }

    @Test
    fun `query matches custom case-insensitively — addCta shown because no exact match`() {
        val result = filterGarmentOptions(
            query = "IRO",
            customs = customs,
            presets = presets,
            allPresets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        assertEquals(listOf(customs[0]), result.matchingCustoms)
        assertTrue(result.matchingPresets.isEmpty())
        // "IRO" is a substring of "Iro and Buba" but not an exact match — CTA should appear.
        assertTrue(result.showAddCustomCta)
    }

    @Test
    fun `query matches preset substring case-insensitively — addCta shown because no exact match`() {
        val result = filterGarmentOptions(
            query = "kaf",
            customs = customs,
            presets = presets,
            allPresets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        assertTrue(result.matchingCustoms.isEmpty())
        assertEquals(listOf(GarmentType.KAFTAN), result.matchingPresets)
        // "kaf" is a substring of "kaftan" but not an exact match — CTA should appear.
        assertTrue(result.showAddCustomCta)
    }

    @Test
    fun `query with no matches shows add CTA`() {
        val result = filterGarmentOptions(
            query = "Kente cape",
            customs = customs,
            presets = presets,
            allPresets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        assertTrue(result.matchingCustoms.isEmpty())
        assertTrue(result.matchingPresets.isEmpty())
        assertTrue(result.showAddCustomCta)
    }

    @Test
    fun `query that exactly matches an existing custom case-insensitively hides add CTA`() {
        val result = filterGarmentOptions(
            query = "iro and buba",
            customs = customs,
            presets = presets,
            allPresets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        assertEquals(listOf(customs[0]), result.matchingCustoms)
        assertFalse(result.showAddCustomCta)
    }

    @Test
    fun `query matching a preset hides add CTA even if no exact custom match`() {
        val result = filterGarmentOptions(
            query = "agbada",
            customs = customs,
            presets = presets,
            allPresets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        assertTrue(result.matchingCustoms.isEmpty())
        assertEquals(listOf(GarmentType.AGBADA), result.matchingPresets)
        assertFalse(result.showAddCustomCta)
    }

    @Test
    fun `query that is a substring of an existing custom but not exact match still shows add CTA`() {
        val result = filterGarmentOptions(
            query = "Iro",
            customs = customs,
            presets = presets,
            allPresets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        // Substring match against "Iro and Buba" — but Iro itself isn't an exact match.
        assertEquals(listOf(customs[0]), result.matchingCustoms)
        assertTrue(result.showAddCustomCta)
    }

    @Test
    fun `exact match on hidden gender-filtered preset is surfaced and suppresses add CTA`() {
        // Simulate the UI's gender filter: MALE chip active narrows `presets` to AGBADA + SENATOR,
        // hiding the UNISEX preset KAFTAN. `allPresets` keeps KAFTAN for dedupe purposes.
        // Typing "kaftan" exactly should surface the hidden preset (so the user has a row to pick)
        // AND keep the Add CTA suppressed (no duplicate-of-preset customs).
        val genderFilteredPresets = listOf(GarmentType.AGBADA, GarmentType.SENATOR)

        val result = filterGarmentOptions(
            query = "kaftan",
            customs = customs,
            presets = genderFilteredPresets,
            allPresets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        // KAFTAN now appears in the visible list, and the Add CTA is hidden.
        assertEquals(listOf(GarmentType.KAFTAN), result.matchingPresets)
        assertFalse(result.showAddCustomCta)
    }

    @Test
    fun `substring match on hidden gender-filtered preset is NOT surfaced`() {
        // Only EXACT matches against hidden presets are surfaced — substring matches stay hidden
        // to avoid noisy cross-gender rows when the user is still typing or browsing.
        val genderFilteredPresets = listOf(GarmentType.AGBADA, GarmentType.SENATOR)

        val result = filterGarmentOptions(
            query = "kaf",
            customs = customs,
            presets = genderFilteredPresets,
            allPresets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        // KAFTAN is hidden by the gender filter; "kaf" is a substring not an exact match,
        // so it stays hidden. Nothing matches, so the Add CTA appears.
        assertTrue(result.matchingPresets.isEmpty())
        assertTrue(result.showAddCustomCta)
    }

    @Test
    fun `blank query whitespace-only treated as empty`() {
        val result = filterGarmentOptions(
            query = "   ",
            customs = customs,
            presets = presets,
            allPresets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        assertEquals(customs, result.matchingCustoms)
        assertEquals(presets, result.matchingPresets)
        assertFalse(result.showAddCustomCta)
    }
}
