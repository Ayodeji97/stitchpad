@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.staff.presentation.team

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.staff.Membership
import com.danzucker.stitchpad.core.domain.staff.TeamMember
import com.danzucker.stitchpad.core.domain.staff.TeamMemberKind
import com.danzucker.stitchpad.core.domain.staff.TeamMemberStatus
import com.danzucker.stitchpad.core.domain.staff.rosterDisplayName
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.buildWhatsAppUrl
import com.danzucker.stitchpad.ui.components.MemberAvatar
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.components.StitchPadButtonVariant
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_assign_you
import stitchpad.composeapp.generated.resources.team_active_header_count
import stitchpad.composeapp.generated.resources.team_add_member
import stitchpad.composeapp.generated.resources.team_add_member_sheet_title
import stitchpad.composeapp.generated.resources.team_approve
import stitchpad.composeapp.generated.resources.team_archive
import stitchpad.composeapp.generated.resources.team_back_cd
import stitchpad.composeapp.generated.resources.team_decline
import stitchpad.composeapp.generated.resources.team_empty_body
import stitchpad.composeapp.generated.resources.team_empty_title
import stitchpad.composeapp.generated.resources.team_invite_copy_code
import stitchpad.composeapp.generated.resources.team_invite_cta
import stitchpad.composeapp.generated.resources.team_invite_expires_days
import stitchpad.composeapp.generated.resources.team_invite_expires_tomorrow
import stitchpad.composeapp.generated.resources.team_invite_first_cta
import stitchpad.composeapp.generated.resources.team_invite_share_link
import stitchpad.composeapp.generated.resources.team_invite_sheet_sub
import stitchpad.composeapp.generated.resources.team_invite_sheet_title
import stitchpad.composeapp.generated.resources.team_invite_whatsapp_message
import stitchpad.composeapp.generated.resources.team_member_linked
import stitchpad.composeapp.generated.resources.team_member_menu_cd
import stitchpad.composeapp.generated.resources.team_member_name_label
import stitchpad.composeapp.generated.resources.team_member_name_placeholder
import stitchpad.composeapp.generated.resources.team_pending_header
import stitchpad.composeapp.generated.resources.team_rename
import stitchpad.composeapp.generated.resources.team_rename_sheet_title
import stitchpad.composeapp.generated.resources.team_revoke
import stitchpad.composeapp.generated.resources.team_revoke_dialog_body
import stitchpad.composeapp.generated.resources.team_revoke_dialog_cancel
import stitchpad.composeapp.generated.resources.team_revoke_dialog_confirm
import stitchpad.composeapp.generated.resources.team_revoke_dialog_title
import stitchpad.composeapp.generated.resources.team_role_staff
import stitchpad.composeapp.generated.resources.team_roster_header
import stitchpad.composeapp.generated.resources.team_seats_full_hint
import stitchpad.composeapp.generated.resources.team_seats_full_note
import stitchpad.composeapp.generated.resources.team_seats_label
import stitchpad.composeapp.generated.resources.team_seats_note
import stitchpad.composeapp.generated.resources.team_seats_plan_hint
import stitchpad.composeapp.generated.resources.team_seats_used
import stitchpad.composeapp.generated.resources.team_title

