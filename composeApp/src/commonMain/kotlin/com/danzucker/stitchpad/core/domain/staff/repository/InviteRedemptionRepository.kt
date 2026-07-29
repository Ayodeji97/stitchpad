package com.danzucker.stitchpad.core.domain.staff.repository

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.staff.RedeemedInvite
import com.danzucker.stitchpad.core.domain.staff.StaffError

/** Staff-side membership actions, backed by the staff callables. */
interface InviteRedemptionRepository {
    suspend fun redeem(code: String): Result<RedeemedInvite, StaffError>

    /**
     * Cancels the caller's OWN membership in [workshopUid] — the "leave workshop"
     * action. Works for a pending request or an active member leaving; the server
     * revokes the membership and clears any staff claim.
     */
    suspend fun cancelMembership(workshopUid: String): EmptyResult<StaffError>
}
