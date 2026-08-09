package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.sharing.ReceiptDocumentType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Slice 6c — an active staff member on the order DETAIL screen may view + advance
 * production, but must never trigger any money (prices / payments / costs / receipts)
 * or customer-contact (call / WhatsApp / reminder / add-phone) action.
 *
 * [OrderDetailViewModel] cannot be instantiated in commonTest — it requires a Coil
 * [coil3.ImageLoader] + [coil3.PlatformContext], and on the Android unit-test classpath
 * PlatformContext == android.content.Context whose stubs throw "Stub!" without
 * Robolectric (see DetailStylePickerTest). So, exactly like every sibling detail test,
 * this targets the pure guard logic the ViewModel delegates to verbatim: the
 * [OrderDetailAction.isStaffRestricted] predicate ([OrderDetailViewModel.onAction]
 * early-returns on it for staff) and [requiresBalanceWarning] (which suppresses the
 * balance dialog for staff so a status advance proceeds directly).
 */
class OrderDetailStaffGuardTest {

    // --- (b) money / contact actions are restricted for staff ---

    @Test
    fun recordPaymentClick_isStaffRestricted() {
        assertTrue(OrderDetailAction.OnRecordPaymentClick.isStaffRestricted())
    }

    @Test
    fun shareClick_isStaffRestricted() {
        assertTrue(OrderDetailAction.OnShareClick.isStaffRestricted())
    }

    @Test
    fun whatsAppClick_isStaffRestricted() {
        assertTrue(OrderDetailAction.OnWhatsAppClick.isStaffRestricted())
    }

    @Test
    fun callClick_isStaffRestricted() {
        assertTrue(OrderDetailAction.OnCallClick.isStaffRestricted())
    }

    @Test
    fun editCostsClick_isStaffRestricted() {
        assertTrue(OrderDetailAction.OnEditCostsClick.isStaffRestricted())
    }

    @Test
    fun everyMoneyAndContactAction_isStaffRestricted() {
        val restricted = listOf(
            // Share / receipt
            OrderDetailAction.OnShareClick,
            OrderDetailAction.OnShareAsImageClick,
            OrderDetailAction.OnShareAsPdfClick,
            OrderDetailAction.OnDocumentTypeChoice(ReceiptDocumentType.INVOICE),
            OrderDetailAction.OnShareReceiptFromSnackbar,
            // Payment
            OrderDetailAction.OnRecordPaymentClick,
            OrderDetailAction.OnPaymentAmountChange("5000"),
            OrderDetailAction.OnPaymentMethodSelect(PaymentMethod.TRANSFER),
            OrderDetailAction.OnPaymentTypeSelect(PaymentType.DEPOSIT),
            OrderDetailAction.OnMarkPaidInFull,
            OrderDetailAction.OnConfirmRecordPayment,
            OrderDetailAction.OnPaymentHistoryToggle,
            OrderDetailAction.OnBalanceWarningRecordPayment,
            OrderDetailAction.OnBalanceWarningProceed,
            OrderDetailAction.OnBalanceWarningDismiss,
            // Costs
            OrderDetailAction.OnEditCostsClick,
            OrderDetailAction.OnSaveCosts,
            OrderDetailAction.OnDismissCostsEditor,
            OrderDetailAction.OnCostsExpandToggle,
            // Contact
            OrderDetailAction.OnWhatsAppClick,
            OrderDetailAction.OnCallClick,
            OrderDetailAction.OnSendReminderClick,
            OrderDetailAction.OnAddPhoneClick,
            // Create / edit / destroy — both arm AND confirm/execute must be guarded
            // (a cold-start race can arm the dialog before isActiveStaff resolves).
            OrderDetailAction.OnEditClick,
            OrderDetailAction.OnDuplicateClick,
            OrderDetailAction.OnArchiveClick,
            OrderDetailAction.OnConfirmArchive,
            OrderDetailAction.OnDeleteClick,
            OrderDetailAction.OnConfirmDelete,
            // Assignment (Task 7) — owner-only; OnClaimClick/OnDismissAssignSheet stay
            // available to staff (checked separately below).
            OrderDetailAction.OnAssignClick,
            OrderDetailAction.OnAssignMember(memberId = "paul", memberName = "Paul"),
            OrderDetailAction.OnUnassignClick,
        )
        restricted.forEach {
            assertTrue(it.isStaffRestricted(), "$it must be staff-restricted")
        }
    }

    // --- Task 10: every order-detail affordance the rules deny for staff ---

    @Test
    fun everyPhase2AffordanceAudit_isStaffRestricted() {
        val restricted = listOf(
            // Due date — owner-only (2026-08-08 product decision).
            OrderDetailAction.OnSetDeadlineClick,
            OrderDetailAction.OnDeadlineSelected(epochMillis = 1_754_611_200_000L),
            // Measurements-link — owner-only by product decision (Phase 2b): rules
            // technically admit items[].measurementId, but linking measurements is an
            // order-setup decision, kept owner-only regardless.
            OrderDetailAction.OnLinkMeasurementsClick,
            OrderDetailAction.OnSelectMeasurement(measurementId = "m1"),
        )
        restricted.forEach {
            assertTrue(it.isStaffRestricted(), "$it must be staff-restricted")
        }
    }

