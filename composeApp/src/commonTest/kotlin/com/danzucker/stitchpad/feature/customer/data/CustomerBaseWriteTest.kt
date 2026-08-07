package com.danzucker.stitchpad.feature.customer.data

import com.danzucker.stitchpad.core.data.dto.CustomerDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Slice 8d-1 (stop-dual-write): updateCustomer must stop mirroring contact onto the
 * base customer doc. The base edit-merge may only carry non-sensitive fields (name,
 * updatedAt); phone/email/address now live solely in /private/contact.
 */
class CustomerBaseWriteTest {

    @Test
    fun customerBaseUpdateFields_keepsNameAndUpdatedAt_neverContact() {
        val dto = CustomerDto(
            id = "c1",
            name = "Ada",
            phone = "08030000000",
            email = "ada@example.com",
            address = "12 Broad St",
            updatedAt = 1_700_000_000_000L,
        )

        val fields = customerBaseUpdateFields(dto)

        assertEquals(
            mapOf<String, Any?>("name" to "Ada", "updatedAt" to 1_700_000_000_000L),
            fields,
        )
        assertFalse(fields.containsKey("phone"))
        assertFalse(fields.containsKey("email"))
        assertFalse(fields.containsKey("address"))
    }
}
