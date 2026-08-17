package com.danzucker.stitchpad.core.data.staff

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.staff.RedeemedInvite
import com.danzucker.stitchpad.core.domain.staff.StaffError
import com.danzucker.stitchpad.core.domain.staff.repository.InviteRedemptionRepository

open class FakeInviteRedemptionRepository : InviteRedemptionRepository {
    var result: Result<RedeemedInvite, StaffError> =
        Result.Success(RedeemedInvite("owner-9", "Ade Fashions", MembershipStatus.PENDING))
    var lastCode: String? = null

    /** Number of [redeem] calls — asserts the double-tap guard actually blocks. */
    var redeemCallCount = 0

    var cancelResult: EmptyResult<StaffError> = Result.Success(Unit)
    var lastCancelledWorkshopUid: String? = null

    override suspend fun redeem(code: String): Result<RedeemedInvite, StaffError> {
        redeemCallCount++
        lastCode = code
        return result
    }

    override suspend fun cancelMembership(workshopUid: String): EmptyResult<StaffError> {
        lastCancelledWorkshopUid = workshopUid
        return cancelResult
    }
}
