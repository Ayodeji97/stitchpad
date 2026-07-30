package com.danzucker.stitchpad.feature.staff.presentation.team

import com.danzucker.stitchpad.core.domain.staff.Membership

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

    data object OnErrorDismiss : TeamAction
}
