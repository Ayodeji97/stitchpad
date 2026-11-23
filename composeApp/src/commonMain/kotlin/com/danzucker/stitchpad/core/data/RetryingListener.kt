package com.danzucker.stitchpad.core.data

import com.danzucker.stitchpad.core.data.sync.backoffDelayMs
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.code
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen

const val LISTENER_RETRY_INITIAL_BACKOFF_MS = 500L
const val LISTENER_RETRY_MAX_BACKOFF_MS = 60_000L

/**
 * Keeps a listener-backed flow alive across upstream failures: on error it
 * reports through [onError], emits [fallback] so the UI stops asserting
 * anything it can no longer verify, waits with capped exponential backoff,
 * then resubscribes — indefinitely.
 *
 * By default all upstream failures retry, including transient permission denial
 * during staff claim refresh. Owner-only listeners opt into a terminal policy
 * through [shouldRetry]; their role-aware gate starts a fresh authorized collection.
 *
 * Other persistent failures retry indefinitely, and [onError] fires on every
 * single attempt (desired — that is the diagnosability signal), receiving the
 * RUN-LOCAL attempt number (0-based, reset to 0 after each changed upstream
 * emission) — the count of consecutive failures since the upstream last
 * produced real data, which is what "retrying (attempt N+1)"-style logging
 * actually intends. Without deduping the downstream emission too, a screen
 * driving an error snackbar off this flow would re-show it on every retry
 * cycle: several times in the first few seconds, then once per [maxBackoffMs]
 * forever. Consecutive [fallback] emissions are therefore collapsed to one —
 * only the FIRST fallback in a failure run reaches collectors; a real data
 * emission passes unless it is consecutive-equal to the fallback value (an
 * idempotent case: the collector already holds that exact value), and a fresh
 * failure run after a recovery emits the fallback again — with backoff
 * restarting at [initialBackoffMs], since it's tracked per failure run, not
 * for the collection's whole lifetime.
 */
fun <T> Flow<T>.retryWithFallback(
    fallback: T,
    initialBackoffMs: Long = LISTENER_RETRY_INITIAL_BACKOFF_MS,
    maxBackoffMs: Long = LISTENER_RETRY_MAX_BACKOFF_MS,
    shouldRetry: (Throwable) -> Boolean = { true },
    onError: (cause: Throwable, attempt: Long) -> Unit,
): Flow<T> = flow {
    // Per-collection state: a failure run's backoff must restart at
    // initialBackoffMs once the upstream produces fresh data, not resume from the
    // lifetime error count — a listener that hiccuped at breakfast shouldn't
    // pay a 60s recovery penalty for an unrelated hiccup at dinner. Declared
    // inside this flow{} builder so each collector gets its own instance;
    // retryWhen's `attempt` counter, by contrast, is cumulative for the
    // collection's lifetime and is deliberately not used here.
    var errorsSinceLastEmission = 0L
    var hasUpstreamValue = false
    var lastUpstreamValue: T? = null
    this@retryWithFallback
        .onEach { value ->
            // Firestore can replay the same cached snapshot on every subscription.
            // That replay is not recovery and must not reset exponential backoff.
            if (!hasUpstreamValue || value != lastUpstreamValue) errorsSinceLastEmission = 0L
            hasUpstreamValue = true
            lastUpstreamValue = value
        }
        .retryWhen { cause, _ ->
            if (!shouldRetry(cause)) {
                return@retryWhen false
            }
            onError(cause, errorsSinceLastEmission)
            emit(fallback)
            delay(backoffDelayMs(errorsSinceLastEmission, initialBackoffMs, maxBackoffMs))
            errorsSinceLastEmission += 1
            true
        }
        .catch { cause ->
            onError(cause, errorsSinceLastEmission)
            emit(fallback)
        }
        // A permanently-failing listener retries forever; without this, every retry
        // re-emits the fallback and error-snackbar screens re-show it on each cycle.
        // Suppressing only CONSECUTIVE emissions equal to the fallback keeps one
        // error per failure run while every data emission still passes. (A data
        // emission that legitimately equals the fallback — e.g. emptyMap() — is
        // idempotent state for all consumers, so collapsing consecutive duplicates
        // of it is harmless.)
        .distinctUntilChanged { old, new -> old == new && new == fallback }
        .collect { value -> emit(value) }
}

/** Only for hydrated owner-private listeners; shared base listeners must keep retrying. */
internal fun retryUnlessPermissionDenied(cause: Throwable): Boolean =
    cause !is FirebaseFirestoreException || retryableFirestoreCode(cause.code)

/** Owner-private permission denial needs a new authorized session, not a timed retry. */
internal fun retryableFirestoreCode(code: FirestoreExceptionCode): Boolean =
    code != FirestoreExceptionCode.PERMISSION_DENIED
