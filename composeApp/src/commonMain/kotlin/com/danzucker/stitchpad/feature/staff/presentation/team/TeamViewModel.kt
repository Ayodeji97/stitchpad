package com.danzucker.stitchpad.feature.staff.presentation.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.error.onFailure
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.staff.StaffInvite
import com.danzucker.stitchpad.core.domain.staff.TeamMember
import com.danzucker.stitchpad.core.domain.staff.TeamMemberKind
import com.danzucker.stitchpad.core.domain.staff.repository.StaffRepository
import com.danzucker.stitchpad.core.domain.staff.repository.TeamRosterRepository
import com.danzucker.stitchpad.core.domain.staff.resolveClaimDisplayName
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.order.presentation.list.OrderListFilter
import com.danzucker.stitchpad.feature.staff.presentation.toTeamRosterUiText
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
@Suppress("TooManyFunctions")
class TeamViewModel(
    private val staffRepository: StaffRepository,
    private val teamRosterRepository: TeamRosterRepository,
    private val authRepository: AuthRepository,
    private val orderRepository: OrderRepository,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    private val _state = MutableStateFlow(TeamState())
    val state = _state.asStateFlow()

    private val _events = Channel<TeamEvent>()
    val events = _events.receiveAsFlow()

    /**
     * Guards [ensureOwnerInRosterIfMissing] to at most one write per VM lifetime: the
     * roster listener can re-emit many times (e.g. offline cache reads, then the network
     * result), and without this a still-missing owner row on a later emission would
     * re-fire `ensureOwnerMember` on every emission instead of once.
     */
    private var ownerEnsureAttempted = false

    init {
        observeTeam()
        observeRoster()
        observeWorkload()
    }

    @Suppress("CyclomaticComplexMethod")
    fun onAction(action: TeamAction) {
        when (action) {
            TeamAction.OnBackClick -> emit(TeamEvent.NavigateBack)
            TeamAction.OnInviteClick -> onInvite()
            TeamAction.OnDismissInviteSheet -> _state.update { it.copy(invite = null) }
            TeamAction.OnCopyCode -> onCopyCode()
            TeamAction.OnShareLink -> onShareLink()
            is TeamAction.OnApprove -> onDecision(action.staffAuthUid, TeamDecision.APPROVE)
            is TeamAction.OnDecline -> onDecision(action.staffAuthUid, TeamDecision.DECLINE)
            is TeamAction.OnRevokeClick -> _state.update { it.copy(revokeTarget = action.member) }
            TeamAction.OnConfirmRevoke -> onConfirmRevoke()
            TeamAction.OnDismissRevokeDialog -> _state.update { it.copy(revokeTarget = null) }
            TeamAction.OnAddMemberClick -> _state.update { it.copy(showAddMemberSheet = true) }
            is TeamAction.OnConfirmAddMember -> onConfirmAddMember(action.name)
            TeamAction.OnDismissAddMember -> _state.update {
                it.copy(showAddMemberSheet = false, renameTarget = null)
            }
            is TeamAction.OnRenameMember -> _state.update { it.copy(renameTarget = action.member) }
            is TeamAction.OnConfirmRename -> onConfirmRename(action.name)
            is TeamAction.OnArchiveMember -> onArchiveMember(action.member)
            is TeamAction.OnMemberOrdersClick -> onMemberOrdersClick(action.memberId)
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

    /**
     * The roster listener (STAFF + NAMED rows) is independent of [observeTeam]'s
     * memberships listener — it's a different collection and the only source of
     * truth for name-only placeholders, which have no [Membership] at all.
     */
    private fun observeRoster() {
        viewModelScope.launch {
            val workshopUid = authRepository.getCurrentUser()?.id ?: return@launch
            teamRosterRepository.observeTeam(workshopUid).collect { result ->
                when (result) {
                    // Unlike observeTeam's Success branch, this does NOT clear errorMessage:
                    // it's a second, independent listener sharing one field, and the screen
                    // already auto-dismisses a shown error (see TeamRoot's LaunchedEffect) —
                    // clearing here too would risk this listener's success racing ahead of
                    // the membership listener's error and swallowing it before it's shown.
                    is Result.Success -> {
                        _state.update { it.copy(roster = result.data, currentUserId = workshopUid) }
                        ensureOwnerInRosterIfMissing(workshopUid, result.data)
                    }
                    is Result.Error -> _state.update { it.copy(errorMessage = result.error.toTeamRosterUiText()) }
                }
            }
        }
    }

    /**
     * Lazily writes the owner's [TeamMemberKind.OWNER] roster row the first time a roster
     * emission comes back without one — the owner has no server-side writer for their own
     * row (unlike staff, whose row is written by `approveStaffMember`), so this is how the
     * owner becomes assignable like any other roster member. Runs at most once per VM
     * lifetime ([ownerEnsureAttempted]): a later emission that still lacks the row (e.g.
     * the write hasn't landed yet) must not re-fire it.
     */
    private fun ensureOwnerInRosterIfMissing(workshopUid: String, roster: List<TeamMember>) {
        if (ownerEnsureAttempted) return
        if (roster.any { it.id == workshopUid }) return
        ownerEnsureAttempted = true
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            val ownerName = resolveClaimDisplayName(user?.displayName, user?.email, fallback = workshopUid)
            teamRosterRepository.ensureOwnerMember(workshopUid, ownerName).onFailure { error ->
                _events.send(TeamEvent.ShowSnackbar(error.toTeamRosterUiText()))
            }
        }
    }

    /**
     * Streams open-order counts per assignee (Task 9), client-side over the same orders
     * stream the Orders list itself observes — no new Firestore query. Independent of
     * [observeRoster]'s listener: a failure here must NOT clobber the shared
     * `errorMessage` (mirrors [observeRoster]'s comment) — the roster stays usable even
     * if the workload counts fail to load.
     */
    private fun observeWorkload() {
        viewModelScope.launch {
            val ownerUid = authRepository.getCurrentUser()?.id ?: return@launch
            orderRepository.observeOrders(ownerUid).collect { result ->
                if (result is Result.Success) {
                    _state.update { it.copy(workloadCounts = openOrderCountsByAssignee(result.data)) }
                }
                // Result.Error: ignore — see kdoc above.
            }
        }
    }

    private fun onMemberOrdersClick(memberId: String?) {
        val initialFilter = memberId?.let(OrderListFilter::assignee) ?: OrderListFilter.ASSIGNEE_UNASSIGNED
        emit(TeamEvent.NavigateToMemberOrders(initialFilter))
    }

    private fun onConfirmAddMember(rawName: String) {
        val name = rawName.trim()
        if (name.isBlank()) return
        _state.update { it.copy(showAddMemberSheet = false) }
        viewModelScope.launch {
            val workshopUid = authRepository.getCurrentUser()?.id ?: return@launch
            teamRosterRepository.addNamedMember(workshopUid, name).onFailure { error ->
                _events.send(TeamEvent.ShowSnackbar(error.toTeamRosterUiText()))
            }
        }
    }

    private fun onConfirmRename(rawName: String) {
        val target = _state.value.renameTarget ?: return
        _state.update { it.copy(renameTarget = null) }
        val name = rawName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            val workshopUid = authRepository.getCurrentUser()?.id ?: return@launch
            teamRosterRepository.renameMember(workshopUid, target.id, name).onFailure { error ->
                _events.send(TeamEvent.ShowSnackbar(error.toTeamRosterUiText()))
            }
        }
    }

    private fun onArchiveMember(member: TeamMember) {
        viewModelScope.launch {
            val workshopUid = authRepository.getCurrentUser()?.id ?: return@launch
            teamRosterRepository.archiveMember(workshopUid, member.id).onFailure { error ->
                _events.send(TeamEvent.ShowSnackbar(error.toTeamRosterUiText()))
            }
        }
    }

    private fun onInvite() {
        // Guard re-entrancy: a second tap while a request is in flight would mint a
        // second invite code (codex). Covers both the empty-state and populated CTAs.
        if (!_state.value.canInvite || _state.value.isGeneratingInvite) return
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
        emit(TeamEvent.ShareInviteLink("$JOIN_LINK_BASE${invite.code}"))
    }

    /**
     * Resolves one pending request, marking it in flight so the card's buttons can
     * show progress and stop accepting taps.
     *
     * Guard re-entrancy the same way [onInvite] does: the call is a Firestore
     * round-trip with no local echo, so an impatient owner tapping twice would
     * otherwise fire a duplicate approve/revoke.
     */
    private fun onDecision(staffAuthUid: String, decision: TeamDecision) {
        if (_state.value.inFlightDecisions.containsKey(staffAuthUid)) return
        _state.update { it.copy(inFlightDecisions = it.inFlightDecisions + (staffAuthUid to decision)) }
        viewModelScope.launch {
            val result = when (decision) {
                TeamDecision.APPROVE -> staffRepository.approve(staffAuthUid)
                TeamDecision.DECLINE -> staffRepository.revoke(staffAuthUid)
            }
            // Clear BEFORE emitting: _events is a rendezvous channel, so the send
            // parks until the screen collects it. Emitting first would leave the
            // buttons spinning indefinitely whenever nothing is listening.
            // On success the memberships listener drops the row anyway; clearing
            // unconditionally is what makes a FAILED decision tappable again.
            _state.update { it.copy(inFlightDecisions = it.inFlightDecisions - staffAuthUid) }
            result.onFailure { error -> _events.send(TeamEvent.ShowSnackbar(error.toUiText())) }
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
