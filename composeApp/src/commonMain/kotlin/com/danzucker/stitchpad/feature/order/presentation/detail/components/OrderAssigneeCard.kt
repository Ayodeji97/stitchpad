package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.staff.TeamMember
import com.danzucker.stitchpad.core.domain.staff.TeamMemberKind
import com.danzucker.stitchpad.core.domain.staff.TeamMemberStatus
import com.danzucker.stitchpad.core.domain.staff.rosterDisplayName
import com.danzucker.stitchpad.ui.components.MemberAvatar
import com.danzucker.stitchpad.ui.components.fallbackMemberColorSeed
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_assign_change
import stitchpad.composeapp.generated.resources.order_assign_claim_cta
import stitchpad.composeapp.generated.resources.order_assign_menu_cd
import stitchpad.composeapp.generated.resources.order_assign_section
import stitchpad.composeapp.generated.resources.order_assign_sheet_empty
import stitchpad.composeapp.generated.resources.order_assign_sheet_title
import stitchpad.composeapp.generated.resources.order_assign_unassign
import stitchpad.composeapp.generated.resources.order_assign_unassigned_owner_hint
import stitchpad.composeapp.generated.resources.order_assign_you

/**
 * Order-detail "Assigned to" card (Task 7 / Slice 8e). Four states:
 * - Unassigned + owner: tap-anywhere row that opens the roster picker ([onAssignClick]).
 * - Unassigned + staff: a "Claim this order" button ([onClaimClick]) — the only staff
 *   affordance on this card; staff never see the roster.
 * - Assigned + owner: a member chip (initials avatar + name) with a change/unassign
 *   overflow menu.
 * - Assigned + staff: the same chip, read-only — "You" when [isAssignedToSelf], the
 *   colleague's name otherwise. No overflow: staff can view but never reassign.
 *
 * [assignedMemberId] is only used to derive a stable avatar color — it is never shown as
 * text. The seed itself is roster-resolved when possible: when [roster] (the owner's live
 * active-member list, Task 7) contains a member matching [assignedMemberId], its stored
 * [com.danzucker.stitchpad.core.domain.staff.TeamMember.colorSeed] is used, matching the
 * hue shown in the assignment picker sheet ([OrderAssignPickerSheet]) and the Team screen.
 * Only when the roster has no match (staff sessions, which never receive a roster; or an
 * archived member no longer in the active list) does this fall back to
 * [fallbackMemberColorSeed]'s stable hash of the id/name.
 */
@Composable
fun OrderAssigneeCard(
    assignedMemberId: String?,
    assignedMemberName: String?,
    isActiveStaff: Boolean,
    isAssignedToSelf: Boolean,
    onAssignClick: () -> Unit,
    onClaimClick: () -> Unit,
    onUnassignClick: () -> Unit,
    roster: List<TeamMember> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(DesignTokens.radiusLg)
    val isOwnerUnassignedTap = !isActiveStaff && assignedMemberName == null
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isOwnerUnassignedTap) {
                    Modifier.clickable(onClick = onAssignClick, role = Role.Button)
                } else {
                    Modifier
                },
            ),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            ) {
                AssignSectionIconTile(imageVector = Icons.Default.Groups, contentDescription = null)
                Text(
                    text = stringResource(Res.string.order_assign_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(DesignTokens.space3))

            when {
                assignedMemberName != null -> AssignedMemberRow(
                    memberId = assignedMemberId,
                    memberName = assignedMemberName,
                    isActiveStaff = isActiveStaff,
                    isAssignedToSelf = isAssignedToSelf,
                    roster = roster,
                    onAssignClick = onAssignClick,
                    onUnassignClick = onUnassignClick,
                )
                isActiveStaff -> Button(
                    onClick = onClaimClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(DesignTokens.iconInline),
                    )
                    Spacer(Modifier.size(DesignTokens.space2))
                    Text(
                        text = stringResource(Res.string.order_assign_claim_cta),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                else -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(Res.string.order_assign_unassigned_owner_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = Icons.Outlined.PersonAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssignedMemberRow(
    memberId: String?,
    memberName: String,
    isActiveStaff: Boolean,
    isAssignedToSelf: Boolean,
    roster: List<TeamMember>,
    onAssignClick: () -> Unit,
    onUnassignClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Prefer the roster's own colorSeed (same hue the picker sheet and Team screen show for
    // this member) when it's resolvable by id; owner sessions always have the roster, so
    // this is the common path there. Staff sessions never receive a roster (Task 7), and an
    // archived member has dropped out of the active-only list this VM exposes, so both fall
    // back to fallbackMemberColorSeed's stable hash of the id (or the name).
    val colorSeed = remember(memberId, memberName, roster) {
        roster.firstOrNull { it.id == memberId }?.colorSeed
            ?: fallbackMemberColorSeed(memberId, memberName)
    }
    val displayName = if (isActiveStaff && isAssignedToSelf) {
        stringResource(Res.string.order_assign_you)
    } else {
        memberName
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MemberAvatar(name = memberName, colorSeed = colorSeed, size = DesignTokens.space8)
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = DesignTokens.space3),
        )
        // Staff view the assignment read-only — only the owner may change/unassign.
        if (!isActiveStaff) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(Res.string.order_assign_menu_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.order_assign_change)) },
                        onClick = {
                            menuOpen = false
                            onAssignClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.order_assign_unassign)) },
                        onClick = {
                            menuOpen = false
                            onUnassignClick()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AssignSectionIconTile(
    imageVector: ImageVector,
    contentDescription: String?,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Owner's roster picker sheet — lists ACTIVE team members ([roster] is already filtered
 * by [com.danzucker.stitchpad.feature.order.presentation.detail.OrderDetailViewModel]).
 * Reuses [MemberAvatar] (Task 6) so a member's color matches everywhere they appear.
 *
 * Each row's headline is [rosterDisplayName]d against [currentAuthUid] — the owner's own
 * row (Task 6, `TeamMemberKind.OWNER`) renders "You". [onSelectMember] always receives the
 * full [TeamMember], so a caller writing an assignment (`assignedMemberName`) still gets
 * the real [TeamMember.name] regardless of what this row displayed — "You" must never be
 * persisted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderAssignPickerSheet(
    roster: List<TeamMember>,
    currentAuthUid: String?,
    onSelectMember: (TeamMember) -> Unit,
    onDismiss: () -> Unit,
) {
    val youLabel = stringResource(Res.string.order_assign_you)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = DesignTokens.space3)) {
            Text(
                text = stringResource(Res.string.order_assign_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    horizontal = DesignTokens.space4,
                    vertical = DesignTokens.space3,
                ),
            )
            if (roster.isEmpty()) {
                Text(
                    text = stringResource(Res.string.order_assign_sheet_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = DesignTokens.space4),
                )
            } else {
                roster.forEach { member ->
                    ListItem(
                        headlineContent = { Text(rosterDisplayName(member, currentAuthUid, youLabel)) },
                        leadingContent = {
                            MemberAvatar(name = member.name, colorSeed = member.colorSeed, size = DesignTokens.space8)
                        },
                        modifier = Modifier.clickable(role = Role.Button) { onSelectMember(member) },
                    )
                }
            }
        }
    }
}

