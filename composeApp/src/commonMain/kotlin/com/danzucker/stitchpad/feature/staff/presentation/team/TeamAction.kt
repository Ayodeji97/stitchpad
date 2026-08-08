package com.danzucker.stitchpad.feature.staff.presentation.team

import com.danzucker.stitchpad.core.domain.staff.Membership
import com.danzucker.stitchpad.core.domain.staff.TeamMember

sealed interface TeamAction {
    data object OnBackClick : TeamAction
    data object OnInviteClick : TeamAction
    data object OnDismissInviteSheet : TeamAction
    data object OnCopyCode : TeamAction
    data object OnShareLink : TeamAction

    data class OnApprove(val staffAuthUid: String) : TeamAction
    data class OnDecline(val staffAuthUid: String) : TeamAction

    data class OnRevokeClick(val member: Membership) : TeamAction
    data object OnConfirmRevoke : TeamAction
    data object OnDismissRevokeDialog : TeamAction

    // Roster (name-only members) — add/rename/archive.
    data object OnAddMemberClick : TeamAction
    data class OnAddMemberNameChange(val name: String) : TeamAction
    data object OnConfirmAddMember : TeamAction

    /** Also dismisses the rename sheet — both are "close the roster form" actions. */
    data object OnDismissAddMember : TeamAction
    data class OnRenameMember(val member: TeamMember) : TeamAction
    data class OnConfirmRename(val name: String) : TeamAction
    data class OnArchiveMember(val member: TeamMember) : TeamAction

    data object OnErrorDismiss : TeamAction
}
