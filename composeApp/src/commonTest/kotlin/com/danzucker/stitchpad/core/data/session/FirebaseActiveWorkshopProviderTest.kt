package com.danzucker.stitchpad.core.data.session

import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.session.WorkshopClaims
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
import com.danzucker.stitchpad.core.domain.session.workshopUidOrNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
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

    // ── Active-claim window: the membership doc is watched for the whole life
    //    of a claim-backed staff session so mid-session revocation propagates
    //    without waiting for the hourly token refresh. ──────────────────────────

    @Test
    fun revoking_an_active_staff_session_demotes_to_owner_of_self() = runTest {
        val claims = MutableStateFlow<WorkshopClaims?>(staff("staff-1", "owner-9"))
        val membership = MutableStateFlow<MembershipStatus?>(MembershipStatus.ACTIVE)
        var watchedWorkshop: String? = null
        var refreshes = 0
        var revokedCallbacks = 0
        val provider = FirebaseActiveWorkshopProvider(
            authClaims = claims,
            scope = backgroundScope,
            membershipStatusFlow = { workshopUid, _ -> watchedWorkshop = workshopUid; membership },
            refreshToken = { refreshes++ },
            onStaffRevoked = { revokedCallbacks++ },
        )
        assertTrue(provider.awaitHydrated().isActiveStaff)
        assertEquals("owner-9", watchedWorkshop)

        // Owner revokes: the doc flips while the token still carries the claim.
        membership.value = MembershipStatus.REVOKED
        runCurrent()

        assertTrue(provider.current().isOwner)
        assertEquals("staff-1", provider.current().workshopUid)
        // The stale claim must be refreshed off the token, and the redeem-time
        // prefs cleared so the stale uid cannot re-enter the pending window.
        assertTrue(refreshes >= 1)
        assertTrue(revokedCallbacks >= 1)
    }

    @Test
    fun revoking_mid_session_demotes_without_racing_the_prefs_clear_flow_restart() = runTest {
        // Reproduces the production coupling (di/CoreModule.kt):
        // `onStaffRevoked = { membershipPrefs.clear() }` clears the SAME flow
        // `storedWorkshopUid` observes, and that flow is part of the outer
        // combine's flatMapLatest key. A mid-session active staffer still has a
        // populated storedWorkshopUid (StaffPendingViewModel never clears it on
        // promotion), so clearing prefs here can restart the outer flow WHILE
        // refreshToken() is still in flight, cancelling the in-flight demotion
        // before it reaches collectors.
        val claims = MutableStateFlow<WorkshopClaims?>(staff("staff-1", "owner-9"))
        val membership = MutableStateFlow<MembershipStatus?>(MembershipStatus.ACTIVE)
        val storedWs = MutableStateFlow<String?>("owner-9")
        var refreshes = 0
        var revokedCallbacks = 0
        var membershipSubscriptions = 0
        val provider = FirebaseActiveWorkshopProvider(
            authClaims = claims,
            scope = backgroundScope,
            storedWorkshopUid = storedWs,
            membershipStatusFlow = { _, _ -> membershipSubscriptions++; membership },
            // A real forceRefreshIdToken() call suspends on network I/O; yield()
            // mirrors that suspension so the outer combine gets a chance to react
            // to the prefs clear before this side effect completes. The provider
            // now LAUNCHES this instead of awaiting it (so the demotion emits
            // immediately) — runCurrent() below drains that launch, so the
            // "exactly one refresh" assertion still holds.
            refreshToken = { refreshes++; yield() },
            // Mirrors CoreModule's `onStaffRevoked = { membershipPrefs.clear() }`
            // — the SAME flow instance storedWorkshopUid observes, unlike the
            // decoupled `flowOf(null)` used by the sibling test above.
            onStaffRevoked = { revokedCallbacks++; storedWs.value = null },
        )
        assertTrue(provider.awaitHydrated().isActiveStaff)
        assertEquals(1, membershipSubscriptions)

        val emissions = mutableListOf<WorkshopSession>()
        val collectJob = launch { provider.flow.collect { emissions.add(it) } }
        runCurrent()

        // Owner revokes: the doc flips while the token still carries the claim.
        membership.value = MembershipStatus.REVOKED
        runCurrent()
        collectJob.cancel()

        val demotedIndex = emissions.indexOfFirst { it.isOwner }
        assertTrue(demotedIndex >= 0, "expected a demotion to be emitted; got $emissions")
        assertTrue(
            emissions.drop(demotedIndex).none { it.isActiveStaff },
            "demotion regressed back to active staff after being emitted: $emissions",
        )
        // The demotion must land from the FIRST REVOKED snapshot: no wasted
        // extra forceRefreshIdToken call and no listener resubscribe caused by
        // the prefs-clear racing the flatMapLatest restart.
        assertEquals(1, refreshes, "demotion required more than one refreshToken() pass")
        assertEquals(1, revokedCallbacks, "onStaffRevoked fired more than once")
        assertEquals(1, membershipSubscriptions, "the membership doc listener was resubscribed")
    }

    @Test
    fun revocation_demotes_before_a_slow_token_refresh_completes() = runTest {
        // The demotion is what stops every owner-tree listener's permission-denied
        // retry loop, so it must not queue behind forceRefreshIdToken()'s network
        // round-trip. Here refreshToken() never returns until the test releases it;
        // the demoted session must already be observable.
        val claims = MutableStateFlow<WorkshopClaims?>(staff("staff-1", "owner-9"))
        val membership = MutableStateFlow<MembershipStatus?>(MembershipStatus.ACTIVE)
        val refreshGate = CompletableDeferred<Unit>()
        var refreshesStarted = 0
        val provider = FirebaseActiveWorkshopProvider(
            authClaims = claims,
            scope = backgroundScope,
            membershipStatusFlow = { _, _ -> membership },
            refreshToken = { refreshesStarted++; refreshGate.await() },
        )
        assertTrue(provider.awaitHydrated().isActiveStaff)

        membership.value = MembershipStatus.REVOKED
        runCurrent()

        assertTrue(provider.current().isOwner, "demotion waited on refreshToken()")
        assertEquals("staff-1", provider.current().workshopUid)
        assertEquals(1, refreshesStarted, "the token refresh was not started")
        assertFalse(refreshGate.isCompleted, "the test gate should still hold the refresh open")

        // Releasing the refresh must not disturb the already-published demotion.
        refreshGate.complete(Unit)
        runCurrent()
        assertTrue(provider.current().isOwner)
    }

    @Test
    fun an_active_staff_claim_stays_active_before_the_first_membership_snapshot() = runTest {
        // Hydration must never wait on Firestore, and a cold cache miss must not
        // bounce an active staffer off the owner's tree — only an explicit
        // revoked flip demotes.
        val claims = MutableStateFlow<WorkshopClaims?>(staff("staff-1", "owner-9"))
        val provider = FirebaseActiveWorkshopProvider(
            authClaims = claims,
            scope = backgroundScope,
            membershipStatusFlow = { _, _ -> emptyFlow() },
        )

        val session = withTimeout(1_000) { provider.awaitHydrated() }

        assertTrue(session.isActiveStaff)
        assertEquals("owner-9", session.workshopUid)
    }

    @Test
    fun a_missing_membership_doc_does_not_demote_an_active_staff_claim() = runTest {
        val claims = MutableStateFlow<WorkshopClaims?>(staff("staff-1", "owner-9"))
        val provider = FirebaseActiveWorkshopProvider(
            authClaims = claims,
            scope = backgroundScope,
            membershipStatusFlow = { _, _ -> MutableStateFlow(null) },
        )

        assertTrue(provider.awaitHydrated().isActiveStaff)
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
    fun a_stored_uid_restores_pending_before_the_first_snapshot_arrives() = runTest {
        // Cold-start regression: with a stored workshopUid but a slow/never-arriving
        // membership snapshot, awaitHydrated() must return STAFF/PENDING (so the gate
        // restores the waiting screen), NOT the provisional owner-of-self default.
        val claims = MutableStateFlow<WorkshopClaims?>(owner("staff-1"))
        val provider = FirebaseActiveWorkshopProvider(
            authClaims = claims,
            scope = backgroundScope,
            storedWorkshopUid = MutableStateFlow("owner-9"),
            membershipStatusFlow = { _, _ -> emptyFlow() },
        )

        val session = provider.awaitHydrated()

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