// region — Previews

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderAssigneeCardOwnerUnassignedPreview() {
    StitchPadTheme {
        OrderAssigneeCard(
            assignedMemberId = null,
            assignedMemberName = null,
            isActiveStaff = false,
            isAssignedToSelf = false,
            onAssignClick = {},
            onClaimClick = {},
            onUnassignClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderAssigneeCardOwnerAssignedPreview() {
    StitchPadTheme {
        OrderAssigneeCard(
            assignedMemberId = "member-paul",
            assignedMemberName = "Paul Adeyemi",
            isActiveStaff = false,
            isAssignedToSelf = false,
            onAssignClick = {},
            onClaimClick = {},
            onUnassignClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderAssigneeCardOwnerAssignedDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        OrderAssigneeCard(
            assignedMemberId = "member-paul",
            assignedMemberName = "Paul Adeyemi",
            isActiveStaff = false,
            isAssignedToSelf = false,
            onAssignClick = {},
            onClaimClick = {},
            onUnassignClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderAssigneeCardStaffUnassignedPreview() {
    StitchPadTheme {
        OrderAssigneeCard(
            assignedMemberId = null,
            assignedMemberName = null,
            isActiveStaff = true,
            isAssignedToSelf = false,
            onAssignClick = {},
            onClaimClick = {},
            onUnassignClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderAssigneeCardStaffAssignedToSelfPreview() {
    StitchPadTheme {
        OrderAssigneeCard(
            assignedMemberId = "staff-chidi",
            assignedMemberName = "Chidi Okafor",
            isActiveStaff = true,
            isAssignedToSelf = true,
            onAssignClick = {},
            onClaimClick = {},
            onUnassignClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderAssignPickerSheetContentPreview() {
    StitchPadTheme {
        Column(modifier = Modifier.fillMaxWidth().padding(top = DesignTokens.space3)) {
            Text(
                text = stringResource(Res.string.order_assign_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = DesignTokens.space4),
            )
            Spacer(Modifier.height(DesignTokens.space3))
            val ownerAuthUid = "owner-1"
            val youLabel = stringResource(Res.string.order_assign_you)
            listOf(
                TeamMember(
                    id = ownerAuthUid,
                    name = "Adaeze Chukwu",
                    kind = TeamMemberKind.OWNER,
                    colorSeed = 2,
                    status = TeamMemberStatus.ACTIVE,
                ),
                TeamMember(
                    id = "m1",
                    name = "Paul Adeyemi",
                    kind = TeamMemberKind.STAFF,
                    colorSeed = 0,
                    status = TeamMemberStatus.ACTIVE,
                ),
                TeamMember(
                    id = "m2",
                    name = "Ngozi Eze",
                    kind = TeamMemberKind.NAMED,
                    colorSeed = 1,
                    status = TeamMemberStatus.ACTIVE,
                ),
            ).forEach { member ->
                ListItem(
                    headlineContent = { Text(rosterDisplayName(member, ownerAuthUid, youLabel)) },
                    leadingContent = {
                        MemberAvatar(name = member.name, colorSeed = member.colorSeed, size = DesignTokens.space8)
                    },
                )
            }
        }
    }
}

// endregion
