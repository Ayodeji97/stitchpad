package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.CustomerDto
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
}
