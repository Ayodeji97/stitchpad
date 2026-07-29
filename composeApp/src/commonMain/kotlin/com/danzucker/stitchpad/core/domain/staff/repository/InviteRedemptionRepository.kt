package com.danzucker.stitchpad.core.domain.staff.repository

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.staff.RedeemedInvite
import com.danzucker.stitchpad.core.domain.staff.StaffError

/** Staff-side invite redemption, backed by the redeemStaffInvite callable. */
interface InviteRedemptionRepository {
    suspend fun redeem(code: String): Result<RedeemedInvite, StaffError>
}
