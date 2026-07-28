package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.domain.model.Customer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomerContactMapperTest {

    private fun customer(
        phone: String = "+2348012345678",
        email: String? = null,
        address: String? = null,
    ) = Customer(
        id = "c1",
        userId = "u1",
        name = "Ada Fashions",
        phone = phone,
        email = email,
        address = address,
    )

    @Test
    fun maps_all_contact_fields() {
        val dto = customer(
            phone = "+2348011112222",
            email = "ada@example.com",
            address = "12 Marina, Lagos",
        ).toCustomerContactDto()

        assertEquals("+2348011112222", dto.phone)
        assertEquals("ada@example.com", dto.email)
        assertEquals("12 Marina, Lagos", dto.address)
    }

    @Test
    fun preserves_null_email_and_address() {
        val dto = customer(email = null, address = null).toCustomerContactDto()

        assertEquals("+2348012345678", dto.phone)
        assertNull(dto.email)
        assertNull(dto.address)
    }
}
