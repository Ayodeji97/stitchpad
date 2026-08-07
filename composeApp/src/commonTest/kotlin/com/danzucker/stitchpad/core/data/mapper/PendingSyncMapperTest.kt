package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.CustomerDto
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingSyncMapperTest {

    private fun dto() = CustomerDto(
        id = "cust-1",
        name = "Adaeze Okafor",
        phone = "+2348012345601",
    )

    @Test
    fun customer_defaults_to_not_pending() {
        assertFalse(dto().toCustomer(userId = "u1").isPendingSync)
    }

    @Test
    fun customer_carries_the_pending_flag_through_the_mapper() {
        assertTrue(dto().toCustomer(userId = "u1", isPendingSync = true).isPendingSync)
    }

    @Test
    fun with_contact_preserves_the_pending_flag() {
        val customer = dto().toCustomer(userId = "u1", isPendingSync = true)
        assertTrue(customer.withContact(null).isPendingSync)
    }
}
