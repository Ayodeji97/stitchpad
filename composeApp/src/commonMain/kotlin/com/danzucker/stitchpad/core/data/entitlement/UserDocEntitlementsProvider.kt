package com.danzucker.stitchpad.core.data.entitlement

import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsCalculator
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlin.time.Clock

private const val TAG = "EntitlementsProvider"

// Last 4 chars of uid in logs — enough to disambiguate test accounts
// (Fola vs Gabby) in Xcode console / Crashlytics without spilling full
// Firebase Auth uids into log streams. Mirrors ReconcileCoordinator's
// UID_SUFFIX_LEN policy.
private const val UID_SUFFIX_LEN = 4

// Backoff between snapshot-listener retries when Firestore errors out
// (permission-denied transient, network blip, deserialization crash).
// retryWhen keeps the listener alive so a transient failure doesn't leave
// the user permanently on default entitlements until they sign out + back in.
private const val SNAPSHOT_RETRY_DELAY_MS = 5_000L

/**
 * Wire shape of the slice of `users/{uid}` we actually read for entitlements.
 *
 * Why a typed DTO instead of `Map<String, Any?>`: kotlinx.serialization on
 * Kotlin/Native has no runtime serializer for `Any?`, so calling
 * `snap.data<Map<String, Any?>>()` crashes the first time the snapshot
 * listener fires on iOS with `SerializationException: Serializer for class
 * 'Any' is not found`. Android falls back to reflection-based serializers
 * and silently worked — the bug only surfaced on a physical iOS device.
 *
 * All writers of `welcomeBonusAppliedAt` must write a `Timestamp` (not a
 * raw `Long`) for the deserialization here to succeed. See
 * [com.danzucker.stitchpad.core.debug.FreemiumDebugActions.expireWelcomeWindow]
 * for the debug-menu path.
 */
@Serializable
private data class UserEntitlementsDoc(
    val subscriptionTier: String? = null,
    val welcomeBonusAppliedAt: Timestamp? = null,
)

/**
 * Internal upstream signal so the collector can distinguish "signed out"
 * (don't mark hydrated; awaitHydrated must keep waiting) from "signed in
 * with a snapshot result" (mark hydrated atomically with `_flow` update).
 * Without this distinction, `awaitHydrated` would race the collector and
 * could resume on a stale default before [_flow] received the real value.
 */
private sealed interface SnapshotSignal {
    data object SignedOut : SnapshotSignal
    data class Loaded(val doc: UserEntitlementsDoc?) : SnapshotSignal
}

