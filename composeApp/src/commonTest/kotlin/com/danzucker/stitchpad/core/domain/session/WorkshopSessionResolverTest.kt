package com.danzucker.stitchpad.core.domain.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkshopSessionResolverTest {

    private fun resolve(
        authUid: String = "user-self",
        claimWorkshopUid: String? = null,
        claimRole: String? = null,
        membershipWorkshopUid: String? = null,
        membershipStatus: MembershipStatus? = null,
    ) = WorkshopSessionResolver.resolve(
        authUid = authUid,
        claimWorkshopUid = claimWorkshopUid,
        claimRole = claimRole,
        membershipWorkshopUid = membershipWorkshopUid,
        membershipStatus = membershipStatus,
    )

    @Test
    fun no_claim_no_membership_resolves_to_owner_of_self() {
        val session = resolve(authUid = "owner-1")

        assertEquals("owner-1", session.authUid)
        assertEquals("owner-1", session.workshopUid)
        assertEquals(StaffRole.OWNER, session.role)
        assertTrue(session.isOwner)
        assertFalse(session.isActiveStaff)
    }

    @Test
    fun staff_claim_resolves_to_active_staff_on_owners_tree() {
        val session = resolve(
            authUid = "staff-1",
            claimWorkshopUid = "owner-9",
            claimRole = WorkshopSessionResolver.CLAIM_ROLE_STAFF,
        )

        assertEquals("staff-1", session.authUid)
        assertEquals("owner-9", session.workshopUid)
        assertEquals(StaffRole.STAFF, session.role)
        assertEquals(MembershipStatus.ACTIVE, session.membershipStatus)
        assertTrue(session.isActiveStaff)
        assertFalse(session.isOwner)
    }

    @Test
    fun active_membership_without_claim_resolves_to_active_staff() {
        // The fallback window: owner just approved, but the staff token hasn't
        // refreshed yet, so the claim is absent. The membership doc drives the UI.
        val session = resolve(
            authUid = "staff-1",
            membershipWorkshopUid = "owner-9",
            membershipStatus = MembershipStatus.ACTIVE,
        )

        assertEquals("owner-9", session.workshopUid)
        assertTrue(session.isActiveStaff)
    }

    @Test
    fun pending_membership_resolves_to_staff_pending_on_own_tree() {
        // Awaiting approval: role is STAFF (so nav routes to the pending screen)
        // but workshopUid stays owner-of-self so no owner data is addressable yet.
        val session = resolve(
            authUid = "staff-1",
            membershipWorkshopUid = "owner-9",
            membershipStatus = MembershipStatus.PENDING,
        )

        assertEquals("staff-1", session.workshopUid)
        assertEquals(StaffRole.STAFF, session.role)
        assertEquals(MembershipStatus.PENDING, session.membershipStatus)
        assertFalse(session.isActiveStaff)
    }

    @Test
    fun revoked_membership_reverts_to_owner_of_self() {
        val session = resolve(
            authUid = "staff-1",
            membershipWorkshopUid = "owner-9",
            membershipStatus = MembershipStatus.REVOKED,
        )

        assertEquals("staff-1", session.workshopUid)
        assertTrue(session.isOwner)
    }

    @Test
    fun malformed_staff_claim_without_workshop_uid_reverts_to_owner_of_self() {
        val session = resolve(
            authUid = "staff-1",
            claimWorkshopUid = null,
            claimRole = WorkshopSessionResolver.CLAIM_ROLE_STAFF,
        )

        assertEquals("staff-1", session.workshopUid)
        assertTrue(session.isOwner)
    }

    @Test
    fun claim_takes_precedence_over_membership_doc() {
        // The claim is server-authoritative; if the two ever disagree, trust the claim.
        val session = resolve(
            authUid = "staff-1",
            claimWorkshopUid = "owner-claim",
            claimRole = WorkshopSessionResolver.CLAIM_ROLE_STAFF,
            membershipWorkshopUid = "owner-doc",
            membershipStatus = MembershipStatus.ACTIVE,
        )

        assertEquals("owner-claim", session.workshopUid)
    }
}
