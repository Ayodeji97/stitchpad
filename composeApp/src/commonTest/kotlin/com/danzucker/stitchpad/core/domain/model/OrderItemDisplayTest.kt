package com.danzucker.stitchpad.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class OrderItemDisplayTest {

    private fun resolver(type: GarmentType): String = "label-${type.name}"

    @Test
    fun `returns custom name when garmentType is OTHER and customGarmentName is non-blank`() {
        val item = OrderItem(
            id = "1",
            garmentType = GarmentType.OTHER,
            customGarmentName = "Iro and Buba",
            description = "",
            price = 0.0,
        )

        val result = item.displayGarmentName(::resolver)

        assertEquals("Iro and Buba", result)
    }

    @Test
    fun `returns resolved enum label for preset garmentType`() {
        val item = OrderItem(
            id = "1",
            garmentType = GarmentType.AGBADA,
            customGarmentName = null,
            description = "",
            price = 0.0,
        )

        val result = item.displayGarmentName(::resolver)

        assertEquals("label-AGBADA", result)
    }

    @Test
    fun `falls back to resolver when garmentType is OTHER but customGarmentName is null`() {
        val item = OrderItem(
            id = "1",
            garmentType = GarmentType.OTHER,
            customGarmentName = null,
            description = "",
            price = 0.0,
        )

        val result = item.displayGarmentName(::resolver)

        assertEquals("label-OTHER", result)
    }

    @Test
    fun `falls back to resolver when garmentType is OTHER but customGarmentName is blank`() {
        val item = OrderItem(
            id = "1",
            garmentType = GarmentType.OTHER,
            customGarmentName = "   ",
            description = "",
            price = 0.0,
        )

        val result = item.displayGarmentName(::resolver)

        assertEquals("label-OTHER", result)
    }

    @Test
    fun `ignores customGarmentName when garmentType is a preset`() {
        val item = OrderItem(
            id = "1",
            garmentType = GarmentType.AGBADA,
            customGarmentName = "Iro and Buba",
            description = "",
            price = 0.0,
        )

        val result = item.displayGarmentName(::resolver)

        assertEquals("label-AGBADA", result)
    }
}
