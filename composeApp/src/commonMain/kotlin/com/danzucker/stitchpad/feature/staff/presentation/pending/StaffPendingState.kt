package com.danzucker.stitchpad.feature.staff.presentation.pending

data class StaffPendingState(
    /** The joined workshop's name for the identity chip; null on a cold start into pending. */
    val workshopName: String? = null,
    val isLeaving: Boolean = false,
)
