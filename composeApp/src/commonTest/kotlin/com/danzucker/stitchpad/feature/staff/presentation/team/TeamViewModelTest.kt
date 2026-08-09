package com.danzucker.stitchpad.feature.staff.presentation.team

import app.cash.turbine.test
import com.danzucker.stitchpad.core.data.repository.FakeTeamRosterRepository
import com.danzucker.stitchpad.core.data.staff.FakeStaffRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.staff.Membership
import com.danzucker.stitchpad.core.domain.staff.StaffError
import com.danzucker.stitchpad.core.domain.staff.StaffInvite
import com.danzucker.stitchpad.core.domain.staff.TeamMember
import com.danzucker.stitchpad.core.domain.staff.TeamMemberKind
import com.danzucker.stitchpad.core.domain.staff.TeamMemberStatus
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TeamViewModelTest {

    private lateinit var staffRepo: FakeStaffRepository
    private lateinit var rosterRepo: FakeTeamRosterRepository
    private lateinit var authRepo: FakeAuthRepository

    private val fixedNow = 1_700_000_000_000L
    private val oneDayMillis = 86_400_000L

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        staffRepo = FakeStaffRepository()
        rosterRepo = FakeTeamRosterRepository()
        authRepo = FakeAuthRepository()
        authRepo.currentUser = User(
            id = "owner-1",
            email = "owner@example.com",
            displayName = "Owner",
            businessName = "Ade Fashions",
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun buildViewModel() = TeamViewModel(
        staffRepository = staffRepo,
        teamRosterRepository = rosterRepo,
        authRepository = authRepo,
        nowMillis = { fixedNow },
    )

    private fun member(uid: String, status: MembershipStatus) = Membership(
        staffAuthUid = uid,
        staffEmail = "$uid@example.com",
        staffName = uid.replaceFirstChar { it.uppercase() },
        status = status,
    )

    private fun rosterMember(
        id: String,
        name: String = id,
        kind: TeamMemberKind = TeamMemberKind.NAMED,
        status: TeamMemberStatus = TeamMemberStatus.ACTIVE,
    ) = TeamMember(id = id, name = name, kind = kind, colorSeed = 0, status = status)

    @Test
    fun membershipsSplitIntoPendingAndActiveAndDropRevoked() {
        staffRepo.memberships.value = Result.Success(
            listOf(
                member("pending", MembershipStatus.PENDING),
                member("active", MembershipStatus.ACTIVE),
                member("gone", MembershipStatus.REVOKED),
            ),
        )
        val vm = buildViewModel()
        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf("pending"), state.pending.map { it.staffAuthUid })
        assertEquals(listOf("active"), state.active.map { it.staffAuthUid })
        assertEquals("owner-1", staffRepo.lastObservedOwnerUid)
    }

    @Test
    fun seatsUsedCountsPendingPlusActiveAndCanInviteFalseAtCap() {
        staffRepo.memberships.value = Result.Success(
            listOf(
                member("a1", MembershipStatus.ACTIVE),
                member("a2", MembershipStatus.ACTIVE),
            ),
        )
        val vm = buildViewModel()
        assertEquals(2, vm.state.value.seatsUsed)
        assertFalse(vm.state.value.canInvite)
    }

    @Test
    fun canInviteTrueWhenBelowCap() {
        staffRepo.memberships.value = Result.Success(listOf(member("a1", MembershipStatus.ACTIVE)))
        val vm = buildViewModel()
        assertEquals(1, vm.state.value.seatsUsed)
        assertTrue(vm.state.value.canInvite)
    }

    @Test
    fun onInviteClickSuccessSetsInviteWithExpiresInDaysAndDisplayCode() {
        staffRepo.inviteResult = Result.Success(
            StaffInvite(code = "7Q4P9RM2", expiresAt = fixedNow + 7 * oneDayMillis),
        )
        val vm = buildViewModel()
        vm.onAction(TeamAction.OnInviteClick)
        val invite = vm.state.value.invite
        assertNotNull(invite)
        assertEquals("7Q4P9RM2", invite.code)
        assertEquals(7, invite.expiresInDays)
        assertEquals("7Q4P-9RM2", invite.displayCode)
        assertFalse(vm.state.value.isGeneratingInvite)
    }

    @Test
    fun onInviteClickErrorEmitsSnackbarAndLeavesInviteNull() = runTest {
        staffRepo.inviteResult = Result.Error(StaffError.NETWORK)
        val vm = buildViewModel()
        vm.events.test {
            vm.onAction(TeamAction.OnInviteClick)
            assertIs<TeamEvent.ShowSnackbar>(awaitItem())
        }
        assertNull(vm.state.value.invite)
        assertFalse(vm.state.value.isGeneratingInvite)
    }

    @Test
    fun onInviteClickIsNoOpWhenSeatsFull() {
        staffRepo.memberships.value = Result.Success(
            listOf(
                member("a1", MembershipStatus.ACTIVE),
                member("a2", MembershipStatus.ACTIVE),
            ),
        )
        val vm = buildViewModel()
        vm.onAction(TeamAction.OnInviteClick)
        assertEquals(0, staffRepo.generateInviteCount)
    }

    @Test
    fun onApproveCallsRepoApproveWithUid() {
        val vm = buildViewModel()
        vm.onAction(TeamAction.OnApprove("staff-9"))
        assertEquals("staff-9", staffRepo.lastApprovedUid)
    }

    @Test
    fun onDeclineCallsRepoRevokeWithUid() {
        val vm = buildViewModel()
        vm.onAction(TeamAction.OnDecline("staff-7"))
        assertEquals("staff-7", staffRepo.lastRevokedUid)
    }

    @Test
    fun approveMarksTheRequestInFlightUntilTheCallReturns() = runTest {
        val gate = CompletableDeferred<Unit>()
        staffRepo.decisionGate = gate
        val vm = buildViewModel()

        vm.onAction(TeamAction.OnApprove("staff-9"))
        assertEquals(TeamDecision.APPROVE, vm.state.value.inFlightDecisions["staff-9"])

        gate.complete(Unit)
        assertNull(vm.state.value.inFlightDecisions["staff-9"])
    }

    @Test
    fun declineMarksTheRequestInFlightUntilTheCallReturns() = runTest {
        val gate = CompletableDeferred<Unit>()
        staffRepo.decisionGate = gate
        val vm = buildViewModel()

        vm.onAction(TeamAction.OnDecline("staff-7"))
        assertEquals(TeamDecision.DECLINE, vm.state.value.inFlightDecisions["staff-7"])

        gate.complete(Unit)
        assertNull(vm.state.value.inFlightDecisions["staff-7"])
    }

    @Test
    fun repeatedTapsWhileApprovingDoNotFireDuplicateRequests() = runTest {
        staffRepo.decisionGate = CompletableDeferred()
        val vm = buildViewModel()

        vm.onAction(TeamAction.OnApprove("staff-9"))
        vm.onAction(TeamAction.OnApprove("staff-9"))
        vm.onAction(TeamAction.OnApprove("staff-9"))

        assertEquals(1, staffRepo.approveCount)
    }

    @Test
    fun decliningWhileApproveIsInFlightIsIgnored() = runTest {
        staffRepo.decisionGate = CompletableDeferred()
        val vm = buildViewModel()

        vm.onAction(TeamAction.OnApprove("staff-9"))
        vm.onAction(TeamAction.OnDecline("staff-9"))

        assertEquals(1, staffRepo.approveCount)
        assertEquals(0, staffRepo.revokeCount)
        assertEquals(TeamDecision.APPROVE, vm.state.value.inFlightDecisions["staff-9"])
    }

    @Test
    fun decisionsOnDifferentMembersRunIndependently() = runTest {
        staffRepo.decisionGate = CompletableDeferred()
        val vm = buildViewModel()

        vm.onAction(TeamAction.OnApprove("staff-1"))
        vm.onAction(TeamAction.OnDecline("staff-2"))

        assertEquals(TeamDecision.APPROVE, vm.state.value.inFlightDecisions["staff-1"])
        assertEquals(TeamDecision.DECLINE, vm.state.value.inFlightDecisions["staff-2"])
    }

    @Test
    fun aFailedApproveClearsInFlightSoTheOwnerCanRetry() = runTest {
        val gate = CompletableDeferred<Unit>()
        staffRepo.decisionGate = gate
        staffRepo.approveResult = Result.Error(StaffError.NETWORK)
        val vm = buildViewModel()

        vm.onAction(TeamAction.OnApprove("staff-9"))
        gate.complete(Unit)
        assertNull(vm.state.value.inFlightDecisions["staff-9"])

        staffRepo.decisionGate = null
        vm.onAction(TeamAction.OnApprove("staff-9"))
        assertEquals(2, staffRepo.approveCount)
    }

    @Test
    fun onRevokeClickArmsDialogAndConfirmRevokeCallsRevokeAndClearsTarget() {
        val target = member("staff-3", MembershipStatus.ACTIVE)
        val vm = buildViewModel()
        vm.onAction(TeamAction.OnRevokeClick(target))
        assertEquals(target, vm.state.value.revokeTarget)

        vm.onAction(TeamAction.OnConfirmRevoke)
        assertEquals("staff-3", staffRepo.lastRevokedUid)
        assertNull(vm.state.value.revokeTarget)
    }

    @Test
    fun onDismissRevokeDialogClearsTargetWithoutRevoking() {
        val target = member("staff-4", MembershipStatus.ACTIVE)
        val vm = buildViewModel()
        vm.onAction(TeamAction.OnRevokeClick(target))
        vm.onAction(TeamAction.OnDismissRevokeDialog)
        assertNull(vm.state.value.revokeTarget)
        assertNull(staffRepo.lastRevokedUid)
    }

    @Test
    fun membershipsErrorSetsErrorMessageAndStopsLoading() {
        staffRepo.memberships.value = Result.Error(StaffError.NETWORK)
        val vm = buildViewModel()
        assertFalse(vm.state.value.isLoading)
        assertNotNull(vm.state.value.errorMessage)
    }

    @Test
    fun noOwnerUidStopsLoadingWithoutObserving() {
        authRepo.currentUser = null
        val vm = buildViewModel()
        assertFalse(vm.state.value.isLoading)
        assertNull(staffRepo.lastObservedOwnerUid)
    }

    // ---- Roster (name-only members) ----

    @Test
    fun rosterFlowPopulatesStateRoster() {
        rosterRepo.seedMembers(
            listOf(
                // Owner row seeded so this test isn't also exercising the lazy-ensure
                // path (covered separately below) — see ensureOwnerMemberCallCount tests.
                rosterMember("owner-1", "Owner", kind = TeamMemberKind.OWNER),
                rosterMember("staff-1", "Gabby Okoro", kind = TeamMemberKind.STAFF),
                rosterMember("named-1", "Ngozi Eze"),
            ),
        )
        val vm = buildViewModel()
        assertEquals(setOf("owner-1", "staff-1", "named-1"), vm.state.value.roster.map { it.id }.toSet())
    }

    @Test
    fun activeRosterExcludesArchivedRows() {
        rosterRepo.seedMembers(
            listOf(
                // Owner row seeded so this test isn't also exercising the lazy-ensure path.
                rosterMember("owner-1", "Owner", kind = TeamMemberKind.OWNER),
                rosterMember("named-1", "Ngozi Eze"),
                rosterMember("named-2", "Tayo Ade", status = TeamMemberStatus.ARCHIVED),
            ),
        )
        val vm = buildViewModel()
        assertEquals(listOf("owner-1", "named-1"), vm.state.value.activeRoster.map { it.id })
    }

    @Test
    fun onAddMemberClickOpensSheetAndClearsPreviousDraft() {
        val vm = buildViewModel()
        vm.onAction(TeamAction.OnAddMemberNameChange("stale draft"))
        vm.onAction(TeamAction.OnAddMemberClick)
        assertTrue(vm.state.value.showAddMemberSheet)
        assertEquals("", vm.state.value.addMemberName)
    }

    @Test
    fun onAddMemberNameChangeUpdatesState() {
        val vm = buildViewModel()
        vm.onAction(TeamAction.OnAddMemberNameChange("Ngozi"))
        assertEquals("Ngozi", vm.state.value.addMemberName)
    }

    @Test
    fun onConfirmAddMemberWithBlankNameIsNoOp() {
        val vm = buildViewModel()
        vm.onAction(TeamAction.OnAddMemberClick)
        vm.onAction(TeamAction.OnAddMemberNameChange("   "))
        vm.onAction(TeamAction.OnConfirmAddMember)
        assertNull(rosterRepo.lastAddedName)
        assertTrue(vm.state.value.showAddMemberSheet)
    }

    @Test
    fun onConfirmAddMemberWithRealNameCallsAddNamedMemberAndClosesSheet() {
        val vm = buildViewModel()
        vm.onAction(TeamAction.OnAddMemberClick)
        vm.onAction(TeamAction.OnAddMemberNameChange("Ngozi Eze"))
        vm.onAction(TeamAction.OnConfirmAddMember)
        assertEquals("Ngozi Eze", rosterRepo.lastAddedName)
        assertFalse(vm.state.value.showAddMemberSheet)
        assertEquals("", vm.state.value.addMemberName)
    }

    @Test
    fun onConfirmAddMemberErrorEmitsSnackbar() = runTest {
        // Owner row seeded so buildViewModel() doesn't also fire the lazy-ensure path
        // (which shares operationError below and would otherwise queue its own
        // ShowSnackbar, leaving this test's events channel with an unconsumed extra item).
        rosterRepo.seedMembers(listOf(rosterMember("owner-1", "Owner", kind = TeamMemberKind.OWNER)))
        rosterRepo.operationError = DataError.Network.UNKNOWN
        val vm = buildViewModel()
        vm.onAction(TeamAction.OnAddMemberNameChange("Ngozi Eze"))
        vm.events.test {
            vm.onAction(TeamAction.OnConfirmAddMember)
            assertIs<TeamEvent.ShowSnackbar>(awaitItem())
        }
    }

    @Test
    fun onRenameMemberArmsRenameTargetAndOnDismissAddMemberClearsIt() {
        val target = rosterMember("named-1", "Ngozi Eze")
        val vm = buildViewModel()
        vm.onAction(TeamAction.OnRenameMember(target))
        assertEquals(target, vm.state.value.renameTarget)

        vm.onAction(TeamAction.OnDismissAddMember)
        assertNull(vm.state.value.renameTarget)
    }

    @Test
    fun onConfirmRenameCallsRenameMemberAndClearsTarget() {
        val target = rosterMember("named-1", "Ngozi Eze")
        val vm = buildViewModel()
        vm.onAction(TeamAction.OnRenameMember(target))
        vm.onAction(TeamAction.OnConfirmRename("Ngozi A. Eze"))
        assertEquals("named-1", rosterRepo.lastRenamedMemberId)
        assertEquals("Ngozi A. Eze", rosterRepo.lastRenamedName)
        assertNull(vm.state.value.renameTarget)
    }

    @Test
    fun onConfirmRenameWithBlankNameIsNoOpButStillClearsTarget() {
        val target = rosterMember("named-1", "Ngozi Eze")
        val vm = buildViewModel()
        vm.onAction(TeamAction.OnRenameMember(target))
        vm.onAction(TeamAction.OnConfirmRename("   "))
        assertNull(rosterRepo.lastRenamedMemberId)
        assertNull(vm.state.value.renameTarget)
    }

    @Test
    fun onArchiveMemberCallsArchiveMember() {
        val target = rosterMember("named-1", "Ngozi Eze")
        val vm = buildViewModel()
        vm.onAction(TeamAction.OnArchiveMember(target))
        assertEquals("named-1", rosterRepo.lastArchivedMemberId)
    }

    @Test
    fun rosterErrorSurfacesErrorMessage() {
        rosterRepo.observeError = DataError.Network.UNKNOWN
        val vm = buildViewModel()
        assertNotNull(vm.state.value.errorMessage)
    }

    // ---- Owner lazy ensure ----

    @Test
    fun ownerMissingFromRosterEmissionTriggersEnsureOwnerMemberOnce() {
        rosterRepo.seedMembers(listOf(rosterMember("named-1", "Ngozi Eze")))
        buildViewModel()

        assertEquals(1, rosterRepo.ensureOwnerMemberCallCount)
        assertEquals("owner-1", rosterRepo.lastEnsuredOwnerUid)
        assertEquals("Owner", rosterRepo.lastEnsuredOwnerName)
    }

    @Test
    fun ownerAlreadyInRosterNeverTriggersEnsureOwnerMember() {
        rosterRepo.seedMembers(
            listOf(rosterMember("owner-1", "Owner", kind = TeamMemberKind.OWNER)),
        )
        buildViewModel()

        assertEquals(0, rosterRepo.ensureOwnerMemberCallCount)
    }

    @Test
    fun secondRosterEmissionStillMissingOwnerDoesNotCallEnsureOwnerMemberAgain() {
        rosterRepo.seedMembers(listOf(rosterMember("named-1", "Ngozi Eze")))
        buildViewModel()
        assertEquals(1, rosterRepo.ensureOwnerMemberCallCount)

        // A second emission (still without the owner row, e.g. the write hasn't landed
        // yet) must not fire a second ensure call.
        rosterRepo.seedMembers(listOf(rosterMember("named-1", "Ngozi Eze"), rosterMember("named-2", "Tayo")))
        assertEquals(1, rosterRepo.ensureOwnerMemberCallCount)
    }

    @Test
    fun ensureOwnerMemberFallsBackToEmailWhenDisplayNameBlank() {
        authRepo.currentUser = User(
            id = "owner-1",
            email = "owner@example.com",
            displayName = "  ",
            businessName = "Ade Fashions",
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
        )
        rosterRepo.seedMembers(emptyList())
        buildViewModel()

        assertEquals("owner@example.com", rosterRepo.lastEnsuredOwnerName)
    }

    @Test
    fun ensureOwnerMemberFailureEmitsSnackbar() = runTest {
        rosterRepo.seedMembers(emptyList())
        rosterRepo.operationError = DataError.Network.UNKNOWN
        val vm = buildViewModel()

        vm.events.test {
            assertIs<TeamEvent.ShowSnackbar>(awaitItem())
        }
    }
}