    /**
     * Whole-branch review (Important): the saved-style LIBRARY is owner-only in the
     * Firestore rules (`customers/{id}/styles`, `styleFolders`, `inspiration*` are all
     * `isOwner`), so for staff the picker can only ever be empty and its "Create new"
     * button led to a StyleFormRoute where the style-doc create, the style-photo
     * Storage upload and the `updateOrder` order link are ALL server-denied. Both the
     * picker entry point and the create button are staff-restricted, and
     * OrderDetailScreen hides the "Pick from saved" row for staff so the guard has no
     * dead affordance to defend.
     *
     * `OnCreateNewMeasurementClick` is in the same family: its ONLY entry point is the
     * measurement picker sheet, which only opens via the already-restricted
     * `OnLinkMeasurementsClick`. It is listed here explicitly so that reachability
     * stops being luck — if a future screen wires a second entry point, the guard still
     * holds.
     */
    @Test
    fun ownerOnlyLibraryCreationActions_areStaffRestricted() {
        val restricted = listOf(
            OrderDetailAction.OnAddStyleClick(itemId = "i1"),
            OrderDetailAction.OnCreateNewStyleClick(itemId = "i1"),
            OrderDetailAction.OnCreateNewMeasurementClick,
        )
        restricted.forEach {
            assertTrue(it.isStaffRestricted(), "$it must be staff-restricted")
        }
    }

    // --- Phase 2b: garment media + notes are staff-enabled (rules work-fields whitelist) ---

    @Test
    fun garmentMediaAndNotesActions_areNotStaffRestricted() {
        val allowed = listOf(
            // Garment media (style + fabric) — staff-enabled as of Phase 2b. Actions
            // wired from OrderGarmentDetailsCard's callbacks (OrderDetailScreen.kt).
            // NOTE: OnAddStyleClick (the saved-style PICKER) is NOT here — the style
            // library is owner-only, see ownerOnlyLibraryCreationActions_* above. Staff
            // add style photos by camera/gallery upload, which is this action:
            OrderDetailAction.OnAddStylePhoto(itemId = "i1", photoBytes = byteArrayOf(1, 2, 3)),
            OrderDetailAction.OnRemoveStyleImage(itemId = "i1", index = 0),
            OrderDetailAction.OnAddFabricPhoto(itemId = "i1", photoBytes = byteArrayOf(4, 5, 6)),
            OrderDetailAction.OnRemoveFabricImage(itemId = "i1", index = 0),
            OrderDetailAction.OnAddFabricNameClick,
            OrderDetailAction.OnFabricNameDraftChange("Royal Lace"),
            OrderDetailAction.OnSaveFabricName,
            // Notes editing — staff-enabled as of Phase 2b.
            OrderDetailAction.OnNotesEditClick,
            OrderDetailAction.OnNotesDraftChange("Use gold thread"),
            OrderDetailAction.OnNotesSaveClick,
        )
        allowed.forEach {
            assertFalse(it.isStaffRestricted(), "$it must stay available to staff")
        }
    }

    // --- (c) production + neutral actions stay available for staff ---

    @Test
    fun statusAdvanceActions_areNotStaffRestricted() {
        val allowed = listOf(
            OrderDetailAction.OnUpdateStatusClick,
            OrderDetailAction.OnSelectStatusTransition(
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.SEWING),
            ),
            OrderDetailAction.OnDismissStatusSheet,
        )
        allowed.forEach {
            assertFalse(it.isStaffRestricted(), "$it must stay available to staff")
        }
    }

    @Test
    fun neutralViewAndEditActions_areNotStaffRestricted() {
        val allowed = listOf(
            OrderDetailAction.OnBackClick,
            // Notes cancel, deadline-dialog dismiss, and view-measurement all stay
            // reachable — they mutate nothing (Task 10: only the notes/deadline/
            // measurements-link WRITE paths above are guarded, not their dismisses).
            OrderDetailAction.OnNotesCancelClick,
            OrderDetailAction.OnDismissDatePickerDialog,
            OrderDetailAction.OnViewMeasurementClick,
        )
        allowed.forEach {
            assertFalse(it.isStaffRestricted(), "$it must stay available to staff")
        }
    }

    // --- (d) staff self-claim stays available (Task 7) ---

    @Test
    fun claimClickAndDismissAssignSheet_areNotStaffRestricted() {
        val allowed = listOf(
            OrderDetailAction.OnClaimClick,
            OrderDetailAction.OnDismissAssignSheet,
        )
        allowed.forEach {
            assertFalse(it.isStaffRestricted(), "$it must stay available to staff")
        }
    }

    // --- balance-warning suppression drives the "advance directly" behaviour ---

    @Test
    fun requiresBalanceWarning_isFalseForStaff_evenWithBalanceOwed() {
        assertFalse(
            requiresBalanceWarning(
                isActiveStaff = true,
                balanceRemaining = 50_000.0,
                toStatus = OrderStatus.READY,
            ),
        )
    }

    @Test
    fun requiresBalanceWarning_isTrueForOwner_withBalanceOwedOnReady() {
        assertTrue(
            requiresBalanceWarning(
                isActiveStaff = false,
                balanceRemaining = 50_000.0,
                toStatus = OrderStatus.READY,
            ),
        )
    }

    @Test
    fun requiresBalanceWarning_isFalseForOwner_whenNothingOwed() {
        assertFalse(
            requiresBalanceWarning(
                isActiveStaff = false,
                balanceRemaining = 0.0,
                toStatus = OrderStatus.DELIVERED,
            ),
        )
    }

    @Test
    fun requiresBalanceWarning_isFalseForOwner_onNonReadyTransition() {
        assertFalse(
            requiresBalanceWarning(
                isActiveStaff = false,
                balanceRemaining = 50_000.0,
                toStatus = OrderStatus.IN_PROGRESS,
            ),
        )
    }
}
