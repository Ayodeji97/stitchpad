package com.danzucker.stitchpad.feature.freemium.presentation.reconcile

import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.freemium.domain.FreemiumRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val TAG = "ReconcileCoordinator"

// Last 4 chars of uid in logs — enough to disambiguate test accounts
// (Fola vs Gabby) without spilling full Firebase Auth uids into log streams.
private const val UID_SUFFIX_LEN = 4

/**
 * Owns the foreground-triggered slot-reconcile lifecycle that previously lived as
 * a raw `LaunchedEffect` in `App.kt`. Promoted to a real DI singleton so:
 *
 * 1. **Failures are observable.** The old call site swallowed any error from
 *    [FreemiumRepository.reconcileSlots] with a "best-effort" comment, so a
 *    failing reconcile (network, permission, transient cloud-function timeout)
 *    left the user with stale slot state and no signal anywhere. This
 *    coordinator logs structured [Result.Error] outcomes via [AppLogger.e],
 *    which a future Crashlytics antilog (planned post-V1.0) will surface on a
 *    dashboard.
 *
 * 2. **Reconciles are deduplicated client-side.** Composition can re-emit the
 *    same `(uid, tier, isInWelcomeWindow)` tuple multiple times during a
 *    single foreground session — the old `LaunchedEffect(uid, tier,
 *    isInWelcomeWindow)` could re-fire reconcile on every recomposition that
 *    flipped any of those keys even when the resulting reconcile would be a
 *    no-op server-side. We track the last-fired key here and skip if it
 *    hasn't moved. A follow-up server PR adds its own `lastReconciledAt`
 *    dedup; this client-side layer is a cheap first line of defense that
 *    saves a function invocation per duplicate trigger.
 *
 * 3. **The trigger is testable.** App.kt's LaunchedEffect was unreachable from
 *    unit tests; this coordinator can be exercised with a fake
 *    FreemiumRepository and a hand-rolled EntitlementsProvider flow.
 *
 * The "lazy" framing in the V1.0 design spec (decision #4) is preserved — the
 * coordinator still only reconciles when an auth or entitlements change is
 * observed in the foreground. There is no scheduled cron. Users who never
 * open the app on the day their welcome window expires don't trigger
 * reconcile until they return — by design, so the cap shrink happens in
 * front of them, not while they sleep.
 *
 * Lifetime: instantiated lazily by Koin at first injection and kept for the
 * app process. The collector launched in [start] runs on the supplied
 * [scope] (a SupervisorJob-backed CoroutineScope) and only ends with the
 * process — there is no `stop()` because the trigger conditions only fire on
 * auth state, which is itself process-scoped.
 */
class ReconcileCoordinator(
    private val uidFlow: Flow<String?>,
    private val entitlementsProvider: EntitlementsProvider,
    private val freemiumRepository: FreemiumRepository,
    scope: CoroutineScope,
) {
    /** Dedup key: (uid, tier, isInWelcomeWindow). Null until first emission. */
    private var lastFiredKey: ReconcileKey? = null

    init {
        scope.launch {
            combine(uidFlow, entitlementsProvider.flow) { uid, entitlements ->
                ReconcileKey.of(uid, entitlements)
            }
                .distinctUntilChanged()
                .collect { key -> maybeReconcile(key) }
        }
    }

    /**
     * No-op entrypoint for `App.kt` to ensure this coordinator is instantiated
     * by Koin on first composition. Without a referenced method, an unused
     * `koinInject<ReconcileCoordinator>()` could be lint-removed or never
     * actually realized depending on Compose's lazy-eval semantics.
     */
    fun ensureRunning() = Unit

    private suspend fun maybeReconcile(key: ReconcileKey) {
        // No user signed in → nothing to reconcile. Reset the dedup key so
        // a fresh sign-in always triggers a reconcile even if the new user
        // happens to land on the same (tier, isInWelcomeWindow) tuple.
        if (key.uid == null) {
            lastFiredKey = null
            return
        }
        if (key == lastFiredKey) return
        lastFiredKey = key

        when (val result = freemiumRepository.reconcileSlots()) {
            is Result.Success -> {
                AppLogger.d(tag = TAG) {
                    "reconcileSlots ok uid=${key.uid.takeLast(UID_SUFFIX_LEN)} " +
                        "tier=${key.tier} welcome=${key.isInWelcomeWindow}"
                }
            }
            is Result.Error -> {
                AppLogger.e(tag = TAG) {
                    "reconcileSlots failed uid=${key.uid.takeLast(UID_SUFFIX_LEN)} " +
                        "tier=${key.tier} welcome=${key.isInWelcomeWindow} error=${result.error}"
                }
            }
        }
    }

    private data class ReconcileKey(
        val uid: String?,
        val tier: SubscriptionTier,
        val isInWelcomeWindow: Boolean,
    ) {
        companion object {
            fun of(uid: String?, entitlements: UserEntitlements): ReconcileKey = ReconcileKey(
                uid = uid,
                tier = entitlements.tier,
                isInWelcomeWindow = entitlements.isInWelcomeWindow,
            )
        }
    }
}
