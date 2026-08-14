package com.danzucker.stitchpad.core.data

import com.danzucker.stitchpad.core.data.sync.backoffDelayMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
 * This exists because `.catch { emit(fallback) }` TERMINATES the flow: one
 * transient permission-denied during a workshop session flip would freeze the
 * screen's live data for the rest of the ViewModel's life (documented
 * independently at four call sites before this was extracted; generalized from
 * the SyncStatus-typed original in SyncStatusMapper).
 *
 * A permanently-failing listener retries forever, and [onError] fires on every
 * single attempt (desired — that is the diagnosability signal), receiving the
 * RUN-LOCAL attempt number (0-based, reset to 0 after each successful upstream
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
    onError: (cause: Throwable, attempt: Long) -> Unit,
): Flow<T> = flow {
    // Per-collection state: a failure run's backoff must restart at
    // initialBackoffMs once the upstream has recovered, not resume from the
    // lifetime error count — a listener that hiccuped at breakfast shouldn't
    // pay a 60s recovery penalty for an unrelated hiccup at dinner. Declared
    // inside this flow{} builder so each collector gets its own instance;
    // retryWhen's `attempt` counter, by contrast, is cumulative for the
    // collection's lifetime and is deliberately not used here.
    var errorsSinceLastEmission = 0L
    this@retryWithFallback
        .onEach { errorsSinceLastEmission = 0L }
        .retryWhen { cause, _ ->
            onError(cause, errorsSinceLastEmission)
            emit(fallback)
            delay(backoffDelayMs(errorsSinceLastEmission, initialBackoffMs, maxBackoffMs))
            errorsSinceLastEmission += 1
            true
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
