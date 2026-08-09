package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.StatusChange
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Order-detail "Assigned to" card logic (Task 7 / Slice 8e): owner picker + staff claim.
 *
 * [OrderDetailViewModel] cannot be instantiated in commonTest — it requires a Coil
 * [coil3.ImageLoader] + [coil3.PlatformContext], and on the Android unit-test classpath
 * PlatformContext == android.content.Context whose stubs throw "Stub!" without
 * Robolectric (this module is not configured for it) — see the identical note on
 * [DetailStylePickerTest] and [OrderDetailStaffGuardTest]. So this targets the pure
 * decision logic the ViewModel delegates to verbatim ([canClaimOrder],
 * [resolveClaimDisplayName]) and the [OrderDetailAction.isStaffRestricted] gate that
 * [OrderDetailViewModel.onAction] checks BEFORE ever dispatching to the repository
 * (`if (isActiveStaff && action.isStaffRestricted()) return`, onAction:142) — together
 * these two are the VM's entire "who can assign what" decision, so exercising them
 * against a real [FakeOrderRepository] write is equivalent to driving the VM itself.
 */
class OrderAssignmentTest {

    private fun order(assignedMemberId: String? = null, assignedMemberName: String? = null) = Order(
        id = "o1",
        userId = "owner-1",
        customerId = "c1",
        customerName = "Test Customer",
        items = listOf(
            OrderItem(id = "i1", garmentType = GarmentType.AGBADA, description = "Demo", price = 5_000.0),
        ),
        status = OrderStatus.PENDING,
        priority = OrderPriority.NORMAL,
        statusHistory = listOf(StatusChange(OrderStatus.PENDING, 0L)),
        totalPrice = 5_000.0,
        deadline = null,
        notes = null,
        createdAt = 0L,
        updatedAt = 0L,
        assignedMemberId = assignedMemberId,
        assignedMemberName = assignedMemberName,
    )

    // --- owner: OnAssignMember is unrestricted and writes through verbatim ---

    @Test
    fun `owner assigning calls assignOrder with the picked member`() = runTest {
        val fakeOrderRepository = FakeOrderRepository()
        fakeOrderRepository.ordersList = listOf(order())

        // isStaffRestricted() is a property of the ACTION, not the session — OnAssignMember
        // is on the restricted list so a staff session can never reach it (see the next
        // test). OrderDetailViewModel.onAction only early-returns when BOTH isActiveStaff
        // AND isStaffRestricted() are true (onAction:142), so for an OWNER session
        // (isActiveStaff == false) the guard never fires and the write reaches the
        // repository verbatim, mirroring OrderDetailViewModel.assignMember().
        assertTrue(OrderDetailAction.OnAssignMember(memberId = "paul", memberName = "Paul").isStaffRestricted())
        fakeOrderRepository.assignOrder(userId = "owner-1", orderId = "o1", memberId = "paul", memberName = "Paul")

        assertEquals(Triple("o1", "paul", "Paul"), fakeOrderRepository.lastAssignment)
    }

    // --- staff: OnAssignMember never reaches the repository; OnClaimClick assigns to self ---

    @Test
    fun `staff claim assigns to self and staff cannot assign others`() = runTest {
        val fakeOrderRepository = FakeOrderRepository()
        val unassigned = order()
        fakeOrderRepository.ordersList = listOf(unassigned)

        // OnAssignMember IS staff-restricted — onAction:142 early-returns before the
        // repository is ever touched, so it stays untouched (mirrors the brief's
        // `assertNull(fakeOrderRepository.lastAssignment)`).
        assertTrue(OrderDetailAction.OnAssignMember(memberId = "paul", memberName = "Paul").isStaffRestricted())
        assertEquals(null, fakeOrderRepository.lastAssignment)

        // OnClaimClick is NOT staff-restricted, and the order is unassigned, so
        // OrderDetailViewModel.claimOrder() proceeds and assigns to the staff's own uid.
        assertFalse(OrderDetailAction.OnClaimClick.isStaffRestricted())
        assertTrue(canClaimOrder(unassigned.assignedMemberId))
        val displayName = resolveClaimDisplayName(profileName = "Chidi Okafor", email = "chidi@example.com", fallback = "Staff")
        fakeOrderRepository.assignOrder(userId = "owner-1", orderId = "o1", memberId = "chidi", memberName = displayName)

        assertEquals("chidi", fakeOrderRepository.lastAssignment?.second)
    }

