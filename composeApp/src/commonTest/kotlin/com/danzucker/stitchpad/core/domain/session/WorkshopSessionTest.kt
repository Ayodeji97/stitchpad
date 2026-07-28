package com.danzucker.stitchpad.core.domain.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkshopSessionTest {

    @Test
    fun owner_of_self_points_the_workshop_at_the_signed_in_user() {
        val session = WorkshopSession.ownerOfSelf("me")

        assertEquals("me", session.authUid)
        assertEquals("me", session.workshopUid)
        assertTrue(session.isOwner)
        assertFalse(session.isActiveStaff)
    }

    @Test
    fun signed_out_default_addresses_no_tree_and_is_a_harmless_owner() {
        // The provider emits this before any uid is known; nothing reads data in
        // this state (nav gates to login), so an empty owner tree is safe.
        val session = WorkshopSession.signedOut()

        assertEquals("", session.authUid)
        assertEquals("", session.workshopUid)
        assertTrue(session.isOwner)
    }
}
