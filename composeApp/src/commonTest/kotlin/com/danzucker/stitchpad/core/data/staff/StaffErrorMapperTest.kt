package com.danzucker.stitchpad.core.data.staff

import com.danzucker.stitchpad.core.domain.staff.StaffError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StaffErrorMapperTest {

    @Test
    fun recovers_known_markers_from_the_message() {
        assertEquals(StaffError.SEAT_CAP_REACHED, StaffErrorMapper.fromMessage("seat_cap_reached"))
        assertEquals(StaffError.INVITE_EXPIRED, StaffErrorMapper.fromMessage("invite_expired"))
        assertEquals(StaffError.ALREADY_MEMBER, StaffErrorMapper.fromMessage("already_member"))
        assertEquals(
            StaffError.CANNOT_JOIN_OWN_WORKSHOP,
            StaffErrorMapper.fromMessage("cannot_join_own_workshop"),
        )
        assertEquals(StaffError.MEMBERSHIP_REVOKED, StaffErrorMapper.fromMessage("membership_revoked"))
    }

    @Test
    fun matches_a_marker_embedded_in_a_larger_message() {
        // iOS may wrap the marker in extra text; substring match still recovers it.
        assertEquals(
            StaffError.SEAT_CAP_REACHED,
            StaffErrorMapper.fromMessage("FUNCTIONS: seat_cap_reached (functionsError)"),
        )
    }

    @Test
    fun unknown_or_missing_message_is_null_so_the_caller_falls_back_to_the_code() {
        assertNull(StaffErrorMapper.fromMessage("something else"))
        assertNull(StaffErrorMapper.fromMessage(null))
    }
}
