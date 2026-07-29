package com.danzucker.stitchpad.core.data.staff

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.staff.RedeemedInvite
import com.danzucker.stitchpad.core.domain.staff.StaffError
import com.danzucker.stitchpad.core.domain.staff.repository.InviteRedemptionRepository

class FakeInviteRedemptionRepository : InviteRedemptionRepository {
    var result: Result<RedeemedInvite, StaffError> =
        Result.Success(RedeemedInvite("owner-9", "Ade Fashions", MembershipStatus.PENDING))
    var lastCode: String? = null

    override suspend fun redeem(code: String): Result<RedeemedInvite, StaffError> {
        lastCode = code
        return result
    }
}
