package com.danzucker.stitchpad.feature.staff.presentation.redeem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.staff.StaffError
import com.danzucker.stitchpad.core.domain.staff.StaffMembershipPrefsStore
import com.danzucker.stitchpad.core.domain.staff.repository.InviteRedemptionRepository
import com.danzucker.stitchpad.feature.auth.domain.SignOutUseCase
import com.danzucker.stitchpad.feature.staff.presentation.toUiText
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the "Join a workshop" screen: normalises + validates the invite code,
 * redeems it, and on success persists the joined workshopUid so the session
 * provider enters the pending window before the approval claim lands.
 */
class RedeemInviteViewModel(
    private val inviteRedemptionRepository: InviteRedemptionRepository,
    private val staffMembershipPrefs: StaffMembershipPrefsStore,
    private val signOutUseCase: SignOutUseCase,
    pendingDeepLink: PendingDeepLinkHolder,
) : ViewModel() {

    private val _state = MutableStateFlow(RedeemInviteState())
    val state = _state.asStateFlow()

    private val _events = Channel<RedeemInviteEvent>()
    val events = _events.receiveAsFlow()

    init {
        // A JOIN_WORKSHOP deep link prefills the code (already normalised by the parser).
        val deepLinkCode = pendingDeepLink.consumeJoinWorkshopCode()
        if (deepLinkCode != null) {
            _state.update { it.copy(code = normalize(deepLinkCode), prefilled = true) }
        }
    }

    fun onAction(action: RedeemInviteAction) {
        when (action) {
            is RedeemInviteAction.OnCodeChange ->
                _state.update { it.copy(code = normalize(action.code), codeError = null) }

            RedeemInviteAction.OnJoinClick -> onJoin()
            RedeemInviteAction.OnBackClick ->
                viewModelScope.launch { _events.send(RedeemInviteEvent.NavigateBack) }
            RedeemInviteAction.OnSignOutClick -> onSignOut()
        }
    }

    private fun onJoin() {
        val code = _state.value.code
        if (code.length != INVITE_CODE_LENGTH) {
            _state.update { it.copy(codeError = StaffError.INVITE_NOT_FOUND.toUiText()) }
            return
        }
        _state.update { it.copy(isLoading = true, codeError = null) }
        viewModelScope.launch {
            when (val result = inviteRedemptionRepository.redeem(code)) {
                is Result.Success -> {
                    // Open the pending window: the provider watches this workshop's
                    // membership doc until the approval claim lands.
                    staffMembershipPrefs.setWorkshopUid(result.data.workshopUid)
                    _events.send(RedeemInviteEvent.NavigateToPending(result.data.workshopName))
                }

                is Result.Error -> {
                    _state.update { it.copy(isLoading = false) }
                    if (result.error.isCodeError()) {
                        _state.update { it.copy(codeError = result.error.toUiText()) }
                    } else {
                        _events.send(RedeemInviteEvent.ShowError(result.error.toUiText()))
                    }
                }
            }
        }
    }

    private fun onSignOut() {
        viewModelScope.launch {
            // Only navigate (and clear the pending-window uid) on a CONFIRMED sign-out —
            // otherwise we'd leave the app at Welcome while still authenticated.
            if (signOutUseCase() is Result.Success) {
                staffMembershipPrefs.clear()
                _events.send(RedeemInviteEvent.SignedOut)
            }
        }
    }

    /** Uppercase, drop separators/invalid chars, cap at the code length. */
    private fun normalize(raw: String): String =
        raw.uppercase().filter { it.isLetterOrDigit() }.take(INVITE_CODE_LENGTH)

    /** Errors about the code itself belong inline under the field; the rest are snackbars. */
    private fun StaffError.isCodeError(): Boolean = when (this) {
        StaffError.INVITE_NOT_FOUND,
        StaffError.INVITE_NOT_OPEN,
        StaffError.INVITE_EXPIRED,
        StaffError.ALREADY_MEMBER,
        StaffError.CANNOT_JOIN_OWN_WORKSHOP,
        StaffError.MEMBERSHIP_NOT_FOUND,
        StaffError.MEMBERSHIP_REVOKED,
        -> true

        StaffError.SEAT_CAP_REACHED,
        StaffError.NETWORK,
        StaffError.UNAUTHENTICATED,
        StaffError.UNKNOWN,
        -> false
    }
}
