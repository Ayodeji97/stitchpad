package com.danzucker.stitchpad.feature.referral.presentation.entry

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface ReferralCodeEvent {
    data object NavigateBack : ReferralCodeEvent
    data class ShowMessage(val message: UiText) : ReferralCodeEvent
}
