package com.danzucker.stitchpad.core.domain.staff

import kotlinx.coroutines.flow.StateFlow

/**
 * Persists the workshopUid a staff member joined (learned at redeem time) so the
 * session provider can watch their membership doc during the PENDING window —
 * before the approval claim lands on the ID token. Cleared on sign-out / leave.
 *
 * Exposed as a [StateFlow] (not just a one-shot getter) because redeeming an
 * invite persists the uid *without* any auth-token change — so the session
 * provider, which otherwise only re-resolves on `idTokenChanged`, must observe
 * this reactively to enter the pending window the moment the uid is saved
 * (rather than only after an app restart).
 *
 * Only needed for the pending window; once approved, workshopUid comes from the
 * custom claim, so a cleared/reinstalled store self-heals for an active member.
 */
interface StaffMembershipPrefsStore {
    /** Current stored workshopUid, plus an emission on every [setWorkshopUid]/[clear]. */
    val workshopUid: StateFlow<String?>
    suspend fun setWorkshopUid(uid: String)
    suspend fun clear()
}
