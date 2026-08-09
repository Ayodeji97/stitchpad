package com.danzucker.stitchpad.core.domain.staff

/**
 * A single row in the workshop's team roster (`users/{workshopUid}/team/{memberId}`).
 *
 * Two members can back the same doc shape: a [TeamMemberKind.STAFF] row is written by the
 * server when a staff invite is approved (Task 2); a [TeamMemberKind.NAMED] row is a
 * name-only placeholder the owner adds directly (e.g. for a tailor who hasn't joined the
 * app yet) so orders can still be assigned to them.
 */
data class TeamMember(
    val id: String,
    val name: String,
    val kind: TeamMemberKind,
    val colorSeed: Int,
    val status: TeamMemberStatus,
)

/** Whether a roster row is backed by a real staff account or is a name-only placeholder. */
enum class TeamMemberKind {
    STAFF,
    NAMED,
    ;

    companion object {
        /** Unknown/missing wire value defaults to NAMED — the safer, least-privileged bucket. */
        fun fromWire(value: String?): TeamMemberKind = when (value?.lowercase()) {
            "staff" -> STAFF
            else -> NAMED
        }
    }
}

/** Lifecycle of a roster row. Archiving is a status flip — rows are never deleted. */
enum class TeamMemberStatus {
    ACTIVE,
    ARCHIVED,
    ;

    companion object {
        /** Unknown/missing wire value defaults to ACTIVE so a malformed doc still shows up. */
        fun fromWire(value: String?): TeamMemberStatus = when (value?.lowercase()) {
            "archived" -> ARCHIVED
            else -> ACTIVE
        }
    }
}
