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
