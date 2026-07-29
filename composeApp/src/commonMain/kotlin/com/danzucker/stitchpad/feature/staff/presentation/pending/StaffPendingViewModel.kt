package com.danzucker.stitchpad.feature.staff.presentation.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.staff.StaffMembershipPrefsStore
import com.danzucker.stitchpad.core.domain.staff.repository.InviteRedemptionRepository
import com.danzucker.stitchpad.feature.auth.domain.SignOutUseCase
import com.danzucker.stitchpad.feature.staff.presentation.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val inviteRedemptionRepository: InviteRedemptionRepository,
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
                    // Clear the stored workshopUid so a later cold start doesn't re-enter the
                    // pending window for the declined workshop. Carry the declined flag to the
                    // redeem screen (a snackbar here would be lost when this screen is disposed
                    // by the navigation) so it can explain why.
                    sawPending && session.isOwner -> {
                        navigated = true
                        staffMembershipPrefs.clear()
                        _events.send(StaffPendingEvent.NavigateToRedeem(declined = true))
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
        val workshopUid = staffMembershipPrefs.workshopUid.value
        if (workshopUid == null) {
            // Nothing recorded to cancel server-side — just go back.
            navigated = true
            viewModelScope.launch { _events.send(StaffPendingEvent.NavigateToRedeem(declined = false)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLeaving = true) }
            // Cancel the membership server-side FIRST; only treat as "left" once the
            // server has revoked it, so the owner can't later approve a cancelled
            // request. On failure, stay put and surface the error.
            when (val result = inviteRedemptionRepository.cancelMembership(workshopUid)) {
                is Result.Success -> {
                    navigated = true // suppress the flow's decline path when prefs clear below.
                    staffMembershipPrefs.clear()
                    _events.send(StaffPendingEvent.NavigateToRedeem(declined = false))
                }
                is Result.Error -> {
                    _state.update { it.copy(isLeaving = false) }
                    _events.send(StaffPendingEvent.ShowError(result.error.toUiText()))
                }
            }
        }
    }

    private fun onSignOut() {
        viewModelScope.launch {
            // Only navigate (and clear the stored uid) on a CONFIRMED sign-out — otherwise
            // we'd leave the app at Welcome while still authenticated.
            if (signOutUseCase() is Result.Success) {
                navigated = true
                staffMembershipPrefs.clear()
                _events.send(StaffPendingEvent.SignedOut)
            }
        }
    }
}
