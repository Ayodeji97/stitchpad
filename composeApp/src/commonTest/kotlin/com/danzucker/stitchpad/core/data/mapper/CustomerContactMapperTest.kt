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
        ).toCustomerContactDto(ownerId = "u1")

        assertEquals("+2348011112222", dto.phone)
        assertEquals("ada@example.com", dto.email)
        assertEquals("12 Marina, Lagos", dto.address)
    }

    @Test
    fun preserves_null_email_and_address() {
        val dto = customer(email = null, address = null).toCustomerContactDto(ownerId = "u1")

        assertEquals("+2348012345678", dto.phone)
        assertNull(dto.email)
        assertNull(dto.address)
    }

    @Test
    fun stamps_owner_id_and_customer_id_for_the_collection_group_read() {
        val dto = customer().toCustomerContactDto(ownerId = "owner-42")

        assertEquals("owner-42", dto.ownerId)
        assertEquals("c1", dto.customerId)
    }

    @Test
    fun withContact_null_keeps_the_base_customer_untouched_fallback() {
        val base = customer(phone = "+234800", email = "a@b.c", address = "Lagos")

        assertEquals(base, base.withContact(null))
    }

    @Test
    fun withContact_sub_doc_overrides_base_contact() {
        // Base carries stale/empty contact (simulating a stripped base doc).
        val base = customer(phone = "", email = null, address = null)
        val contact = customer(
            phone = "+2348011112222",
            email = "ada@example.com",
            address = "12 Marina, Lagos",
        ).toCustomerContactDto(ownerId = "u1")

        val merged = base.withContact(contact)

        assertEquals("+2348011112222", merged.phone)
        assertEquals("ada@example.com", merged.email)
        assertEquals("12 Marina, Lagos", merged.address)
    }
}
