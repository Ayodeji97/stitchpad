package com.danzucker.stitchpad.feature.staff.presentation.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.staff.StaffMembershipPrefsStore
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.SignOutUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.staff_pending_declined

/**
 * The staff "waiting for approval" screen. Passive: it watches the session and
 * flips the app to Home the instant the approval claim lands. Also handles the
 * owner declining (membership reverts to owner-of-self → back to the join screen)
 * and explicit leave / sign out.
 */
class StaffPendingViewModel(
    workshopName: String,
    activeWorkshopProvider: ActiveWorkshopProvider,
    private val staffMembershipPrefs: StaffMembershipPrefsStore,
    private val signOutUseCase: SignOutUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(StaffPendingState(workshopName = workshopName.ifBlank { null }))
    val state = _state.asStateFlow()

    private val _events = Channel<StaffPendingEvent>()
    val events = _events.receiveAsFlow()

    // One-shot navigation guard: once we route away, ignore further session churn
    // (e.g. our own prefs.clear() on leave flipping the session to owner-of-self).
    private var navigated = false
    private var sawPending = false

    init {
        viewModelScope.launch {
            activeWorkshopProvider.flow.collect { session ->
                if (navigated) return@collect
                when {
                    // Approved: the claim landed → enter the app.
                    session.isActiveStaff -> {
                        navigated = true
                        _events.send(StaffPendingEvent.NavigateToHome)
                    }
                    // Still waiting.
                    session.role == StaffRole.STAFF && session.membershipStatus == MembershipStatus.PENDING ->
                        sawPending = true
                    // Reverted to owner-of-self AFTER being pending = the owner declined/removed us.
                    sawPending && session.isOwner -> {
                        navigated = true
                        _events.send(
                            StaffPendingEvent.ShowMessage(UiText.StringResourceText(Res.string.staff_pending_declined)),
                        )
                        _events.send(StaffPendingEvent.NavigateToRedeem)
                    }
                }
            }
        }
    }

    fun onAction(action: StaffPendingAction) {
        when (action) {
            StaffPendingAction.OnLeaveClick -> onLeave()
            StaffPendingAction.OnSignOutClick -> onSignOut()
        }
    }

    private fun onLeave() {
        viewModelScope.launch {
            navigated = true // suppress the flow's decline path when prefs clear below.
            _state.update { it.copy(isLeaving = true) }
            staffMembershipPrefs.clear()
            _events.send(StaffPendingEvent.NavigateToRedeem)
        }
    }

    private fun onSignOut() {
        viewModelScope.launch {
            navigated = true
            staffMembershipPrefs.clear()
            signOutUseCase()
            _events.send(StaffPendingEvent.SignedOut)
        }
    }
}
