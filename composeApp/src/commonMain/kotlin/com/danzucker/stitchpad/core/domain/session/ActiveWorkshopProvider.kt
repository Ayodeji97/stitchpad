package com.danzucker.stitchpad.core.domain.session

import kotlinx.coroutines.flow.StateFlow

/**
 * Read-side handle for the current [WorkshopSession] — the single place the app
 * asks "whose data tree am I working in, and as what role?".
 *
 * Modelled on [com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider]:
 * implementations hot-cache the resolved session, re-resolve on auth changes, and
 * reset on sign-out. Before the first resolution lands, [current] returns the
 * fail-safe owner-of-self default (see [WorkshopSession.ownerOfSelf]); any path
 * that must not act on a pre-resolution guess (e.g. a data write) uses [awaitHydrated].
 */
interface ActiveWorkshopProvider {
    /**
     * Fast synchronous read of the last resolved session. WARNING: may return the
     * owner-of-self default before the first resolution; use [awaitHydrated] where
     * acting on that default would be wrong.
     */
    fun current(): WorkshopSession

    /** Hot flow for reactive observers (nav gate, role-aware shell). */
    val flow: StateFlow<WorkshopSession>

    /** True once the signed-in user's session has been resolved. */
    fun hasHydrated(): Boolean = true

    /**
     * Suspends until the current signed-in user's session has been resolved, then
     * returns it. Use in any gate where the owner-of-self default could produce a
     * wrong answer (targeting the wrong data tree).
     */
    suspend fun awaitHydrated(): WorkshopSession
}

/**
 * The workshop tree id to read/write for the signed-in user, or `null` when
 * signed out. Suspends until the session resolves (see [ActiveWorkshopProvider.awaitHydrated]).
 *
 * Drop-in replacement for the old `authRepository.getCurrentUser()?.id` at
 * data-scoping call sites: for an owner it is their own uid; for active staff it
 * is the owner's uid, so the same repository call now targets the right tree.
 */
suspend fun ActiveWorkshopProvider.workshopUidOrNull(): String? =
    awaitHydrated().workshopUid.takeIf { it.isNotBlank() }
