package com.danzucker.stitchpad.core.data.session

import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.session.WorkshopClaims
import com.danzucker.stitchpad.core.domain.session.workshopUidOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirebaseActiveWorkshopProviderTest {

    private fun owner(uid: String) = WorkshopClaims(authUid = uid, workshopUid = null, role = null)
    private fun staff(uid: String, workshop: String) =
        WorkshopClaims(authUid = uid, workshopUid = workshop, role = "staff")

    @Test
    fun a_user_with_no_claims_resolves_to_owner_of_self() = runTest {
        val claims = MutableStateFlow<WorkshopClaims?>(owner("user-9"))
        val provider = FirebaseActiveWorkshopProvider(claims, backgroundScope)

        val session = provider.awaitHydrated()

        assertEquals("user-9", session.workshopUid)
        assertTrue(session.isOwner)
    }

    @Test
    fun a_staff_claim_resolves_to_active_staff_on_the_owners_tree() = runTest {
        val claims = MutableStateFlow<WorkshopClaims?>(staff("staff-1", "owner-9"))
        val provider = FirebaseActiveWorkshopProvider(claims, backgroundScope)

        val session = provider.awaitHydrated()

        assertEquals("staff-1", session.authUid)
        assertEquals("owner-9", session.workshopUid)
        assertTrue(session.isActiveStaff)
    }

    @Test
    fun a_claim_change_re_resolves_the_session() = runTest {
        // Simulates the post-approval token refresh: owner-of-self → staff.
        val claims = MutableStateFlow<WorkshopClaims?>(owner("staff-1"))
        val provider = FirebaseActiveWorkshopProvider(claims, backgroundScope)
        assertTrue(provider.awaitHydrated().isOwner)

        claims.value = staff("staff-1", "owner-9")
        runCurrent()

        assertEquals(StaffRole.STAFF, provider.current().role)
        assertEquals("owner-9", provider.current().workshopUid)
    }

    @Test
    fun signed_out_resolves_immediately_and_does_not_hang() = runTest {
        // Regression: an unhydrated signed-out state made awaitHydrated()/
        // workshopUidOrNull() suspend forever. Signed-out is resolved → null.
        val claims = MutableStateFlow<WorkshopClaims?>(null)
        val provider = FirebaseActiveWorkshopProvider(claims, backgroundScope)

        val uid = withTimeout(1_000) { provider.workshopUidOrNull() }

        assertNull(uid)
    }

    @Test
    fun sign_out_after_sign_in_still_resolves_without_hanging() = runTest {
        val claims = MutableStateFlow<WorkshopClaims?>(owner("user-9"))
        val provider = FirebaseActiveWorkshopProvider(claims, backgroundScope)
        assertEquals("user-9", provider.awaitHydrated().workshopUid)

        claims.value = null
        runCurrent()

        val uid = withTimeout(1_000) { provider.workshopUidOrNull() }
        assertNull(uid)
    }

    // ── Pending window: no claim yet, driven by the stored workshopUid +
    //    the watched membership doc (before the approval token refresh). ──────

    @Test
    fun a_pending_membership_doc_resolves_to_pending_staff() = runTest {
        // Redeemed but not approved: no staff claim, but a stored workshopUid
        // from redeem time drives watching the membership doc.
        val claims = MutableStateFlow<WorkshopClaims?>(owner("staff-1"))
        val membership = MutableStateFlow<MembershipStatus?>(MembershipStatus.PENDING)
        val provider = FirebaseActiveWorkshopProvider(
            authClaims = claims,
            scope = backgroundScope,
            storedWorkshopUid = MutableStateFlow("owner-9"),
            membershipStatusFlow = { _, _ -> membership },
        )

        val session = provider.awaitHydrated()
        // resolve() keeps a pending staffer on their own tree — no owner data
        // is addressable until approval — but with the STAFF/PENDING marker so
        // nav can route to the pending screen.
        assertEquals(StaffRole.STAFF, session.role)
        assertEquals(MembershipStatus.PENDING, session.membershipStatus)
        assertEquals("staff-1", session.workshopUid)
    }

    @Test
    fun an_active_membership_doc_forces_a_refresh_but_holds_until_the_claim_lands() =
        runTest {
            val claims = MutableStateFlow<WorkshopClaims?>(owner("staff-1"))
            val membership = MutableStateFlow<MembershipStatus?>(MembershipStatus.PENDING)
            var refreshes = 0
            val provider = FirebaseActiveWorkshopProvider(
                authClaims = claims,
                scope = backgroundScope,
                storedWorkshopUid = MutableStateFlow("owner-9"),
                membershipStatusFlow = { _, _ -> membership },
                // A real refresh would re-emit authClaims with the staff claim;
                // simulate that below by setting `claims` after the refresh count.
                refreshToken = { refreshes++ },
            )
            assertEquals(MembershipStatus.PENDING, provider.awaitHydrated().membershipStatus)

            // Owner approves: doc flips to active before the token carries the
            // claim. The provider forces a refresh but must NOT expose the owner's
            // tree yet — reads would be denied without the claim. It holds PENDING.
            membership.value = MembershipStatus.ACTIVE
            runCurrent()

            assertTrue(refreshes >= 1)
            assertFalse(provider.current().isActiveStaff)
            assertEquals("staff-1", provider.current().workshopUid)

            // The forced refresh lands the staff claim on the token → promote.
            claims.value = staff("staff-1", "owner-9")
            runCurrent()

            assertTrue(provider.current().isActiveStaff)
            assertEquals("owner-9", provider.current().workshopUid)
        }

    @Test
    fun a_revoked_membership_doc_falls_back_to_owner_of_self() = runTest {
        val claims = MutableStateFlow<WorkshopClaims?>(owner("staff-1"))
        val membership = MutableStateFlow<MembershipStatus?>(MembershipStatus.REVOKED)
        val provider = FirebaseActiveWorkshopProvider(
            authClaims = claims,
            scope = backgroundScope,
            storedWorkshopUid = MutableStateFlow("owner-9"),
            membershipStatusFlow = { _, _ -> membership },
        )

        val session = provider.awaitHydrated()

        assertTrue(session.isOwner)
        assertEquals("staff-1", session.workshopUid)
    }

    @Test
    fun no_stored_workshop_uid_resolves_to_owner_of_self_without_watching() = runTest {
        // A genuine owner with no stored workshopUid never watches a doc.
        var watched = false
        val claims = MutableStateFlow<WorkshopClaims?>(owner("user-9"))
        val provider = FirebaseActiveWorkshopProvider(
            authClaims = claims,
            scope = backgroundScope,
            storedWorkshopUid = MutableStateFlow(null),
            membershipStatusFlow = { _, _ -> watched = true; MutableStateFlow(null) },
        )

        val session = provider.awaitHydrated()

        assertTrue(session.isOwner)
        assertEquals("user-9", session.workshopUid)
        assertTrue(!watched)
    }

    @Test
    fun saving_the_workshop_uid_after_hydration_enters_the_pending_window() = runTest {
        // Regression (codex P1): redeeming an invite writes the workshopUid with
        // NO auth-token change. The provider must observe the stored uid
        // reactively, or it would stay owner-of-self until an app restart.
        val claims = MutableStateFlow<WorkshopClaims?>(owner("staff-1"))
        val storedWs = MutableStateFlow<String?>(null)
        val membership = MutableStateFlow<MembershipStatus?>(MembershipStatus.PENDING)
        val provider = FirebaseActiveWorkshopProvider(
            authClaims = claims,
            scope = backgroundScope,
            storedWorkshopUid = storedWs,
            membershipStatusFlow = { _, _ -> membership },
        )
        assertTrue(provider.awaitHydrated().isOwner)

        // Redeem persists the workshopUid — no token change.
        storedWs.value = "owner-9"
        runCurrent()

        assertEquals(StaffRole.STAFF, provider.current().role)
        assertEquals(MembershipStatus.PENDING, provider.current().membershipStatus)
    }
}
