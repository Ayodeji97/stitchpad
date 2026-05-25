package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.CustomerDto
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomerMapperTest {

    @Test
    fun toDomain_defaults_slotState_to_ACTIVE_when_empty() {
        val dto = CustomerDto(slotState = "") // simulate legacy doc / empty
        val domain = dto.toCustomer("u1")
        assertEquals(CustomerSlotState.ACTIVE, domain.slotState)
        assertNull(domain.lockedAt)
    }

    @Test
    fun toDomain_round_trip_preserves_LOCKED_and_lockedAt() {
        val dto = CustomerDto(slotState = "locked", lockedAt = 1_731_000_000_000L)
        val domain = dto.toCustomer("u1")
        assertEquals(CustomerSlotState.LOCKED, domain.slotState)
        assertEquals(1_731_000_000_000L, domain.lockedAt)
        val backToDto = domain.toCustomerDto()
        assertEquals("locked", backToDto.slotState)
        assertEquals(1_731_000_000_000L, backToDto.lockedAt)
    }

    @Test
    fun toDomain_defaults_slotState_to_ACTIVE_when_missing_from_legacy_doc() {
        // Default CustomerDto.slotState = "active" simulates a legacy doc with no field
        val dto = CustomerDto()
        val domain = dto.toCustomer("u1")
        assertEquals(CustomerSlotState.ACTIVE, domain.slotState)
        assertNull(domain.lockedAt)
    }

    /**
     * Documents the root-cause of the "edit unlocks slotState" bug (Bug 2):
     * Customer's default slotState is ACTIVE, so a form edit that reconstructs
     * Customer without preserving the existing slotState will serialize "active"
     * back to Firestore, silently unlocking a LOCKED customer.
     *
     * The fix lives in FirebaseCustomerRepository.updateCustomer, which reads
     * the existing doc and copies slotState + lockedAt into the write payload.
     * This test documents the mapper-level root cause and confirms that a
     * Customer with default ACTIVE serializes to "active" — illustrating exactly
     * what updateCustomer must override when the existing doc is LOCKED.
     */
    @Test
    fun toDto_uses_Customer_default_ACTIVE_when_slotState_not_explicitly_set() {
        // A form edit that builds Customer without reading the existing doc
        // gets the Kotlin default: slotState = ACTIVE, lockedAt = null.
        val editedCustomer = Customer(
            id = "c1",
            userId = "u1",
            name = "Folake Edited",
            phone = "08012345678",
            // slotState defaults to ACTIVE — form did not load or preserve it
        )
        val dto = editedCustomer.toCustomerDto()
        // Without the repo-level fix, this "active" would overwrite a LOCKED doc.
        assertEquals("active", dto.slotState)
        assertNull(dto.lockedAt)
    }
}
