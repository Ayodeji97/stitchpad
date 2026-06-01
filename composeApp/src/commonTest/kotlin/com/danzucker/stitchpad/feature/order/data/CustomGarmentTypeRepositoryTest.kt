package com.danzucker.stitchpad.feature.order.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CustomGarmentTypeRepositoryTest {
    @Test
    fun customGarmentUpsertFields_doesNotOverwriteCreatedAt() {
        val fields = customGarmentUpsertFields(
            id = "iro~2fbuba",
            name = "Iro/Buba",
            lastUsedAt = 1_800L,
        )

        assertEquals("iro~2fbuba", fields["id"])
        assertEquals("Iro/Buba", fields["name"])
        assertEquals(1_800L, fields["lastUsedAt"])
        assertFalse(fields.containsKey("createdAt"))
    }
}
