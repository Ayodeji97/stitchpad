package com.danzucker.stitchpad.core.data.staff

import com.danzucker.stitchpad.core.domain.staff.StaffError

/**
 * Maps a Cloud Functions error MESSAGE to a [StaffError]. The staff callables
 * throw HttpsError(code, '<marker>') where the marker is a stable string
 * (e.g. 'seat_cap_reached'). GitLive on iOS can drop the canonical HttpsError
 * code, so we recover intent from the message marker first and fall back to the
 * code only when no marker matches (see CloudFunctionsReferralRepository).
 *
 * Pure + internal so the marker mapping is unit-testable without GitLive types.
 */
internal object StaffErrorMapper {
    fun fromMessage(message: String?): StaffError? = when {
        message == null -> null
        message.contains("seat_cap_reached") -> StaffError.SEAT_CAP_REACHED
        message.contains("invite_not_found") -> StaffError.INVITE_NOT_FOUND
        message.contains("invite_not_open") -> StaffError.INVITE_NOT_OPEN
        message.contains("invite_expired") -> StaffError.INVITE_EXPIRED
        message.contains("already_member") -> StaffError.ALREADY_MEMBER
        message.contains("cannot_join_own_workshop") -> StaffError.CANNOT_JOIN_OWN_WORKSHOP
        message.contains("membership_not_found") -> StaffError.MEMBERSHIP_NOT_FOUND
        message.contains("membership_revoked") -> StaffError.MEMBERSHIP_REVOKED
        message.contains("auth_required") -> StaffError.UNAUTHENTICATED
        else -> null
    }
}
