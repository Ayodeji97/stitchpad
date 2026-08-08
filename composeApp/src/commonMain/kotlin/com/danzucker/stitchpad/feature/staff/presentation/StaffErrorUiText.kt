package com.danzucker.stitchpad.feature.staff.presentation

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.staff.StaffError
import com.danzucker.stitchpad.core.presentation.UiText
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_no_internet
import stitchpad.composeapp.generated.resources.error_unknown
import stitchpad.composeapp.generated.resources.staff_error_already_member
import stitchpad.composeapp.generated.resources.staff_error_cannot_join_own
import stitchpad.composeapp.generated.resources.staff_error_generic
import stitchpad.composeapp.generated.resources.staff_error_invite_invalid
import stitchpad.composeapp.generated.resources.staff_error_network
import stitchpad.composeapp.generated.resources.staff_error_seat_cap

/** Maps a [StaffError] from the invite/redeem callables to a staff-facing message. */
fun StaffError.toUiText(): UiText = when (this) {
    // The three "bad code" outcomes read the same to a staffer: get a new code.
    StaffError.INVITE_NOT_FOUND,
    StaffError.INVITE_NOT_OPEN,
    StaffError.INVITE_EXPIRED,
    StaffError.MEMBERSHIP_NOT_FOUND,
    StaffError.MEMBERSHIP_REVOKED,
    -> UiText.StringResourceText(Res.string.staff_error_invite_invalid)

    StaffError.ALREADY_MEMBER -> UiText.StringResourceText(Res.string.staff_error_already_member)
    StaffError.CANNOT_JOIN_OWN_WORKSHOP -> UiText.StringResourceText(Res.string.staff_error_cannot_join_own)
    StaffError.SEAT_CAP_REACHED -> UiText.StringResourceText(Res.string.staff_error_seat_cap)
    StaffError.NETWORK -> UiText.StringResourceText(Res.string.staff_error_network)
    StaffError.UNAUTHENTICATED,
    StaffError.UNKNOWN,
    -> UiText.StringResourceText(Res.string.staff_error_generic)
}

/**
 * Maps a [DataError.Network] from [com.danzucker.stitchpad.core.domain.staff.repository.TeamRosterRepository]
 * calls to a user-facing message. Feature-specific `toXUiText()` per the codebase convention
 * (see CustomerErrorMapping/StyleErrorMapping) rather than a shared `DataError.Network.toUiText()`.
 */
fun DataError.Network.toTeamRosterUiText(): UiText = when (this) {
    DataError.Network.NO_INTERNET -> UiText.StringResourceText(Res.string.error_no_internet)
    else -> UiText.StringResourceText(Res.string.error_unknown)
}