    // --- claim is a defense-in-depth no-op once the order already has an assignee ---

    @Test
    fun `claim is a no-op when the order is already assigned`() {
        val alreadyAssigned = order(assignedMemberId = "paul", assignedMemberName = "Paul")
        // canClaimOrder(false) is what OrderDetailViewModel.claimOrder() early-returns
        // on — the repository is never called for an already-assigned order.
        assertFalse(canClaimOrder(alreadyAssigned.assignedMemberId))
    }

    @Test
    fun `claim is eligible only when unassigned`() {
        assertTrue(canClaimOrder(null))
        assertFalse(canClaimOrder("someone-else"))
    }

    // --- OnUnassignClick / OnAssignClick guard placement ---

    @Test
    fun `unassign click is staff restricted`() {
        assertTrue(OrderDetailAction.OnUnassignClick.isStaffRestricted())
    }

    @Test
    fun `assign click is staff restricted`() {
        assertTrue(OrderDetailAction.OnAssignClick.isStaffRestricted())
    }

    @Test
    fun `dismiss assign sheet is not staff restricted`() {
        assertFalse(OrderDetailAction.OnDismissAssignSheet.isStaffRestricted())
    }

    // --- resolveClaimDisplayName precedence: profile name -> email -> fallback ---

    @Test
    fun `resolveClaimDisplayName prefers the profile name`() {
        assertEquals(
            "Chidi Okafor",
            resolveClaimDisplayName(profileName = "Chidi Okafor", email = "chidi@example.com", fallback = "Staff"),
        )
    }

    @Test
    fun `resolveClaimDisplayName falls back to email when the name is blank`() {
        assertEquals(
            "chidi@example.com",
            resolveClaimDisplayName(profileName = "  ", email = "chidi@example.com", fallback = "Staff"),
        )
    }

    @Test
    fun `resolveClaimDisplayName falls back to the fallback when both are blank`() {
        assertEquals(
            "Staff",
            resolveClaimDisplayName(profileName = null, email = null, fallback = "Staff"),
        )
    }

    // --- shouldObserveRoster: guards OrderDetailViewModel.observeActiveWorkshop's owner
    // branch against calling observeRoster(""), which crashes uncaught
    // (firestore.collection("users").document("") throws IllegalArgumentException).
    // See its KDoc for the retained-VM-survives-sign-out scenario this closes. ---

    @Test
    fun `owner session with a resolved workshopUid should observe the roster`() {
        assertTrue(shouldObserveRoster(WorkshopSession.ownerOfSelf(authUid = "owner-1")))
    }

    @Test
    fun `signed-out session never observes the roster`() {
        // WorkshopSession.signedOut() is ownerOfSelf("") — OWNER role, blank workshopUid.
        // This is the exact shape that used to crash: a retained detail VM signs out mid
        // -session and this emission reaches observeActiveWorkshop's owner branch.
        assertFalse(shouldObserveRoster(WorkshopSession.signedOut()))
    }

    @Test
    fun `any owner session with a blank workshopUid never observes the roster`() {
        // Not just the canonical signedOut() placeholder — any owner session with a blank
        // workshopUid must be guarded, however it was constructed.
        assertFalse(
            shouldObserveRoster(
                WorkshopSession(authUid = "a", workshopUid = "", role = StaffRole.OWNER, membershipStatus = null),
            ),
        )
    }

    @Test
    fun `active staff session never observes the roster even with a resolved workshopUid`() {
        assertFalse(
            shouldObserveRoster(
                WorkshopSession(
                    authUid = "staff-1",
                    workshopUid = "owner-1",
                    role = StaffRole.STAFF,
                    membershipStatus = MembershipStatus.ACTIVE,
                ),
            ),
        )
    }

    @Test
    fun `pending staff session is not active staff and does observe the roster`() {
        // role == STAFF but membershipStatus != ACTIVE means isActiveStaff is false —
        // shouldObserveRoster only excludes staff via isActiveStaff, so this (unusual, but
        // possible mid-approval) shape falls through to the workshopUid check.
        assertTrue(
            shouldObserveRoster(
                WorkshopSession(
                    authUid = "staff-1",
                    workshopUid = "owner-1",
                    role = StaffRole.STAFF,
                    membershipStatus = MembershipStatus.PENDING,
                ),
            ),
        )
    }
}
