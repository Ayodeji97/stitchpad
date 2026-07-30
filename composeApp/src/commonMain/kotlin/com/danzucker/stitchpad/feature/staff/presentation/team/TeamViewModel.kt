package com.danzucker.stitchpad.feature.staff.presentation.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.error.onFailure
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.staff.StaffInvite
import com.danzucker.stitchpad.core.domain.staff.repository.StaffRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.staff.presentation.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.team_invite_copied
import kotlin.math.ceil
import kotlin.math.max
import kotlin.time.Clock

/**
 * Drives the owner's "Team" screen: mints invite codes, streams the workshop's
 * memberships (split into pending + active), and approves/declines/revokes
 * members. The memberships listener is the single source of truth for the list,
 * so approve/decline/revoke only surface errors — the list refreshes itself.
 */
class TeamViewModel(
    private val staffRepository: StaffRepository,
    private val authRepository: AuthRepository,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    private val _state = MutableStateFlow(TeamState())
    val state = _state.asStateFlow()

    private val _events = Channel<TeamEvent>()
    val events = _events.receiveAsFlow()

    init {
        observeTeam()
    }

    fun onAction(action: TeamAction) {
        when (action) {
            TeamAction.OnBackClick -> emit(TeamEvent.NavigateBack)
            TeamAction.OnInviteClick -> onInvite()
            TeamAction.OnDismissInviteSheet -> _state.update { it.copy(invite = null) }
            TeamAction.OnCopyCode -> onCopyCode()
            TeamAction.OnShareLink -> onShareLink()
            is TeamAction.OnApprove -> onApprove(action.staffAuthUid)
            is TeamAction.OnDecline -> onDecline(action.staffAuthUid)
            is TeamAction.OnRevokeClick -> _state.update { it.copy(revokeTarget = action.member) }
            TeamAction.OnConfirmRevoke -> onConfirmRevoke()
            TeamAction.OnDismissRevokeDialog -> _state.update { it.copy(revokeTarget = null) }
            TeamAction.OnErrorDismiss -> _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun observeTeam() {
        viewModelScope.launch {
            val ownerUid = authRepository.getCurrentUser()?.id
            if (ownerUid == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            staffRepository.observeMemberships(ownerUid).collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(
                            isLoading = false,
                            pending = result.data.filter { m -> m.status == MembershipStatus.PENDING },
                            active = result.data.filter { m -> m.status == MembershipStatus.ACTIVE },
                            errorMessage = null,
                        )
                    }

                    is Result.Error -> _state.update {
                        it.copy(isLoading = false, errorMessage = result.error.toUiText())
                    }
                }
            }
        }
    }

    private fun onInvite() {
        if (!_state.value.canInvite) return
        _state.update { it.copy(isGeneratingInvite = true) }
        viewModelScope.launch {
            when (val result = staffRepository.generateInvite()) {
                is Result.Success -> _state.update {
                    it.copy(
                        isGeneratingInvite = false,
                        invite = result.data.toUi(),
                    )
                }

                is Result.Error -> {
                    _state.update { it.copy(isGeneratingInvite = false) }
                    _events.send(TeamEvent.ShowSnackbar(result.error.toUiText()))
                }
            }
        }
    }

    private fun StaffInvite.toUi(): TeamInviteUi {
        val remainingMillis = expiresAt - nowMillis()
        val days = ceil(remainingMillis.toDouble() / MILLIS_PER_DAY).toInt()
        return TeamInviteUi(code = code, expiresInDays = max(0, days))
    }

    private fun onCopyCode() {
        val invite = _state.value.invite ?: return
        emit(TeamEvent.CopyToClipboard(invite.code))
        emit(TeamEvent.ShowSnackbar(UiText.StringResourceText(Res.string.team_invite_copied)))
    }

    private fun onShareLink() {
        val invite = _state.value.invite ?: return
        emit(TeamEvent.ShareViaWhatsApp("$JOIN_LINK_BASE${invite.code}"))
    }

    private fun onApprove(staffAuthUid: String) {
        viewModelScope.launch {
            staffRepository.approve(staffAuthUid).onFailure { error ->
                _events.send(TeamEvent.ShowSnackbar(error.toUiText()))
            }
        }
    }

    private fun onDecline(staffAuthUid: String) {
        viewModelScope.launch {
            staffRepository.revoke(staffAuthUid).onFailure { error ->
                _events.send(TeamEvent.ShowSnackbar(error.toUiText()))
            }
        }
    }

    private fun onConfirmRevoke() {
        val member = _state.value.revokeTarget ?: return
        _state.update { it.copy(revokeTarget = null) }
        viewModelScope.launch {
            staffRepository.revoke(member.staffAuthUid).onFailure { error ->
                _events.send(TeamEvent.ShowSnackbar(error.toUiText()))
            }
        }
    }

    private fun emit(event: TeamEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000.0
        const val JOIN_LINK_BASE = "https://link.getstitchpad.com/join?code="
    }
}
