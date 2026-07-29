package com.danzucker.stitchpad.core.domain.staff.repository

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.staff.Membership
import com.danzucker.stitchpad.core.domain.staff.StaffError
import com.danzucker.stitchpad.core.domain.staff.StaffInvite
import kotlinx.coroutines.flow.Flow

/**
 * Owner-side staff management, backed by the staff Cloud Functions callables
 * (generate/approve/revoke) and a Firestore listener on the memberships list.
 */
interface StaffRepository {
    /** Mint an invite code for the signed-in owner's workshop. */
    suspend fun generateInvite(): Result<StaffInvite, StaffError>

    /** Live list of the owner's memberships (pending + active + revoked). */
    fun observeMemberships(ownerUid: String): Flow<Result<List<Membership>, StaffError>>

    /** Approve a pending member — grants the staff claim server-side. */
    suspend fun approve(staffAuthUid: String): EmptyResult<StaffError>

    /** Revoke a member — clears the claim and cuts access immediately. */
    suspend fun revoke(staffAuthUid: String): EmptyResult<StaffError>
}
