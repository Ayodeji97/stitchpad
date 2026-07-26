package com.danzucker.stitchpad.core.domain.session

/**
 * Pure resolution of a [WorkshopSession] from the two sources of truth about
 * staff membership: the custom auth **claim** (server-authoritative, on the ID
 * token) and, as a fallback for the window before a freshly-approved token
 * refreshes, the staff member's own **membership** document.
 *
 * Kept pure (no Firebase) so the whole safety model — precedence, the pending
 * state, and the owner-of-self fail-safe — is unit-testable, mirroring how
 * [com.danzucker.stitchpad.core.domain.entitlement.EntitlementsCalculator] is
 * the tested core behind the thin Firebase provider.
 */
object WorkshopSessionResolver {

    /** Value of the custom-claim `role` field that marks a staff token. */
    const val CLAIM_ROLE_STAFF = "staff"

    /**
     * @param authUid the signed-in Firebase uid.
     * @param claimWorkshopUid the `workshopUid` custom claim, if present on the token.
     * @param claimRole the `role` custom claim, if present ([CLAIM_ROLE_STAFF] for staff).
     * @param membershipWorkshopUid the workshop from the staff member's membership doc, if read.
     * @param membershipStatus the membership lifecycle state from that doc, if read.
     */
    fun resolve(
        authUid: String,
        claimWorkshopUid: String?,
        claimRole: String?,
        membershipWorkshopUid: String?,
        membershipStatus: MembershipStatus?,
    ): WorkshopSession {
        // Precedence: server-authoritative claim, then the membership-doc fallback
        // (for the window before an approved token refreshes), then the fail-safe.
        return staffFromClaim(authUid, claimWorkshopUid, claimRole)
            ?: staffFromMembership(authUid, membershipWorkshopUid, membershipStatus)
            ?: WorkshopSession.ownerOfSelf(authUid)
    }

    private fun staffFromClaim(
        authUid: String,
        claimWorkshopUid: String?,
        claimRole: String?,
    ): WorkshopSession? =
        if (claimRole == CLAIM_ROLE_STAFF && claimWorkshopUid != null) {
            WorkshopSession(
                authUid = authUid,
                workshopUid = claimWorkshopUid,
                role = StaffRole.STAFF,
                membershipStatus = MembershipStatus.ACTIVE,
            )
        } else {
            null
        }

    private fun staffFromMembership(
        authUid: String,
        membershipWorkshopUid: String?,
        membershipStatus: MembershipStatus?,
    ): WorkshopSession? = when (membershipStatus) {
        MembershipStatus.ACTIVE -> membershipWorkshopUid?.let {
            WorkshopSession(authUid, it, StaffRole.STAFF, MembershipStatus.ACTIVE)
        }
        // Not yet approved: STAFF role so nav routes to the pending screen, but
        // workshopUid stays owner-of-self so no owner data is addressable yet.
        MembershipStatus.PENDING ->
            WorkshopSession(authUid, authUid, StaffRole.STAFF, MembershipStatus.PENDING)
        // REVOKED or absent → no staff session; caller falls back to owner-of-self.
        MembershipStatus.REVOKED, null -> null
    }
}
