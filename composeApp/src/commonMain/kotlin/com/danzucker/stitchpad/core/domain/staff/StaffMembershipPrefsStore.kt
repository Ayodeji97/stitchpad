package com.danzucker.stitchpad.core.domain.staff

/**
 * Persists the workshopUid a staff member joined (learned at redeem time) so the
 * session provider can watch their membership doc during the PENDING window —
 * before the approval claim lands on the ID token. Cleared on sign-out / leave.
 *
 * Only needed for the pending window; once approved, workshopUid comes from the
 * custom claim, so a cleared/reinstalled store self-heals for an active member.
 */
interface StaffMembershipPrefsStore {
    suspend fun getWorkshopUid(): String?
    suspend fun setWorkshopUid(uid: String)
    suspend fun clear()
}
