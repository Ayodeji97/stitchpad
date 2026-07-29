package com.danzucker.stitchpad.core.data.staff

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.staff.Membership
import com.danzucker.stitchpad.core.domain.staff.StaffError
import com.danzucker.stitchpad.core.domain.staff.StaffInvite
import com.danzucker.stitchpad.core.domain.staff.repository.StaffRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private const val TAG = "StaffRepo"

internal class CloudFunctionsStaffRepository(
    private val functions: FirebaseFunctions,
    private val firestore: FirebaseFirestore,
) : StaffRepository {

    override suspend fun generateInvite(): Result<StaffInvite, StaffError> = staffCall("generateStaffInvite") {
        val res = functions.httpsCallable("generateStaffInvite").invoke().data<GenerateInviteResponseDto>()
        StaffInvite(code = res.code, expiresAt = res.expiresAt.toLong())
    }

    override fun observeMemberships(ownerUid: String): Flow<Result<List<Membership>, StaffError>> =
        firestore.collection("users").document(ownerUid).collection("memberships")
            .snapshots()
            .map { snapshot ->
                val members = snapshot.documents.map { doc ->
                    val dto = doc.data<MembershipDto>()
                    Membership(
                        staffAuthUid = dto.staffAuthUid.ifBlank { doc.id },
                        staffEmail = dto.staffEmail,
                        staffName = dto.staffName,
                        status = MembershipStatus.fromWire(dto.status) ?: MembershipStatus.PENDING,
                    )
                }
                Result.Success(members) as Result<List<Membership>, StaffError>
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observeMemberships failed ownerUid=$ownerUid" }
                emit(Result.Error(StaffError.NETWORK))
            }

    override suspend fun approve(staffAuthUid: String): EmptyResult<StaffError> = staffCall("approveStaffMember") {
        functions.httpsCallable("approveStaffMember").invoke(StaffUidRequestDto(staffAuthUid))
        Unit
    }

    override suspend fun revoke(staffAuthUid: String): EmptyResult<StaffError> = staffCall("revokeStaffMember") {
        functions.httpsCallable("revokeStaffMember").invoke(StaffUidRequestDto(staffAuthUid))
        Unit
    }
}

/**
 * Shared invoke wrapper for the staff callables: recover the error intent from
 * the server message marker first (GitLive drops the code on iOS), then fall
 * back to the canonical code, then NETWORK for non-Functions throwables.
 */
internal suspend fun <T> staffCall(op: String, block: suspend () -> T): Result<T, StaffError> =
    try {
        Result.Success(block())
    } catch (e: FirebaseFunctionsException) {
        AppLogger.e(tag = TAG, throwable = e) { "$op failed code=${e.code}" }
        Result.Error(StaffErrorMapper.fromMessage(e.message) ?: mapFunctionsCode(e.code))
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
        AppLogger.e(tag = TAG, throwable = e) { "$op failed" }
        Result.Error(StaffErrorMapper.fromMessage(e.message) ?: StaffError.NETWORK)
    }

private fun mapFunctionsCode(code: FunctionsExceptionCode): StaffError = when (code) {
    FunctionsExceptionCode.UNAUTHENTICATED, FunctionsExceptionCode.PERMISSION_DENIED -> StaffError.UNAUTHENTICATED
    FunctionsExceptionCode.UNAVAILABLE -> StaffError.NETWORK
    else -> StaffError.UNKNOWN
}

@Serializable
private data class GenerateInviteResponseDto(
    val code: String = "",
    // Firebase callable numbers can arrive as Double on some platforms.
    val expiresAt: Double = 0.0,
)

@Serializable
private data class StaffUidRequestDto(val staffAuthUid: String)

@Serializable
private data class MembershipDto(
    val staffAuthUid: String = "",
    val staffEmail: String = "",
    val staffName: String = "",
    val status: String = "pending",
)