/**
 * Watches the signed-in user's Firestore document and recomputes
 * [UserEntitlements] whenever [subscriptionTier] or [welcomeBonusAppliedAt]
 * changes. Resets to FREE defaults on sign-out so one user's entitlements
 * never leak into the next user's session in the same process.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class UserDocEntitlementsProvider(
    auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val now: () -> Instant = {
        Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
    },
    private val timeZone: TimeZone = TimeZone.of("Africa/Lagos"),
    scope: CoroutineScope,
) : EntitlementsProvider {

    private val _flow = MutableStateFlow(defaultEntitlements())
    override val flow: StateFlow<UserEntitlements> = _flow.asStateFlow()

    // Tracks whether the current signed-in user's Firestore snapshot has been
    // applied. Resets to false on sign-out (uid == null) so the next sign-in
    // re-awaits a fresh snapshot. createCustomer + any other write-time cap
    // gates use [awaitHydrated] to avoid blocking a Pro/Atelier account with
    // 15+ customers on the default FREE/15 placeholder before Firestore emits.
    private val _hydrated = MutableStateFlow(false)

    /**
     * Emits Unit immediately, then once every hour. Combined with the
     * Firestore snapshot flow so that [EntitlementsCalculator.calculate]
     * is re-run with the current [now] even if no document change occurs
     * (e.g. the welcome window expiring at midnight while the app is open).
     */
    private val recomputeTicker = flow {
        while (true) {
            emit(Unit)
            delay(60 * 60 * 1000L) // 1 hour
        }
    }

    init {
        scope.launch {
            val snapshotFlow = auth.authStateChanged
                .map { it?.uid }
                .distinctUntilChanged()
                .flatMapLatest { uid ->
                    if (uid == null) {
                        flowOf<SnapshotSignal>(SnapshotSignal.SignedOut)
                    } else {
                        firestore.collection("users").document(uid).snapshots
                            .map<_, SnapshotSignal> { snap ->
                                SnapshotSignal.Loaded(
                                    if (snap.exists) snap.data<UserEntitlementsDoc>() else null
                                )
                            }
                            // retryWhen (not .catch) so the listener keeps
                            // running after a transient Firestore failure
                            // (permission-denied, network blip, deserialization
                            // crash). .catch + emit would END the inner flow
                            // and leave the user permanently on default
                            // entitlements until they sign out + sign in.
                            // The awaitHydrated timeout below is the
                            // user-facing safety net for the rare case where
                            // retries never succeed.
                            .retryWhen { error, _ ->
                                AppLogger.e(tag = TAG, throwable = error) {
                                    "user-doc snapshot failed uid=...${uid.takeLast(UID_SUFFIX_LEN)}; " +
                                        "retrying in ${SNAPSHOT_RETRY_DELAY_MS}ms"
                                }
                                delay(SNAPSHOT_RETRY_DELAY_MS)
                                true
                            }
                    }
                }

            combine(snapshotFlow, recomputeTicker) { signal, _ -> signal }
                .collectLatest { signal ->
                    // Write `_flow` BEFORE flipping `_hydrated` so a racing
                    // awaitHydrated() that resumes on the hydration flag
                    // reads the real value, not the stale default.
                    when (signal) {
                        SnapshotSignal.SignedOut -> {
                            _flow.value = defaultEntitlements()
                            _hydrated.value = false
                        }
                        is SnapshotSignal.Loaded -> {
                            if (signal.doc == null) {
                                // Signed in but user doc missing — typically:
                                //   (a) fresh signup race before createUserProfile lands
                                //   (b) accidental doc deletion / admin sweep
                                // Do NOT mark hydrated. The real entitlements may
                                // arrive shortly with a non-trivial cap (First Month
                                // 200, Pro ∞), and a cap gate that proceeds with the
                                // FREE/15 default could falsely block a Pro account
                                // or miss the First Month customer cap. The snapshot
                                // listener stays alive; once the doc lands, the next
                                // Loaded emission flips hydrated and replaces _flow.
                                _flow.value = defaultEntitlements()
                                _hydrated.value = false
                            } else {
                                _flow.value = computeFromData(signal.doc)
                                _hydrated.value = true
                            }
                        }
                    }
                }
        }
    }

    private fun computeFromData(data: UserEntitlementsDoc): UserEntitlements {
        val seededAt = data.welcomeBonusAppliedAt?.let {
            Instant.fromEpochMilliseconds(it.toMilliseconds().toLong())
        }
        return EntitlementsCalculator.calculate(
            tier = SubscriptionTier.fromWire(data.subscriptionTier),
            welcomeBonusAppliedAt = seededAt,
            now = now(),
            timeZone = timeZone,
        )
    }

    override fun current(): UserEntitlements = _flow.value

    override fun hasHydrated(): Boolean = _hydrated.value

    override suspend fun awaitHydrated(): UserEntitlements {
        // No timeout: a wall-clock cutoff would return the default FREE/15 on
        // a merely slow snapshot, falsely blocking Pro/Atelier users with
        // 15+ customers on poor networks — the exact bug awaitHydrated exists
        // to prevent. retryWhen above keeps the snapshot listener alive
        // through transient failures, so callers always converge eventually.
        // Permanent failures (rules deny, account doc missing) leave the
        // caller's UI in a spinner — honest signal that the gate can't be
        // evaluated, vs silently letting the user proceed against stale data.
        _hydrated.first { it }
        return _flow.value
    }

    private fun defaultEntitlements() = EntitlementsCalculator.calculate(
        tier = SubscriptionTier.FREE,
        welcomeBonusAppliedAt = null,
        now = now(),
        timeZone = timeZone,
    )
}
