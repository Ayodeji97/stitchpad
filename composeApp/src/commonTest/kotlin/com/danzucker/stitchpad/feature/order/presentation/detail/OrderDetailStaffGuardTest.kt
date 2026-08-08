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
            OrderDetailAction.OnNotesEditClick,
            OrderDetailAction.OnSetDeadlineClick,
            OrderDetailAction.OnLinkMeasurementsClick,
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
