package com.danzucker.stitchpad.core.data.staff

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.staff.Membership
import com.danzucker.stitchpad.core.domain.staff.StaffError
import com.danzucker.stitchpad.core.domain.staff.StaffInvite
import com.danzucker.stitchpad.core.domain.staff.repository.StaffRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeStaffRepository : StaffRepository {
    var inviteResult: Result<StaffInvite, StaffError> = Result.Success(StaffInvite("7Q4P9RM2", 0L))

    var approveResult: EmptyResult<StaffError> = Result.Success(Unit)
    var revokeResult: EmptyResult<StaffError> = Result.Success(Unit)

    val memberships = MutableStateFlow<Result<List<Membership>, StaffError>>(Result.Success(emptyList()))

    var lastObservedOwnerUid: String? = null
    var lastApprovedUid: String? = null
    var lastRevokedUid: String? = null
    var generateInviteCount = 0

    override suspend fun generateInvite(): Result<StaffInvite, StaffError> {
        generateInviteCount++
        return inviteResult
    }

    override fun observeMemberships(ownerUid: String): Flow<Result<List<Membership>, StaffError>> {
        lastObservedOwnerUid = ownerUid
        return memberships
    }

    override suspend fun approve(staffAuthUid: String): EmptyResult<StaffError> {
        lastApprovedUid = staffAuthUid
        return approveResult
    }

    override suspend fun revoke(staffAuthUid: String): EmptyResult<StaffError> {
        lastRevokedUid = staffAuthUid
        return revokeResult
    }
}
