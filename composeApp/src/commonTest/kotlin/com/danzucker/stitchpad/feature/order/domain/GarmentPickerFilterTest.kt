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
            resolvePresetLabel = ::resolvePreset,
        )

        assertEquals(customs, result.matchingCustoms)
        assertEquals(presets, result.matchingPresets)
        assertFalse(result.showAddCustomCta)
    }

    @Test
    fun `query matches custom case-insensitively — addCta hidden`() {
        val result = filterGarmentOptions(
            query = "IRO",
            customs = customs,
            presets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        assertEquals(listOf(customs[0]), result.matchingCustoms)
        assertTrue(result.matchingPresets.isEmpty())
        assertFalse(result.showAddCustomCta)
    }

    @Test
    fun `query matches preset substring case-insensitively`() {
        val result = filterGarmentOptions(
            query = "kaf",
            customs = customs,
            presets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        assertTrue(result.matchingCustoms.isEmpty())
        assertEquals(listOf(GarmentType.KAFTAN), result.matchingPresets)
        assertFalse(result.showAddCustomCta)
    }

    @Test
    fun `query with no matches shows add CTA`() {
        val result = filterGarmentOptions(
            query = "Kente cape",
            customs = customs,
            presets = presets,
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
            resolvePresetLabel = ::resolvePreset,
        )

        assertTrue(result.matchingCustoms.isEmpty())
        assertEquals(listOf(GarmentType.AGBADA), result.matchingPresets)
        assertFalse(result.showAddCustomCta)
    }

    @Test
    fun `blank query (whitespace only) treated as empty`() {
        val result = filterGarmentOptions(
            query = "   ",
            customs = customs,
            presets = presets,
            resolvePresetLabel = ::resolvePreset,
        )

        assertEquals(customs, result.matchingCustoms)
        assertEquals(presets, result.matchingPresets)
        assertFalse(result.showAddCustomCta)
    }
}
