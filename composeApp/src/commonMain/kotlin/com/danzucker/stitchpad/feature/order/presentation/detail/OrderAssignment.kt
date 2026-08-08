package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.session.WorkshopSession

/**
 * Pure decision logic behind the order-detail "Assigned to" card (Task 7 / Slice 8e).
 * Kept outside [OrderDetailViewModel] so it's directly unit-testable — the ViewModel
 * itself cannot be instantiated in commonTest (it requires a Coil `ImageLoader` +
 * `PlatformContext`; see the KDoc on [DetailStylePickerTest][com.danzucker.stitchpad.feature.order.presentation.detail.DetailStylePickerTest]
 * and [OrderDetailStaffGuardTest][com.danzucker.stitchpad.feature.order.presentation.detail.OrderDetailStaffGuardTest]
 * for the same constraint on every sibling detail-logic file), so this mirrors
 * PaymentMath.kt / StylePickerLogic.kt / StatusTransition.kt: the VM delegates to these
 * functions verbatim.
 */

/**
 * Staff CLAIM is a one-way null -> self move (Task 3 rules invariant). This is
 * defense-in-depth on top of the Firestore rule: it stops a stray tap on a stale card
 * (e.g. a race right after another device already claimed the order) from re-firing a
 * claim write that would silently overwrite an existing assignment.
 */
internal fun canClaimOrder(assignedMemberId: String?): Boolean = assignedMemberId == null

/**
 * The display name written alongside a staff self-claim (`assignOrder`'s `memberName`).
 * Order of preference: the signed-in user's profile display name, then their email, then
 * [fallback] — a claim must never write a blank name. See task-7-report.md for why this
 * order was chosen (profile name over an email-local-part heuristic).
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
 * Whether [OrderDetailViewModel.observeActiveWorkshop] should (re)subscribe to the live
 * roster for [session]. Only an owner session with a resolvable tree qualifies:
 * - Staff never see the picker (Task 7) regardless of [WorkshopSession.workshopUid].
 * - A signed-out emission ([WorkshopSession.signedOut]) resolves to an OWNER role with a
 *   blank [WorkshopSession.workshopUid] — without this guard, calling
 *   `observeRoster("")` reaches `firestore.collection("users").document("")`, which throws
 *   `IllegalArgumentException` uncaught inside `viewModelScope`. This case is reachable in
 *   practice: a retained detail VM (kept alive across bottom-tab switches by Compose Nav's
 *   `saveState`) is still subscribed to [WorkshopSession] when the user signs out, so the
 *   signed-out placeholder lands here before the VM itself is torn down.
 */
internal fun shouldObserveRoster(session: WorkshopSession): Boolean =
    !session.isActiveStaff && session.workshopUid.isNotBlank()
