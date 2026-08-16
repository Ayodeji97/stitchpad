package com.danzucker.stitchpad.core.data.session

import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.session.WorkshopClaims
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
import com.danzucker.stitchpad.core.domain.session.WorkshopSessionResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Watches the signed-in user's custom auth claims and publishes the resolved
 * [WorkshopSession]. Fed a [WorkshopClaims] stream sourced from the ID token —
 * because it keys off the token (not just auth-state), a claim change (e.g. an
 * owner approving a staff member, once the token refreshes) re-resolves the
 * session automatically.
 *
 * Two resolution paths, in precedence order:
 *  1. **Claim** (server-authoritative): a staff token (role='staff',
 *     workshopUid=<owner>) resolves to an active-staff session on the owner's
 *     tree. An owner has no claims → falls through.
 *  2. **Membership-doc fallback** for the *pending window* — after a staffer
 *     redeems an invite but before approval mints a claim, there is no claim to
 *     read. Using the workshopUid recorded at redeem time ([storedWorkshopUid]),
 *     the provider watches that membership doc ([membershipStatusFlow]) so the
 *     session reflects PENDING → ACTIVE the moment the owner approves. On ACTIVE
 *     it calls [refreshToken] to force the claim onto the token, after which
 *     path (1) becomes the authority. No stored workshopUid → owner-of-self.
 *
 * Resets to [WorkshopSession.signedOut] on sign-out. Writes `_flow` before
 * flipping `_hydrated` so a racing [awaitHydrated] reads the real value, not the
 * default — mirroring [com.danzucker.stitchpad.core.data.entitlement.UserDocEntitlementsProvider].
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FirebaseActiveWorkshopProvider(
    authClaims: Flow<WorkshopClaims?>,
    scope: CoroutineScope,
    storedWorkshopUid: Flow<String?> = flowOf(null),
    // Remote kill-switch (config/app.staffFeatureEnabled). Default true (fail-open):
    // when false, every user resolves to owner-of-self so the staff experience is
    // disabled instantly without a release. See [WorkshopSessionResolver].
    staffFeatureEnabled: Flow<Boolean> = flowOf(true),
    private val membershipStatusFlow: (workshopUid: String, authUid: String) -> Flow<MembershipStatus?> =
        { _, _ -> flowOf(null) },
    private val refreshToken: suspend () -> Unit = {},
    // Fired when a revoked membership doc is observed (active session or pending
    // window). Wired to clearing the redeem-time prefs so the stale workshopUid
    // cannot re-enter the pending window after demotion.
    private val onStaffRevoked: suspend () -> Unit = {},
) : ActiveWorkshopProvider {

    private val _flow = MutableStateFlow(WorkshopSession.signedOut())
    override val flow: StateFlow<WorkshopSession> = _flow.asStateFlow()

    private val _hydrated = MutableStateFlow(false)

    init {
        scope.launch {
            // Combine (not just map over authClaims): the stored workshopUid is
            // written at redeem time WITHOUT any auth-token change, so the
            // provider must observe it reactively — otherwise it would only enter
            // the pending window on the next idTokenChanged (i.e. app restart).
            combine(
                authClaims.distinctUntilChanged(),
                storedWorkshopUid.distinctUntilChanged(),
                staffFeatureEnabled.distinctUntilChanged(),
            ) { claims, storedWs, staffEnabled -> Triple(claims, storedWs, staffEnabled) }
                // A claim-backed active-staff session (resolvedFlow's path (1))
                // never reads storedWs, so a storedWs-only change must not
                // restart flatMapLatest while that path is active. Without this,
                // onStaffRevoked() clearing the SAME prefs storedWorkshopUid
                // observes (production wiring: onStaffRevoked = {
                // membershipPrefs.clear() }) races activeStaffFlow's
                // refreshToken(): flatMapLatest cancels the in-flight demotion
                // mid-suspension, re-subscribes with the still-stale STAFF
                // claim, and activeStaffFlow's onStart re-asserts the active
                // session — discarding the demotion (regression covered by
                // revoking_mid_session_demotes_without_racing_the_prefs_clear_flow_restart).
                .distinctUntilChangedBy { (claims, storedWs, staffEnabled) ->
                    val storedWsKey = if (claims?.role == WorkshopSessionResolver.CLAIM_ROLE_STAFF) null else storedWs
                    Triple(claims, storedWsKey, staffEnabled)
                }
                .flatMapLatest { (claims, storedWs, staffEnabled) ->
                    sessionFlow(claims, storedWs, staffEnabled)
                }
                .collect { session ->
                    // Write `_flow` before flipping `_hydrated` so a racing
                    // awaitHydrated() reads the real value, not the default.
                    _flow.value = session
                    _hydrated.value = true
                }
        }
    }

    @Suppress("ReturnCount") // three fast-exits: signed-out, kill-switch, resolved.
    private fun sessionFlow(
        claims: WorkshopClaims?,
        storedWs: String?,
        staffEnabled: Boolean,
    ): Flow<WorkshopSession> {
        // Signed-out is a RESOLVED state — emitting it marks hydrated so
        // awaitHydrated()/workshopUidOrNull() return immediately instead of
        // suspending forever.
        if (claims == null) return flowOf(WorkshopSession.signedOut())
        // Remote kill-switch: with staff disabled in config/app, resolve to
        // owner-of-self for everyone (a staff member falls back to their own empty
        // tree). Short-circuits ALL resolution paths, incl. the pending-window flow.
        if (!staffEnabled) return flowOf(WorkshopSession.ownerOfSelf(claims.authUid))
        return resolvedFlow(claims, storedWs)
    }

    private fun resolvedFlow(claims: WorkshopClaims, storedWs: String?): Flow<WorkshopSession> {
        // (1) Server-authoritative staff claim → active staff, but keep watching
        // the membership doc for the session's whole life — see [activeStaffFlow].
        val fromClaim = resolve(claims, membershipStatus = null)
        if (fromClaim.role == StaffRole.STAFF) return activeStaffFlow(claims, fromClaim)

        // (2) No staff claim: a real owner, or a staff member in the pending
        // window whose claim has not been minted yet. Only the latter recorded a
        // workshopUid at redeem time — watch that membership doc to drive the
        // pending→active transition. Guard against a stale self-uid.
        return if (storedWs == null || storedWs == claims.authUid) {
            flowOf(WorkshopSession.ownerOfSelf(claims.authUid))
        } else {
            pendingWindowFlow(claims, storedWs)
        }
    }

    /**
     * Watches the staff member's own membership doc for the whole life of a
     * claim-backed active staff session. Revocation clears the claim server-side
     * but cannot touch the already-minted token — the doc flip to `revoked` is
     * the ONLY signal the client gets before the hourly token refresh, and the
     * rules deny every owner-tree read the moment it flips. Without this watch
     * each listener enters a permission-denied retry loop (UI flashing between
     * stale cached data and the error fallback) until the token expires.
     *
     * On an observed revocation: demote to owner-of-self immediately (the
     * resolver treats a REVOKED doc as overriding the claim), clear the
     * redeem-time prefs via [onStaffRevoked], and force a token refresh so the
     * stale claim is dropped. A null status (doc unread or missing — e.g. a cold
     * cache miss) never demotes; only an explicit revoked flip does.
     */
    private fun activeStaffFlow(claims: WorkshopClaims, fromClaim: WorkshopSession): Flow<WorkshopSession> =
        membershipStatusFlow(fromClaim.workshopUid, claims.authUid)
            // Listener resubscribes re-emit the same status; without dedup each
            // re-emission would re-fire the refresh below.
            .distinctUntilChanged()
            .onEach { status ->
                if (status == MembershipStatus.REVOKED) {
                    // Prefs first: once the token refreshes claimless, a still-
                    // stored workshopUid would re-enter the pending window.
                    onStaffRevoked()
                    refreshToken()
                }
            }
            .map { status -> resolve(claims, membershipStatus = status) }
            // The claim alone is authoritative while the first snapshot is in
            // flight — emit immediately so hydration never waits on Firestore.
            .onStart { emit(fromClaim) }

    /**
     * Watches the staffer's membership doc during the pending window and, once
     * the doc flips to active, forces a token refresh so the claim path takes
     * over. Crucially it does NOT itself switch to the owner's tree from the doc
     * — [WorkshopSessionResolver] holds an approved-but-claimless staffer on their
     * own tree, so no owner read is attempted before the claim (which the rules
     * require) is actually on the token.
     */
    private fun pendingWindowFlow(claims: WorkshopClaims, storedWs: String): Flow<WorkshopSession> =
        membershipStatusFlow(storedWs, claims.authUid)
            .onEach { status ->
                // Approved on the server: force a token refresh so idTokenChanged
                // re-emits with the staff claim and the claim path promotes to the
                // owner's tree. approveStaffMember sets the claim BEFORE flipping
                // the doc to active, so a refresh triggered here reliably returns
                // the claim (no denied-read window, no refresh loop).
                if (status == MembershipStatus.ACTIVE) refreshToken()
                // Revoked (or declined) while pending/claimless: clear the stored
                // workshopUid so the dead pending window is not re-entered on
                // every later claims emission (each re-entry flashes a transient
                // provisional PENDING through the session flow).
                if (status == MembershipStatus.REVOKED) onStaffRevoked()
            }
            .map { status -> resolve(claims, membershipStatus = status) }
            // Emit a provisional STAFF/PENDING (on the user's OWN tree) first, so
            // awaitHydrated() never hangs on the first Firestore snapshot AND a
            // cold-starting pending staffer is restored to the waiting screen rather
            // than the provisional owner-of-self default. We're only in this flow
            // because a workshopUid was stored at redeem time, so pending is the
            // correct assumption until the snapshot confirms active/revoked.
            .onStart {
                emit(WorkshopSession(claims.authUid, claims.authUid, StaffRole.STAFF, MembershipStatus.PENDING))
            }

    private fun resolve(
        claims: WorkshopClaims,
        membershipStatus: MembershipStatus?,
    ): WorkshopSession = WorkshopSessionResolver.resolve(
        authUid = claims.authUid,
        claimWorkshopUid = claims.workshopUid,
        claimRole = claims.role,
        membershipStatus = membershipStatus,
    )

    override fun current(): WorkshopSession = _flow.value

    override fun hasHydrated(): Boolean = _hydrated.value

    override suspend fun awaitHydrated(): WorkshopSession {
        _hydrated.first { it }
        return _flow.value
    }
}
