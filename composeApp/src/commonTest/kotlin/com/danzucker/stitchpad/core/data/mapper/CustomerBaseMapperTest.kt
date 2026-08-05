package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.CustomerBaseDto
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import kotlinx.serialization.descriptors.elementNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Slice 8d-1 (stop-dual-write): the base customer doc must stop carrying contact.
 * The base-write DTO's wire shape must not declare phone/email/address — those are
 * the staff-poaching-sensitive fields that now live only in `/private/contact`.
 */
class CustomerBaseMapperTest {

    private val contactFields = setOf("phone", "email", "address")

    private fun customer() = Customer(
        id = "cust-1",
        userId = "u1",
        name = "Ada",
        phone = "08030000000",
        email = "ada@example.com",
        address = "12 Broad St",
        createdAt = 10L,
        slotState = CustomerSlotState.ACTIVE,
        lockedAt = null,
    )

    @Test
    fun baseDto_wireShape_declaresNoContactField() {
        val names = CustomerBaseDto.serializer().descriptor.elementNames.toSet()

        contactFields.forEach { field ->
            assertFalse(field in names, "CustomerBaseDto must not declare contact field '$field'")
        }
    }

    @Test
    fun baseDto_wireShape_keepsNonContactFields() {
        val names = CustomerBaseDto.serializer().descriptor.elementNames.toSet()

        listOf("id", "name", "createdAt", "updatedAt", "slotState", "lockedAt")
            .forEach { field -> assertTrue(field in names, "CustomerBaseDto must keep field '$field'") }
    }

    @Test
    fun toCustomerBaseDto_carriesNameAndSlotButNotContact() {
        val dto = customer().toCustomerBaseDto()

        assertEquals("cust-1", dto.id)
        assertEquals("Ada", dto.name)
        assertEquals(CustomerSlotState.ACTIVE.wireValue, dto.slotState)
    }
}
