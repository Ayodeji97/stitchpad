package com.danzucker.stitchpad.core.domain.staff

import com.danzucker.stitchpad.core.domain.session.MembershipStatus

/** A staff member's membership in the owner's workshop (owner-side list view). */
data class Membership(
    val staffAuthUid: String,
    val staffEmail: String,
    val staffName: String,
    val status: MembershipStatus,
)

/** A freshly minted staff invite returned by generateStaffInvite. */
data class StaffInvite(
    val code: String,
    val expiresAt: Long,
)

/** Result of redeeming an invite — the workshop joined, awaiting owner approval. */
data class RedeemedInvite(
    val workshopUid: String,
    val workshopName: String,
    val status: MembershipStatus,
)
