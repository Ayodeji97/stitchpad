package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

/**
 * Wire shape for `users/{workshopUid}/team/{memberId}`.
 *
 * Deliberately has NO `id` field: the document id is the sole source of truth for
 * [com.danzucker.stitchpad.core.domain.staff.TeamMember.id] (see
 * [com.danzucker.stitchpad.core.data.staff.toTeamMember]), the same
 * document-id-is-authoritative convention used for customers/orders — see
 * [com.danzucker.stitchpad.feature.customer.data.withDocumentId]. Never add an `id`
 * field here; it would only invite the same stale/blank-id class of bug fixed there.
 *
 * `kind`/`status` are free-form strings on the wire ("staff"|"named",
 * "active"|"archived") so a server-side value from another platform's future addition
 * degrades to a safe default at read time rather than failing to decode.
 * `createdAt`/`updatedAt` are epoch millis (matching the Cloud Functions `nowMs` shape
 * written by `functions/src/staff/approveStaffMember.ts`), not Firestore `Timestamp`.
 */
@Serializable
data class TeamMemberDto(
    val name: String = "",
    val kind: String = "named",
    val status: String = "active",
    val colorSeed: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
