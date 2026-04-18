package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.StyleDto
import com.danzucker.stitchpad.core.domain.model.Style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StyleMapperTest {

    // --- StyleDto to Style ---

    @Test
    fun dtoToStyleMapsAllFields_andAttachesCustomerId() {
        val dto = StyleDto(
            id = "style-1",
            description = "Red agbada",
            photoUrl = "https://example.com/p.jpg",
            photoStoragePath = "users/u1/customers/c1/styles/style-1.jpg",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_100_000L,
        )

        val style = dto.toStyle(customerId = "c1")

        assertEquals("style-1", style.id)
        assertEquals("c1", style.customerId)
        assertEquals("Red agbada", style.description)
        assertEquals("https://example.com/p.jpg", style.photoUrl)
        assertEquals("users/u1/customers/c1/styles/style-1.jpg", style.photoStoragePath)
        assertEquals(1_700_000_000_000L, style.createdAt)
        assertEquals(1_700_000_100_000L, style.updatedAt)
    }

    @Test
    fun dtoToStyleWithDefaults_producesBlankDomainFields() {
        val dto = StyleDto()

        val style = dto.toStyle(customerId = "c1")

        assertEquals("", style.id)
        assertEquals("c1", style.customerId)
        assertEquals("", style.description)
        assertEquals("", style.photoUrl)
        assertEquals("", style.photoStoragePath)
        assertEquals(0L, style.createdAt)
        assertEquals(0L, style.updatedAt)
    }

    // --- Style to StyleDto ---

    @Test
    fun styleToDtoMapsAllFields() {
        val style = Style(
            id = "style-2",
            customerId = "c1",
            description = "Blue senator kaftan",
            photoUrl = "https://example.com/q.jpg",
            photoStoragePath = "users/u1/customers/c1/styles/style-2.jpg",
            createdAt = 1_700_000_000_000L,
            updatedAt = 0L,
        )

        val dto = style.toStyleDto()

        assertEquals("style-2", dto.id)
        assertEquals("Blue senator kaftan", dto.description)
        assertEquals("https://example.com/q.jpg", dto.photoUrl)
        assertEquals("users/u1/customers/c1/styles/style-2.jpg", dto.photoStoragePath)
        // createdAt is preserved when non-zero
        assertEquals(1_700_000_000_000L, dto.createdAt)
    }

    @Test
    fun styleToDto_setsCreatedAtToNow_whenStyleCreatedAtIsZero() {
        val before = nowMillis()
        val style = Style(
            id = "style-3",
            customerId = "c1",
            description = "Ankara gown",
            photoUrl = "",
            photoStoragePath = "",
            createdAt = 0L,
            updatedAt = 0L,
        )

        val dto = style.toStyleDto()

        val after = nowMillis()
        assertTrue(dto.createdAt in before..after, "createdAt $dto.createdAt not in [$before, $after]")
        assertTrue(dto.updatedAt in before..after, "updatedAt $dto.updatedAt not in [$before, $after]")
    }

    @Test
    fun styleToDto_alwaysSetsUpdatedAtToNow() {
        val style = Style(
            id = "style-4",
            customerId = "c1",
            description = "d",
            photoUrl = "",
            photoStoragePath = "",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
        )

        val dto = style.toStyleDto()

        assertNotEquals(1_700_000_000_000L, dto.updatedAt)
    }

    // --- Round-trip ---

    @Test
    fun roundTripDtoToStyleToDto_preservesStableFields() {
        val original = StyleDto(
            id = "style-5",
            description = "Round",
            photoUrl = "https://example.com/r.jpg",
            photoStoragePath = "users/u1/customers/c1/styles/style-5.jpg",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_100_000L,
        )

        val roundTripped = original.toStyle(customerId = "c1").toStyleDto()

        assertEquals(original.id, roundTripped.id)
        assertEquals(original.description, roundTripped.description)
        assertEquals(original.photoUrl, roundTripped.photoUrl)
        assertEquals(original.photoStoragePath, roundTripped.photoStoragePath)
        assertEquals(original.createdAt, roundTripped.createdAt)
        // updatedAt is intentionally refreshed by the mapper
    }

    private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
}
