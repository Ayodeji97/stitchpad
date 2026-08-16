package com.danzucker.stitchpad.navigation

import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaffRoleChangeRedirectTest {

    private val owner = WorkshopSession.ownerOfSelf("staff-1")
    private val pending = WorkshopSession(
        authUid = "staff-1",
        workshopUid = "staff-1",
        role = StaffRole.STAFF,
        membershipStatus = MembershipStatus.PENDING,
    )
    private val active = WorkshopSession(
        authUid = "staff-1",
        workshopUid = "owner-9",
        role = StaffRole.STAFF,
        membershipStatus = MembershipStatus.ACTIVE,
    )

    @Test
    fun redeemTransitionDoesNotRacePendingNavigation() {
        assertFalse(shouldRedirectHomeForStaffSessionChange(owner, pending))
    }

    @Test
    fun approvalTransitionRemainsOwnedByPendingScreen() {
        assertFalse(shouldRedirectHomeForStaffSessionChange(pending, active))
    }

    @Test
    fun pendingSignOutDoesNotNavigateBackIntoApp() {
        assertFalse(shouldRedirectHomeForStaffSessionChange(pending, WorkshopSession.signedOut()))
        assertFalse(shouldRedirectHomeForStaffSessionChange(pending, owner))
    }

    @Test
    fun activeStaffRevocationForSameUserRedirectsHome() {
        assertTrue(shouldRedirectHomeForStaffSessionChange(active, owner))
    }

    @Test
    fun activeStaffSignOutDoesNotRedirectHome() {
        assertFalse(shouldRedirectHomeForStaffSessionChange(active, WorkshopSession.signedOut()))
    }

    @Test
    fun demotionWithoutWorkshopProfileGoesToWorkshopSetup() {
        assertEquals(WorkshopSetupRoute, staffDemotionDestination(needsWorkshopSetup = true))
    }

    @Test
    fun demotionWithExistingProfileGoesHome() {
        assertEquals(HomeRoute, staffDemotionDestination(needsWorkshopSetup = false))
    }

    @Test
    fun demotionStillCurrentWhenAuthUidAndOwnerMatch() {
        // Demotion was acted on this session state
        val acted = active
        // Latest session state still matches (same authUid, still owner)
        val latest = owner
        assertTrue(demotionStillCurrent(acted, latest))
    }

    @Test
    fun demotionStaleWhenSignedOut() {
        // Demotion was acted on this session state
        val acted = active
        // Latest session is signed out (authUid blank, authUid changed)
        val latest = WorkshopSession.signedOut()
        assertFalse(demotionStillCurrent(acted, latest))
    }

    @Test
    fun demotionStaleWhenReActivatedAsStaff() {
        // Demotion was acted on this session state
        val acted = active
        // Latest session is re-activated as staff (authUid same, but isOwner is false)
        val reactivated = WorkshopSession(
            authUid = "staff-1",
            workshopUid = "owner-9",
            role = StaffRole.STAFF,
            membershipStatus = MembershipStatus.ACTIVE,
        )
        assertFalse(demotionStillCurrent(acted, reactivated))
    }
}
