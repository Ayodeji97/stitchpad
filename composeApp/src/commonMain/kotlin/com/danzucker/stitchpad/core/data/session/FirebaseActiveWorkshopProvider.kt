package com.danzucker.stitchpad.core.data.session

import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.WorkshopClaims
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
import com.danzucker.stitchpad.core.domain.session.WorkshopSessionResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Watches the signed-in user's custom auth claims and publishes the resolved
 * [WorkshopSession]. Fed a [WorkshopClaims] stream sourced from the ID token —
 * because it keys off the token (not just auth-state), a claim change (e.g. an
 * owner approving a staff member, once the token refreshes) re-resolves the
 * session automatically.
 *
 * An owner has no claims → [WorkshopSessionResolver] falls through to
 * owner-of-self. A staff member's claims (role='staff', workshopUid=<owner>)
 * resolve to an active-staff session on the owner's tree. The membership-doc
 * fallback (for the pending window before the token refreshes) is wired by the
 * staff-onboarding slice; here the claim is the source of truth.
 *
 * Resets to [WorkshopSession.signedOut] on sign-out. Writes `_flow` before
 * flipping `_hydrated` so a racing [awaitHydrated] reads the real value, not the
 * default — mirroring [com.danzucker.stitchpad.core.data.entitlement.UserDocEntitlementsProvider].
 */
internal class FirebaseActiveWorkshopProvider(
    authClaims: Flow<WorkshopClaims?>,
    scope: CoroutineScope,
) : ActiveWorkshopProvider {

    private val _flow = MutableStateFlow(WorkshopSession.signedOut())
    override val flow: StateFlow<WorkshopSession> = _flow.asStateFlow()

    private val _hydrated = MutableStateFlow(false)

    init {
        scope.launch {
            authClaims
                .distinctUntilChanged()
                .collect { claims ->
                    if (claims == null) {
                        // Signed-out is a RESOLVED state — mark hydrated so
                        // awaitHydrated()/workshopUidOrNull() return null immediately
                        // instead of suspending forever. Only the pre-first-emission
                        // startup state stays unhydrated (initial `_hydrated = false`).
                        _flow.value = WorkshopSession.signedOut()
                        _hydrated.value = true
                    } else {
                        _flow.value = WorkshopSessionResolver.resolve(
                            authUid = claims.authUid,
                            claimWorkshopUid = claims.workshopUid,
                            claimRole = claims.role,
                            membershipWorkshopUid = null,
                            membershipStatus = null,
                        )
                        _hydrated.value = true
                    }
                }
        }
    }

    override fun current(): WorkshopSession = _flow.value

    override fun hasHydrated(): Boolean = _hydrated.value

    override suspend fun awaitHydrated(): WorkshopSession {
        _hydrated.first { it }
        return _flow.value
    }
}
