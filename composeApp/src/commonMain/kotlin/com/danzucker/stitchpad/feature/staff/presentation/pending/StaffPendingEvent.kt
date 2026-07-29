package com.danzucker.stitchpad.feature.staff.presentation.pending

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface StaffPendingEvent {
    /** Approved — the claim landed; enter the app. */
    data object NavigateToHome : StaffPendingEvent

    /** Left, or the owner declined the request — back to the join screen. */
    data class NavigateToRedeem(val declined: Boolean = false) : StaffPendingEvent

    data object SignedOut : StaffPendingEvent

    /** A leave attempt failed — shown as a snackbar (the screen stays put). */
    data class ShowError(val message: UiText) : StaffPendingEvent
}
