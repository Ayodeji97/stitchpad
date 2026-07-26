package com.danzucker.stitchpad.core.data.session

import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
import com.danzucker.stitchpad.core.domain.session.WorkshopSessionResolver
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Watches Firebase Auth state and publishes the resolved [WorkshopSession].
 *
 * **Slice 0 scope:** custom claims and membership documents are not yet written
 * anywhere, so every signed-in user resolves to owner-of-self
 * ([WorkshopSession.ownerOfSelf]) — i.e. `workshopUid == authUid` — making this a
 * behaviour-neutral addition. A later slice extends the signed-in branch to read
 * the `workshopUid`/`role` custom claims (and the membership-doc fallback) and
 * feed them to [WorkshopSessionResolver]; the resolver, its precedence rules, and
 * the fail-safe default are already in place and unit-tested.
 *
 * Resets to [WorkshopSession.signedOut] on sign-out so one user's session never
 * leaks into the next in the same process. Writes `_flow` before flipping
 * `_hydrated` so a racing [awaitHydrated] reads the real value, not the default —
 * mirroring [com.danzucker.stitchpad.core.data.entitlement.UserDocEntitlementsProvider].
 */
internal class FirebaseActiveWorkshopProvider(
    auth: FirebaseAuth,
    scope: CoroutineScope,
) : ActiveWorkshopProvider {

    private val _flow = MutableStateFlow(WorkshopSession.signedOut())
    override val flow: StateFlow<WorkshopSession> = _flow.asStateFlow()

    private val _hydrated = MutableStateFlow(false)

    init {
        scope.launch {
            auth.authStateChanged
                .map { it?.uid }
                .distinctUntilChanged()
                .collect { uid ->
                    if (uid == null) {
                        _flow.value = WorkshopSession.signedOut()
                        _hydrated.value = false
                    } else {
                        _flow.value = WorkshopSessionResolver.resolve(
                            authUid = uid,
                            claimWorkshopUid = null,
                            claimRole = null,
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
