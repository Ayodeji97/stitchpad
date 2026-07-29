package com.danzucker.stitchpad.core.domain.session

/**
 * Snapshot of the staff-related custom auth claims on the signed-in user's ID
 * token. Fed to [WorkshopSessionResolver] by [com.danzucker.stitchpad.core.data.session.FirebaseActiveWorkshopProvider].
 *
 * [workshopUid] and [role] are null for an owner (no claims) or for a staff
 * member whose approval hasn't refreshed onto the token yet.
 */
data class WorkshopClaims(
    val authUid: String,
    val workshopUid: String?,
    val role: String?,
)
