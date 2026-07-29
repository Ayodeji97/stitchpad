package com.danzucker.stitchpad.core.domain.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MembershipStatusTest {

    @Test
    fun parses_the_backend_wire_values() {
        assertEquals(MembershipStatus.PENDING, MembershipStatus.fromWire("pending"))
        assertEquals(MembershipStatus.ACTIVE, MembershipStatus.fromWire("active"))
        assertEquals(MembershipStatus.REVOKED, MembershipStatus.fromWire("revoked"))
    }

    @Test
    fun is_tolerant_of_case() {
        assertEquals(MembershipStatus.ACTIVE, MembershipStatus.fromWire("ACTIVE"))
    }

    @Test
    fun unknown_or_missing_status_is_null() {
        assertNull(MembershipStatus.fromWire("fancy"))
        assertNull(MembershipStatus.fromWire(null))
        assertNull(MembershipStatus.fromWire(""))
    }
}
