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
        membershipStatus: MembershipStatus? = null,
        staffFeatureEnabled: Boolean = true,
    ) = WorkshopSessionResolver.resolve(
        authUid = authUid,
        claimWorkshopUid = claimWorkshopUid,
        claimRole = claimRole,
        membershipStatus = membershipStatus,
        staffFeatureEnabled = staffFeatureEnabled,
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
    fun kill_switch_disabled_resolves_a_staff_claim_to_owner_of_self() {
        // Remote kill-switch: even a valid staff claim resolves to owner-of-self
        // when config/app.staffFeatureEnabled is false — the break-glass disable.
        val session = resolve(
            authUid = "staff-1",
            claimWorkshopUid = "owner-9",
            claimRole = WorkshopSessionResolver.CLAIM_ROLE_STAFF,
            staffFeatureEnabled = false,
        )

        assertEquals("staff-1", session.authUid)
        assertEquals("staff-1", session.workshopUid)
        assertEquals(StaffRole.OWNER, session.role)
        assertFalse(session.isActiveStaff)
    }

    @Test
    fun kill_switch_disabled_holds_a_pending_staffer_on_owner_of_self() {
        val session = resolve(
            authUid = "staff-2",
            membershipStatus = MembershipStatus.PENDING,
            staffFeatureEnabled = false,
        )

        assertEquals("staff-2", session.workshopUid)
        assertEquals(StaffRole.OWNER, session.role)
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
    fun active_membership_without_claim_is_held_on_own_tree_not_the_owners() {
        // The fallback window: owner just approved, but the staff token hasn't
        // refreshed the claim yet. The doc must NOT grant the owner's tree —
        // rules require the claim, so reads would be denied. Hold as STAFF/PENDING
        // on the staffer's own tree until the claim lands (claim path promotes).
        val session = resolve(
            authUid = "staff-1",
            membershipStatus = MembershipStatus.ACTIVE,
        )

        assertEquals("staff-1", session.workshopUid)
        assertEquals(StaffRole.STAFF, session.role)
        assertEquals(MembershipStatus.PENDING, session.membershipStatus)
        assertFalse(session.isActiveStaff)
    }

    @Test
    fun pending_membership_resolves_to_staff_pending_on_own_tree() {
        // Awaiting approval: role is STAFF (so nav routes to the pending screen)
        // but workshopUid stays owner-of-self so no owner data is addressable yet.
        val session = resolve(
            authUid = "staff-1",
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
            membershipStatus = MembershipStatus.ACTIVE,
        )

        assertEquals("owner-claim", session.workshopUid)
        assertTrue(session.isActiveStaff)
    }
}
