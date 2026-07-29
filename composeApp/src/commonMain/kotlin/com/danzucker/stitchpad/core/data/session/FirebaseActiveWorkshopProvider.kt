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
    private val membershipStatusFlow: (workshopUid: String, authUid: String) -> Flow<MembershipStatus?> =
        { _, _ -> flowOf(null) },
    private val refreshToken: suspend () -> Unit = {},
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
            ) { claims, storedWs -> claims to storedWs }
                .flatMapLatest { (claims, storedWs) -> sessionFlow(claims, storedWs) }
                .collect { session ->
                    // Write `_flow` before flipping `_hydrated` so a racing
                    // awaitHydrated() reads the real value, not the default.
                    _flow.value = session
                    _hydrated.value = true
                }
        }
    }

    private fun sessionFlow(claims: WorkshopClaims?, storedWs: String?): Flow<WorkshopSession> {
        // Signed-out is a RESOLVED state — emitting it marks hydrated so
        // awaitHydrated()/workshopUidOrNull() return immediately instead of
        // suspending forever.
        if (claims == null) return flowOf(WorkshopSession.signedOut())
        return resolvedFlow(claims, storedWs)
    }

    private fun resolvedFlow(claims: WorkshopClaims, storedWs: String?): Flow<WorkshopSession> {
        // (1) Server-authoritative staff claim → active staff; no doc read needed.
        val fromClaim = resolve(claims, membershipStatus = null)
        if (fromClaim.role == StaffRole.STAFF) return flowOf(fromClaim)

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
            }
            .map { status -> resolve(claims, membershipStatus = status) }
            // Emit the fail-safe default first so awaitHydrated() never hangs
            // waiting for the first Firestore snapshot.
            .onStart { emit(WorkshopSession.ownerOfSelf(claims.authUid)) }

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
