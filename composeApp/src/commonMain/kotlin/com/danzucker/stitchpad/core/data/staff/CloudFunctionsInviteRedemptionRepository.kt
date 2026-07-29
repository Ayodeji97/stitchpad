package com.danzucker.stitchpad.core.data.staff

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.staff.RedeemedInvite
import com.danzucker.stitchpad.core.domain.staff.StaffError
import com.danzucker.stitchpad.core.domain.staff.repository.InviteRedemptionRepository
import dev.gitlive.firebase.functions.FirebaseFunctions
import kotlinx.serialization.Serializable

internal class CloudFunctionsInviteRedemptionRepository(
    private val functions: FirebaseFunctions,
) : InviteRedemptionRepository {

    override suspend fun redeem(code: String): Result<RedeemedInvite, StaffError> =
        staffCall("redeemStaffInvite") {
            val res = functions
                .httpsCallable("redeemStaffInvite")
                .invoke(RedeemRequestDto(code = code))
                .data<RedeemResponseDto>()
            RedeemedInvite(
                workshopUid = res.workshopUid,
                workshopName = res.workshopName,
                status = MembershipStatus.fromWire(res.status) ?: MembershipStatus.PENDING,
            )
        }

    override suspend fun cancelMembership(workshopUid: String): EmptyResult<StaffError> =
        staffCall("cancelStaffMembership") {
            functions
                .httpsCallable("cancelStaffMembership")
                .invoke(CancelRequestDto(workshopUid = workshopUid))
            Unit
        }
}

@Serializable
private data class RedeemRequestDto(val code: String)

@Serializable
private data class CancelRequestDto(val workshopUid: String)

@Serializable
private data class RedeemResponseDto(
    val workshopUid: String = "",
    val workshopName: String = "",
    val status: String = "pending",
)