@Composable
fun TeamRoot(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TeamViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            TeamEvent.NavigateBack -> onNavigateBack()
            is TeamEvent.CopyToClipboard -> clipboard.setText(AnnotatedString(event.text))
            is TeamEvent.ShareViaWhatsApp -> scope.launch {
                val message = getString(Res.string.team_invite_whatsapp_message, event.url)
                // Empty phone opens WhatsApp's share picker (same path as the gift/invite rows).
                uriHandler.openUri(buildWhatsAppUrl("", message))
            }
            is TeamEvent.ShowSnackbar -> scope.launch {
                snackbarHostState.showSnackbar(event.text.resolve())
            }
        }
    }

    // Memberships-listener failures land in state; surface once, then clear.
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it.resolve())
            viewModel.onAction(TeamAction.OnErrorDismiss)
        }
    }

    TeamScreen(
        state = state,
        onAction = viewModel::onAction,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    state: TeamState,
    onAction: (TeamAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.team_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(TeamAction.OnBackClick) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.team_back_cd),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.space5),
        ) {
            if (state.isEmpty) {
                EmptyTeam(
                    seatCap = state.seatCap,
                    isGeneratingInvite = state.isGeneratingInvite,
                    onAction = onAction,
                )
            } else {
                SeatCard(state = state)
                InviteButton(state = state, onAction = onAction)
                PendingSection(
                    pending = state.pending,
                    inFlightDecisions = state.inFlightDecisions,
                    onAction = onAction,
                )
                ActiveSection(active = state.active, onAction = onAction)
            }
            // Outside the isEmpty branch: named roster members are independent of the
            // seat-capped staff invite flow above, so "Add member" must stay reachable
            // even when the owner has zero staff memberships (isEmpty == true).
            RosterSection(roster = state.activeRoster, currentAuthUid = state.currentUserId, onAction = onAction)
            Spacer(Modifier.height(DesignTokens.space8))
        }
    }

    state.invite?.let { invite ->
        InviteSheet(invite = invite, onAction = onAction)
    }

    state.revokeTarget?.let { member ->
        RevokeDialog(member = member, onAction = onAction)
    }

    if (state.showAddMemberSheet) {
        AddMemberSheet(name = state.addMemberName, onAction = onAction)
    }

    state.renameTarget?.let { target ->
        RenameMemberSheet(target = target, onAction = onAction)
    }
}

@Composable
private fun SeatCard(state: TeamState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = DesignTokens.space2)
            .background(
                brush = Brush.linearGradient(
                    listOf(DesignTokens.indigo500, DesignTokens.indigo700),
                ),
                shape = RoundedCornerShape(DesignTokens.radiusXl),
            )
            .padding(DesignTokens.space4),
    ) {
        Text(
            text = stringResource(Res.string.team_seats_label).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = DesignTokens.indigo200,
        )
        Text(
            text = stringResource(Res.string.team_seats_used, state.seatsUsed, state.seatCap),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = DesignTokens.space2),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DesignTokens.space3),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            repeat(state.seatCap) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(
                            color = if (index < state.seatsUsed) {
                                DesignTokens.saffron500
                            } else {
                                Color.White.copy(alpha = 0.18f)
                            },
                            shape = RoundedCornerShape(DesignTokens.radiusFull),
                        ),
                )
            }
        }
        Text(
            text = stringResource(
                if (state.canInvite) Res.string.team_seats_note else Res.string.team_seats_full_note,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = DesignTokens.space3),
        )
    }
}

@Composable
private fun InviteButton(state: TeamState, onAction: (TeamAction) -> Unit) {
    StitchPadButton(
        text = stringResource(Res.string.team_invite_cta),
        onClick = { onAction(TeamAction.OnInviteClick) },
        enabled = state.canInvite,
        isLoading = state.isGeneratingInvite,
        leadingIcon = Icons.Outlined.PersonAdd,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = DesignTokens.space4),
    )
    if (!state.canInvite) {
        CapHint(text = stringResource(Res.string.team_seats_full_hint))
    }
}

@Composable
private fun CapHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = DesignTokens.space2),
    )
}

@Composable
private fun PendingSection(
    pending: List<Membership>,
    inFlightDecisions: Map<String, TeamDecision>,
    onAction: (TeamAction) -> Unit,
) {
    if (pending.isEmpty()) return
    SectionHeader(text = stringResource(Res.string.team_pending_header))
    pending.forEach { member ->
        PendingCard(
            member = member,
            decisionInFlight = inFlightDecisions[member.staffAuthUid],
            onAction = onAction,
        )
    }
}

@Composable
private fun ActiveSection(active: List<Membership>, onAction: (TeamAction) -> Unit) {
    if (active.isEmpty()) return
    SectionHeader(text = stringResource(Res.string.team_active_header_count, active.size))
    active.forEach { member ->
        ActiveMemberRow(member = member, onAction = onAction)
    }
}

/**
 * Name-only + staff roster rows, plus the "Add member" CTA. Unlike [PendingSection] and
 * [ActiveSection] this always renders (even with an empty [roster]) — named members are
 * independent of the seat-capped staff invite flow, so the CTA to add one must stay reachable.
 */
