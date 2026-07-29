package com.danzucker.stitchpad.feature.staff.presentation.pending

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface StaffPendingEvent {
    /** Approved — the claim landed; enter the app. */
    data object NavigateToHome : StaffPendingEvent

    /** Left, or the owner declined the request — back to the join screen. */
    data object NavigateToRedeem : StaffPendingEvent

    data object SignedOut : StaffPendingEvent

    data class ShowMessage(val message: UiText) : StaffPendingEvent
}
