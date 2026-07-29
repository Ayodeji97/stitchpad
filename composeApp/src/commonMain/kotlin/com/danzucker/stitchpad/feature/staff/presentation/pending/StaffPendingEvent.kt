package com.danzucker.stitchpad.feature.staff.presentation.pending

sealed interface StaffPendingEvent {
    /** Approved — the claim landed; enter the app. */
    data object NavigateToHome : StaffPendingEvent

    /** Left, or the owner declined the request — back to the join screen. */
    data class NavigateToRedeem(val declined: Boolean = false) : StaffPendingEvent

    data object SignedOut : StaffPendingEvent
}