@Composable
private fun RosterSection(
    roster: List<TeamMember>,
    currentAuthUid: String?,
    onAction: (TeamAction) -> Unit,
) {
    SectionHeader(text = stringResource(Res.string.team_roster_header))
    roster.forEach { member ->
        RosterMemberRow(member = member, currentAuthUid = currentAuthUid, onAction = onAction)
    }
    StitchPadButton(
        text = stringResource(Res.string.team_add_member),
        onClick = { onAction(TeamAction.OnAddMemberClick) },
        variant = StitchPadButtonVariant.Secondary,
        leadingIcon = Icons.Outlined.PersonAdd,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = DesignTokens.space2),
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = DesignTokens.space6, bottom = DesignTokens.space3),
    )
}

@Composable
private fun PendingCard(
    member: Membership,
    decisionInFlight: TeamDecision?,
    onAction: (TeamAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = DesignTokens.space3)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(DesignTokens.radiusLg))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(DesignTokens.radiusLg),
            )
            .padding(DesignTokens.space3),
    ) {
        MemberIdentity(member = member, showStaffPill = false)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DesignTokens.space3),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            // While either decision is in flight both buttons lock, so the owner
            // can't approve and decline the same request in quick succession.
            StitchPadButton(
                text = stringResource(Res.string.team_approve),
                onClick = { onAction(TeamAction.OnApprove(member.staffAuthUid)) },
                enabled = decisionInFlight == null,
                isLoading = decisionInFlight == TeamDecision.APPROVE,
                modifier = Modifier.weight(1f),
            )
            StitchPadButton(
                text = stringResource(Res.string.team_decline),
                onClick = { onAction(TeamAction.OnDecline(member.staffAuthUid)) },
                enabled = decisionInFlight == null,
                isLoading = decisionInFlight == TeamDecision.DECLINE,
                variant = StitchPadButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActiveMemberRow(member: Membership, onAction: (TeamAction) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = DesignTokens.space3)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(DesignTokens.radiusLg))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(DesignTokens.radiusLg),
            )
            .padding(DesignTokens.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            MemberIdentity(member = member, showStaffPill = true)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(Res.string.team_member_menu_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.team_revoke)) },
                    onClick = {
                        menuOpen = false
                        onAction(TeamAction.OnRevokeClick(member))
                    },
                )
            }
        }
    }
}

/**
 * Whether a roster row exposes the rename/archive overflow menu (Task 6). Extracted as a
 * pure function (per this codebase's "no business logic in composables" rule) so it's
 * directly unit-testable without instantiating [RosterMemberRow]. Only a NAMED row is
 * mutable this way: a STAFF row's lifecycle is revoke (via [ActiveSection] above, not
 * archive), and an OWNER row (Task 5/6) is never rename/archivable — the owner is a fixed,
 * pinned-first roster member, not something the owner themself edits or removes.
 */
internal fun rosterRowShowsMenu(kind: TeamMemberKind): Boolean = kind == TeamMemberKind.NAMED

/**
 * One roster row. Mirrors [ActiveMemberRow]'s dropdown-menu pattern, but only a NAMED
 * member gets one ([rosterRowShowsMenu]) — a STAFF row shows a "linked account" caption
 * instead, and an OWNER row (Task 6) shows neither: it renders [rosterDisplayName] (so the
 * owner sees "You") with no trailing affordance at all.
 */
