package com.danzucker.stitchpad.core.domain.staff

/**
 * The display name to write alongside a roster-attributed action for the signed-in user
 * (a staff self-claim's `memberName`, or the owner's lazily-created [TeamMemberKind.OWNER]
 * roster row). Order of preference: the signed-in user's profile display name, then their
 * email, then [fallback] — these writes must never carry a blank name.
 *
 * Shared between `feature/order` (staff claim) and `feature/staff` (owner lazy ensure) —
 * lives in `core/domain/staff` rather than either feature package so neither imports the
 * other (feature-to-feature imports are against this codebase's package rules). Originally
 * lived in `feature/order/presentation/detail/OrderAssignment.kt`; see
 * task-7-report.md for why this preference order was chosen (profile name over an
 * email-local-part heuristic).
 */
internal fun resolveClaimDisplayName(
    profileName: String?,
    email: String?,
    fallback: String,
): String =
    profileName?.trim()?.takeIf { it.isNotBlank() }
        ?: email?.trim()?.takeIf { it.isNotBlank() }
        ?: fallback

/**
 * How a roster row's name renders for the *viewer*: the signed-in viewer's own row (owner
 * or otherwise) shows [youLabel] instead of [TeamMember.name] — every other row shows its
 * real name unchanged. Shared between `feature/staff`'s Team rows and `feature/order`'s
 * assign picker (Task 6) for the same reason [resolveClaimDisplayName] lives here rather
 * than in either feature: neither feature package imports the other.
 *
 * This is a display-only substitution — callers must keep writing [TeamMember.name] (the
 * real name) into any persisted field (e.g. `Order.assignedMemberName`); [youLabel] must
 * never be written to storage, or every device would render the literal string "You".
 */
internal fun rosterDisplayName(
    member: TeamMember,
    currentAuthUid: String?,
    youLabel: String,
): String = if (currentAuthUid != null && member.id == currentAuthUid) youLabel else member.name
