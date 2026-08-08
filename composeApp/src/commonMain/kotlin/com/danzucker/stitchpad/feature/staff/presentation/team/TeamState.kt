package com.danzucker.stitchpad.feature.staff.presentation.team

import com.danzucker.stitchpad.core.domain.staff.Membership
import com.danzucker.stitchpad.core.presentation.UiText

/**
 * Owner-facing "Team" screen state. Memberships are split into [pending] and
 * [active] (REVOKED members are dropped upstream). Seats are capped by
 * [seatCap] (a plan entitlement, default 2); the invite CTA is gated on
 * [canInvite].
 */
data class TeamState(
    val isLoading: Boolean = true,
    val pending: List<Membership> = emptyList(),
    val active: List<Membership> = emptyList(),
    val seatCap: Int = DEFAULT_SEAT_CAP,
    val invite: TeamInviteUi? = null,
    val isGeneratingInvite: Boolean = false,
    val revokeTarget: Membership? = null,
    val errorMessage: UiText? = null,
    /**
     * Approve/decline calls currently in flight, keyed by staffAuthUid. Approving
     * is a Firestore round-trip with no local echo, so without this the buttons
     * stayed idle and invited a second tap that fired a duplicate request.
     */
    val inFlightDecisions: Map<String, TeamDecision> = emptyMap(),
) {
    /** Pending requests count against seats too — a pending member holds a seat. */
    val seatsUsed: Int get() = pending.size + active.size

    val canInvite: Boolean get() = seatsUsed < seatCap

    /** True only once memberships have loaded and there is no team at all. */
    val isEmpty: Boolean get() = !isLoading && pending.isEmpty() && active.isEmpty()

    companion object {
        const val DEFAULT_SEAT_CAP = 2
    }
}

/** Which way an owner resolved a pending join request. */
enum class TeamDecision { APPROVE, DECLINE }

/**
 * A freshly minted invite as shown in the invite sheet. [displayCode] renders the
 * raw code with a hyphen at the midpoint for readability (7Q4P9RM2 -> "7Q4P-9RM2").
 */
data class TeamInviteUi(
    val code: String,
    val expiresInDays: Int,
) {
    val displayCode: String
        get() {
            if (code.length < 2) return code
            val mid = code.length / 2
            return "${code.take(mid)}-${code.drop(mid)}"
        }
}
