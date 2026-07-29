package com.danzucker.stitchpad.feature.staff.presentation.pending

sealed interface StaffPendingAction {
    data object OnLeaveClick : StaffPendingAction
    data object OnSignOutClick : StaffPendingAction
}
