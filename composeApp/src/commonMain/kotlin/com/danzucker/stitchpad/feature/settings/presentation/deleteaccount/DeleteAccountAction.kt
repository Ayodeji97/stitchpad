package com.danzucker.stitchpad.feature.settings.presentation.deleteaccount

import com.danzucker.stitchpad.feature.settings.domain.DeletionReason

sealed interface DeleteAccountAction {
    data object OnConfirmContinue : DeleteAccountAction
    data object OnConfirmCancel : DeleteAccountAction

    data class OnReasonSelect(val reason: DeletionReason) : DeleteAccountAction
    data class OnAdditionalNotesChange(val value: String) : DeleteAccountAction
    data object OnReasonContinue : DeleteAccountAction
    data object OnReasonCancel : DeleteAccountAction

    data class OnReauthPasswordChange(val value: String) : DeleteAccountAction
    data object OnReauthConfirm : DeleteAccountAction
    data object OnReauthCancel : DeleteAccountAction
    data object OnForgotPassword : DeleteAccountAction

    data object OnGoodbyeContinue : DeleteAccountAction
    data object OnBackClick : DeleteAccountAction
}
