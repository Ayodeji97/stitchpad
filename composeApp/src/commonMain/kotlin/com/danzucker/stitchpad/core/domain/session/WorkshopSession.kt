package com.danzucker.stitchpad.core.domain.session

/**
 * The resolved identity split for the current signed-in user.
 *
 * StitchPad historically conflated two ideas into one Firebase uid: *who is
 * signed in* and *whose data tree we read/write*. This value object separates
 * them so a staff member can operate on their owner's data without owning it.
 *
 * @param authUid    who is signed in (the Firebase Auth uid).
 * @param workshopUid whose `users/{uid}/…` tree the app reads/writes. Equal to
 *   [authUid] for an owner; the owner's uid for active staff.
 * @param role        [StaffRole.OWNER] or [StaffRole.STAFF].
 * @param membershipStatus the staff membership lifecycle state; `null` for an owner.
 */
data class WorkshopSession(
    val authUid: String,
    val workshopUid: String,
    val role: StaffRole,
    val membershipStatus: MembershipStatus?,
) {
    val isOwner: Boolean get() = role == StaffRole.OWNER

    /** True only for an approved staff member — the sole state that unlocks the owner's data. */
    val isActiveStaff: Boolean
        get() = role == StaffRole.STAFF && membershipStatus == MembershipStatus.ACTIVE

    companion object {
        /**
         * The fail-safe default: the signed-in user is the owner of their own
         * tree ([workshopUid] == [authUid]). Used before claims/membership have
         * been resolved. Worst case a staff member briefly sees their own empty
         * tree, never the owner's data under a wrong role assumption.
         */
        fun ownerOfSelf(authUid: String): WorkshopSession =
            WorkshopSession(
                authUid = authUid,
                workshopUid = authUid,
                role = StaffRole.OWNER,
                membershipStatus = null,
            )

        /** Placeholder for the signed-out state; no tree is addressable. */
        fun signedOut(): WorkshopSession = ownerOfSelf(authUid = "")
    }
}
