package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.staff.TeamMember
import com.danzucker.stitchpad.core.domain.staff.TeamMemberKind
import com.danzucker.stitchpad.core.domain.staff.TeamMemberStatus
import com.danzucker.stitchpad.core.domain.staff.repository.TeamRosterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

private const val COLOR_SEED_BUCKETS = 10

class FakeTeamRosterRepository : TeamRosterRepository {
    var observeError: DataError.Network? = null
    var operationError: DataError.Network? = null
    var lastAddedName: String? = null
    var lastRenamedMemberId: String? = null
    var lastRenamedName: String? = null
    var lastArchivedMemberId: String? = null

    private var nextId = 0
    private val _members = MutableStateFlow<List<TeamMember>>(emptyList())

    /** Test seed helper — set the initial roster. */
    fun seedMembers(members: List<TeamMember>) {
        _members.value = members
    }

    override fun observeTeam(workshopUid: String): Flow<Result<List<TeamMember>, DataError.Network>> =
        _members.asStateFlow().map { current ->
            observeError?.let { Result.Error(it) }
                // Mirror the interface's documented sort (active first, then name) — the
                // real Firestore-backed impl sorts server-side; this fake previously
                // passed the seed order through unsorted.
                ?: Result.Success(current.sortedWith(compareBy({ it.status }, { it.name })))
        }

    override suspend fun addNamedMember(workshopUid: String, name: String): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        val trimmedName = name.trim()
        lastAddedName = trimmedName
        val member = TeamMember(
            id = "fake-member-${nextId++}",
            name = trimmedName,
            kind = TeamMemberKind.NAMED,
            colorSeed = trimmedName.hashCode().mod(COLOR_SEED_BUCKETS),
            status = TeamMemberStatus.ACTIVE,
        )
        _members.value = _members.value + member
        return Result.Success(Unit)
    }

    override suspend fun renameMember(
        workshopUid: String,
        memberId: String,
        name: String,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        val trimmedName = name.trim()
        lastRenamedMemberId = memberId
        lastRenamedName = trimmedName
        _members.value = _members.value.map {
            if (it.id == memberId) it.copy(name = trimmedName) else it
        }
        return Result.Success(Unit)
    }

    override suspend fun archiveMember(workshopUid: String, memberId: String): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastArchivedMemberId = memberId
        _members.value = _members.value.map {
            if (it.id == memberId) it.copy(status = TeamMemberStatus.ARCHIVED) else it
        }
        return Result.Success(Unit)
    }
}
