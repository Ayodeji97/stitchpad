package com.danzucker.stitchpad.feature.staff.presentation.team

import com.danzucker.stitchpad.core.domain.staff.Membership
import com.danzucker.stitchpad.core.domain.staff.TeamMember
import com.danzucker.stitchpad.core.domain.staff.TeamMemberStatus
import com.danzucker.stitchpad.core.presentation.UiText

/**
 * Owner-facing "Team" screen state. Memberships are split into [pending] and
 * [active] (REVOKED members are dropped upstream). Seats are capped by
 * [seatCap] (a plan entitlement, default 2); the invite CTA is gated on
 * [canInvite].
 *
 * [roster] is a separate, independently-observed list (see [TeamRosterRepository])
 * covering both STAFF rows (mirroring [active]) and NAMED, account-less
 * placeholders the owner can add so work can be assigned to a tailor who
 * hasn't joined the app yet.
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
    /** Full roster as observed (active + archived) — filter to [activeRoster] for display. */
    val roster: List<TeamMember> = emptyList(),
    /**
     * The signed-in owner's own uid — resolves "You" against [activeRoster]'s owner row
     * (Task 6, `rosterDisplayName`). The Team screen is owner-only, so this is always the
     * same uid as the workshop tree itself.
     */
    val currentUserId: String? = null,
    val showAddMemberSheet: Boolean = false,
    /** Non-null while the rename sheet is shown for this roster row. */
    val renameTarget: TeamMember? = null,
    /**
     * Open-order counts per roster member id (Task 9's "who is working on what"), keyed
     * like [com.danzucker.stitchpad.core.domain.model.Order.assignedMemberId] — `null` is
     * the unassigned bucket. See [openOrderCountsByAssignee]. Independent of [roster]'s own
     * listener failures — a stalled/erroring orders stream just leaves this at its default
     * and the roster stays usable.
     */
    val workloadCounts: Map<String?, Int> = emptyMap(),
) {
    /** Pending requests count against seats too — a pending member holds a seat. */
    val seatsUsed: Int get() = pending.size + active.size

    val canInvite: Boolean get() = seatsUsed < seatCap

    /** True only once memberships have loaded and there is no team at all. */
    val isEmpty: Boolean get() = !isLoading && pending.isEmpty() && active.isEmpty()

    /** Archived rows are never deleted server-side — hide them from the roster list. */
    val activeRoster: List<TeamMember> get() = roster.filter { it.status == TeamMemberStatus.ACTIVE }

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
