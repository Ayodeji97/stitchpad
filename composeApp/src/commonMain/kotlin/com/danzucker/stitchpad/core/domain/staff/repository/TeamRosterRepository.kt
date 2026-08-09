package com.danzucker.stitchpad.core.domain.staff.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.staff.TeamMember
import kotlinx.coroutines.flow.Flow

/**
 * Owner + Staff team roster (`users/{workshopUid}/team/{memberId}`), backed directly by
 * Firestore (unlike [com.danzucker.stitchpad.core.domain.staff.repository.StaffRepository],
 * which goes through Cloud Functions callables for the account-linked invite flow).
 *
 * Name-only rows added here ([addNamedMember]) let the owner assign work to a tailor who
 * hasn't joined the app yet. Rows are never deleted server-side (Firestore rules deny
 * `delete`); [archiveMember] is a status flip that hides the row from active pickers while
 * preserving historical assignment references.
 */
interface TeamRosterRepository {
    /** Live roster for the workshop, active rows first, then alphabetical by name. */
    fun observeTeam(workshopUid: String): Flow<Result<List<TeamMember>, DataError.Network>>

    /** Add a name-only placeholder member (not backed by a staff account). */
    suspend fun addNamedMember(workshopUid: String, name: String): EmptyResult<DataError.Network>

    /** Rename an existing roster row (staff or named). */
    suspend fun renameMember(workshopUid: String, memberId: String, name: String): EmptyResult<DataError.Network>

    /** Archive a roster row. Never deletes — archiving is a status merge-set. */
    suspend fun archiveMember(workshopUid: String, memberId: String): EmptyResult<DataError.Network>
}