@Composable
private fun RosterMemberRow(
    member: TeamMember,
    currentAuthUid: String?,
    onAction: (TeamAction) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = DesignTokens.space3)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(DesignTokens.radiusLg))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(DesignTokens.radiusLg),
            )
            .padding(DesignTokens.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(name = member.name, colorSeed = member.colorSeed)
        Text(
            text = rosterDisplayName(member, currentAuthUid, stringResource(Res.string.order_assign_you)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = DesignTokens.space3),
        )
        if (member.kind == TeamMemberKind.STAFF) {
            Text(
                text = stringResource(Res.string.team_member_linked),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (rosterRowShowsMenu(member.kind)) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(Res.string.team_member_menu_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.team_rename)) },
                        onClick = {
                            menuOpen = false
                            onAction(TeamAction.OnRenameMember(member))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.team_archive)) },
                        onClick = {
                            menuOpen = false
                            onAction(TeamAction.OnArchiveMember(member))
                        },
                    )
                }
            }
        }
        // OWNER: neither branch — no trailing affordance at all (Task 6).
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemberSheet(name: String, onAction: (TeamAction) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onAction(TeamAction.OnDismissAddMember) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.space5)
                .padding(bottom = DesignTokens.space8),
        ) {
            Text(
                text = stringResource(Res.string.team_add_member_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(DesignTokens.space4))
            OutlinedTextField(
                // State-driven per MVI: the ViewModel owns addMemberName, unlike the
                // rename sheet below (no OnRenameNameChange action in the contract).
                value = name,
                onValueChange = { onAction(TeamAction.OnAddMemberNameChange(it)) },
                label = { Text(stringResource(Res.string.team_member_name_label)) },
                placeholder = { Text(stringResource(Res.string.team_member_name_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAction(TeamAction.OnConfirmAddMember) }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            )
            Spacer(Modifier.height(DesignTokens.space4))
            StitchPadButton(
                text = stringResource(Res.string.team_add_member),
                onClick = { onAction(TeamAction.OnConfirmAddMember) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameMemberSheet(target: TeamMember, onAction: (TeamAction) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Local TextFieldValue (not VM state, unlike AddMemberSheet): the contract has no
    // OnRenameNameChange action, only OnConfirmRename(String) — mirrors StyleFoldersScreen's
    // FolderNameSheet, which avoids the cursor-desync VM-owned-string would cause here too.
    var textValue by rememberSaveable(target.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(target.name))
    }
    ModalBottomSheet(
        onDismissRequest = { onAction(TeamAction.OnDismissAddMember) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.space5)
                .padding(bottom = DesignTokens.space8),
        ) {
            Text(
                text = stringResource(Res.string.team_rename_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(DesignTokens.space4))
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                label = { Text(stringResource(Res.string.team_member_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val trimmed = textValue.text.trim()
                        if (trimmed.isNotBlank()) onAction(TeamAction.OnConfirmRename(trimmed))
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            )
            Spacer(Modifier.height(DesignTokens.space4))
            StitchPadButton(
                text = stringResource(Res.string.team_rename),
                onClick = {
                    val trimmed = textValue.text.trim()
                    if (trimmed.isNotBlank()) onAction(TeamAction.OnConfirmRename(trimmed))
                },
                enabled = textValue.text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MemberIdentity(member: Membership, showStaffPill: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(name = member.staffName, seed = member.staffAuthUid)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = DesignTokens.space3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.staffName.ifBlank { member.staffEmail },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (showStaffPill) {
                    Spacer(Modifier.width(DesignTokens.space2))
                    StaffPill()
                }
            }
            Text(
                text = member.staffEmail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StaffPill() {
    Text(
        text = stringResource(Res.string.team_role_staff).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                RoundedCornerShape(DesignTokens.radiusFull),
            )
            .padding(horizontal = DesignTokens.space2, vertical = 2.dp),
    )
}

@Composable
private fun Avatar(name: String, seed: String) {
    val palette = listOf(
        listOf(DesignTokens.sienna300, DesignTokens.sienna500),
        listOf(DesignTokens.indigo400, DesignTokens.indigo700),
        listOf(Color(0xFF6B8F71), Color(0xFF3F6146)),
    )
    val colors = palette[(seed.hashCode() and Int.MAX_VALUE) % palette.size]
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(Brush.linearGradient(colors), RoundedCornerShape(DesignTokens.radiusFull)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

@Composable
private fun EmptyTeam(seatCap: Int, isGeneratingInvite: Boolean, onAction: (TeamAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(top = DesignTokens.space10)
                .size(92.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    RoundedCornerShape(DesignTokens.radiusFull),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(46.dp),
            )
        }
        Text(
            text = stringResource(Res.string.team_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = DesignTokens.space4),
        )
        Text(
            text = stringResource(Res.string.team_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(
                top = DesignTokens.space2,
                start = DesignTokens.space4,
                end = DesignTokens.space4
            ),
        )
        StitchPadButton(
            text = stringResource(Res.string.team_invite_first_cta),
            onClick = { onAction(TeamAction.OnInviteClick) },
            isLoading = isGeneratingInvite,
            leadingIcon = Icons.Outlined.PersonAdd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DesignTokens.space8),
        )
        CapHint(text = stringResource(Res.string.team_seats_plan_hint, seatCap))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteSheet(invite: TeamInviteUi, onAction: (TeamAction) -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = { onAction(TeamAction.OnDismissInviteSheet) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.space5)
                .padding(bottom = DesignTokens.space8),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.team_invite_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.team_invite_sheet_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = DesignTokens.space2),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DesignTokens.space5)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        RoundedCornerShape(DesignTokens.radiusLg),
                    )
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(DesignTokens.radiusLg),
                    )
                    .padding(DesignTokens.space4),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = invite.displayCode,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = JetBrainsMonoFamily(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
                    modifier = Modifier.padding(top = DesignTokens.space2),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (invite.expiresInDays <= 1) {
                            stringResource(Res.string.team_invite_expires_tomorrow)
                        } else {
                            stringResource(Res.string.team_invite_expires_days, invite.expiresInDays)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DesignTokens.space4),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            ) {
                OutlinedButton(
                    onClick = { onAction(TeamAction.OnCopyCode) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(DesignTokens.radiusLg),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(Res.string.team_invite_copy_code),
                        modifier = Modifier.padding(start = DesignTokens.space2),
                    )
                }
                StitchPadButton(
                    text = stringResource(Res.string.team_invite_share_link),
                    onClick = { onAction(TeamAction.OnShareLink) },
                    leadingIcon = Icons.Outlined.Share,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RevokeDialog(member: Membership, onAction: (TeamAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(TeamAction.OnDismissRevokeDialog) },
        title = { Text(stringResource(Res.string.team_revoke_dialog_title)) },
        text = {
            Text(
                stringResource(
                    Res.string.team_revoke_dialog_body,
                    member.staffName.ifBlank { member.staffEmail },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onAction(TeamAction.OnConfirmRevoke) }) {
                Text(
                    text = stringResource(Res.string.team_revoke_dialog_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(TeamAction.OnDismissRevokeDialog) }) {
                Text(stringResource(Res.string.team_revoke_dialog_cancel))
            }
        },
    )
}

private suspend fun UiText.resolve(): String = when (this) {
    is UiText.DynamicString -> value
    is UiText.StringResourceText -> getString(id)
}

// ---- Previews ----

private val sampleActive = Membership(
    staffAuthUid = "uid-gabby",
    staffEmail = "gabby.okoro@gmail.com",
    staffName = "Gabby Okoro",
    status = MembershipStatus.ACTIVE,
)
private val samplePending = Membership(
    staffAuthUid = "uid-tunde",
    staffEmail = "tunde.b@gmail.com",
    staffName = "Tunde Bakare",
    status = MembershipStatus.PENDING,
)
private const val SAMPLE_OWNER_UID = "owner-1"
private val sampleOwnerRosterMember = TeamMember(
    id = SAMPLE_OWNER_UID,
    name = "Adaeze Chukwu",
    kind = TeamMemberKind.OWNER,
    colorSeed = 0,
    status = TeamMemberStatus.ACTIVE,
)
private val sampleStaffRosterMember = TeamMember(
    id = "uid-gabby",
    name = "Gabby Okoro",
    kind = TeamMemberKind.STAFF,
    colorSeed = 1,
    status = TeamMemberStatus.ACTIVE,
)
private val sampleNamedRosterMember = TeamMember(
    id = "fake-member-0",
    name = "Ngozi Eze",
    kind = TeamMemberKind.NAMED,
    colorSeed = 4,
    status = TeamMemberStatus.ACTIVE,
)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun TeamScreenPopulatedPreview() {
    StitchPadTheme {
        TeamScreen(
            state = TeamState(
                isLoading = false,
                pending = listOf(samplePending),
                active = listOf(sampleActive),
                // Owner row first — matches the Task 5 repository-level roster sort.
                roster = listOf(sampleOwnerRosterMember, sampleStaffRosterMember, sampleNamedRosterMember),
                currentUserId = SAMPLE_OWNER_UID,
            ),
            onAction = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun TeamScreenEmptyDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        TeamScreen(
            state = TeamState(isLoading = false),
            onAction = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

/** Dedicated roster-section preview with mixed staff/named members, per task-6 brief. */
@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun RosterSectionPreview() {
    StitchPadTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxWidth()
                .padding(DesignTokens.space5),
        ) {
            RosterSection(
                roster = listOf(sampleOwnerRosterMember, sampleStaffRosterMember, sampleNamedRosterMember),
                currentAuthUid = SAMPLE_OWNER_UID,
                onAction = {},
            )
        }
    }
}
