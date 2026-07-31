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
     * The membership doc never selects which tree is addressable — only the
     * server-authoritative **claim** does, because Firestore rules key access off
     * the claim. The doc is used solely to drive the pending/waiting UI and (via
     * the provider) to trigger a token refresh once approved. So there is no
     * `membershipWorkshopUid` parameter: an approved-but-claimless staffer is
     * held on their OWN tree until the claim lands, never on the owner's.
     *
     * @param authUid the signed-in Firebase uid.
     * @param claimWorkshopUid the `workshopUid` custom claim, if present on the token.
     * @param claimRole the `role` custom claim, if present ([CLAIM_ROLE_STAFF] for staff).
     * @param membershipStatus the membership lifecycle state from that doc, if read.
     */
    fun resolve(
        authUid: String,
        claimWorkshopUid: String?,
        claimRole: String?,
        membershipStatus: MembershipStatus?,
        staffFeatureEnabled: Boolean = true,
    ): WorkshopSession {
        // Remote kill-switch (config/app.staffFeatureEnabled): when the whole Owner +
        // Staff experience is disabled, ignore the claim + membership and resolve
        // EVERY user to owner-of-self. A staff member falls back to their own (empty)
        // tree — no staff nav, no owner data, no crash — so a misbehaving staff
        // release can be disabled instantly with no app release. Fail-open: the
        // default is true, so a missing/unreadable config never disables staff.
        if (!staffFeatureEnabled) return WorkshopSession.ownerOfSelf(authUid)
        // Precedence: server-authoritative claim, then the membership-doc fallback
        // (drives the waiting UI before an approved token refreshes), then the
        // fail-safe.
        return staffFromClaim(authUid, claimWorkshopUid, claimRole)
            ?: staffFromMembership(authUid, membershipStatus)
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
        membershipStatus: MembershipStatus?,
    ): WorkshopSession? = when (membershipStatus) {
        // PENDING (awaiting approval) and ACTIVE-without-claim (approved, but the
        // token has not refreshed the claim yet — else staffFromClaim would have
        // won) BOTH resolve to STAFF/PENDING on the user's OWN tree: nav routes to
        // the waiting screen and no owner data is addressable (rules would deny it
        // without the claim). The provider force-refreshes the token on ACTIVE;
        // once the claim lands, staffFromClaim promotes to the owner's tree.
        MembershipStatus.PENDING, MembershipStatus.ACTIVE ->
            WorkshopSession(authUid, authUid, StaffRole.STAFF, MembershipStatus.PENDING)
        // REVOKED or absent → no staff session; caller falls back to owner-of-self.
        MembershipStatus.REVOKED, null -> null
    }
}
