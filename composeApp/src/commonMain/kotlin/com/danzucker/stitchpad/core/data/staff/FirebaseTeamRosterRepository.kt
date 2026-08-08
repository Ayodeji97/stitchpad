package com.danzucker.stitchpad.core.data.staff

import com.danzucker.stitchpad.core.data.decodeDocOrLog
import com.danzucker.stitchpad.core.data.dto.TeamMemberDto
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.staff.TeamMember
import com.danzucker.stitchpad.core.domain.staff.TeamMemberKind
import com.danzucker.stitchpad.core.domain.staff.TeamMemberStatus
import com.danzucker.stitchpad.core.domain.staff.repository.TeamRosterRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.offline.OfflineWriteDispatcher
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

private const val TAG = "TeamRosterRepo"

/**
 * Maps a decoded [TeamMemberDto] to the domain [TeamMember].
 *
 * The document id is authoritative (see [TeamMemberDto]'s kdoc) — [docId] always wins over
 * any DTO field, so this takes it as an explicit parameter rather than reading it off the
 * DTO. A blank `name` (shouldn't happen from this app's writers, but a doc could be seeded
 * by hand or by a future writer) falls back to the doc id as a last-resort label so the row
 * never renders empty in the roster list.
 *
 * Pure + internal so it is unit-testable without a Firestore fake.
 */
internal fun TeamMemberDto.toTeamMember(docId: String): TeamMember =
    TeamMember(
        id = docId,
        name = name.ifBlank { docId },
        kind = TeamMemberKind.fromWire(kind),
        colorSeed = colorSeed,
        status = TeamMemberStatus.fromWire(status),
    )

class FirebaseTeamRosterRepository(
    private val firestore: FirebaseFirestore,
    private val offlineWrites: OfflineWriteDispatcher,
) : TeamRosterRepository {

    private fun teamCollection(workshopUid: String) =
        firestore.collection("users").document(workshopUid).collection("team")

    override fun observeTeam(workshopUid: String): Flow<Result<List<TeamMember>, DataError.Network>> =
        teamCollection(workshopUid)
            .snapshots()
            .map { snapshot ->
                val members = snapshot.documents
                    .mapNotNull { doc ->
                        decodeDocOrLog(tag = TAG, docId = doc.id) {
                            doc.data<TeamMemberDto>().toTeamMember(doc.id)
                        }
                    }
                    .sortedWith(compareBy({ it.status }, { it.name.lowercase() }))
                Result.Success(members) as Result<List<TeamMember>, DataError.Network>
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) {
                    "observeTeam failed workshopUid=$workshopUid"
                }
                emit(Result.Error(DataError.Network.UNKNOWN))
            }

    override suspend fun addNamedMember(
        workshopUid: String,
        name: String,
    ): EmptyResult<DataError.Network> {
        val trimmedName = name.trim()
        val docRef = teamCollection(workshopUid).document
        val now = Clock.System.now().toEpochMilliseconds()
        val dto = TeamMemberDto(
            name = trimmedName,
            kind = "named",
            status = "active",
            // Independent bucket from the server's uid-based colorSeedFor() (staffConstants.ts)
            // — named members have no uid to hash, so this hashes the name instead.
            colorSeed = trimmedName.hashCode().mod(COLOR_SEED_BUCKETS),
            createdAt = now,
            updatedAt = now,
        )
        val accepted = offlineWrites.enqueue("addNamedMember workshopUid=$workshopUid") {
            docRef.set(dto)
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun renameMember(
        workshopUid: String,
        memberId: String,
        name: String,
    ): EmptyResult<DataError.Network> {
        val now = Clock.System.now().toEpochMilliseconds()
        val accepted = offlineWrites.enqueue("renameMember workshopUid=$workshopUid memberId=$memberId") {
            teamCollection(workshopUid).document(memberId).set(
                mapOf("name" to name.trim(), "updatedAt" to now),
                merge = true,
            )
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun archiveMember(
        workshopUid: String,
        memberId: String,
    ): EmptyResult<DataError.Network> {
        val now = Clock.System.now().toEpochMilliseconds()
        val accepted = offlineWrites.enqueue("archiveMember workshopUid=$workshopUid memberId=$memberId") {
            teamCollection(workshopUid).document(memberId).set(
                mapOf("status" to "archived", "updatedAt" to now),
                merge = true,
            )
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }
}

private const val COLOR_SEED_BUCKETS = 10
