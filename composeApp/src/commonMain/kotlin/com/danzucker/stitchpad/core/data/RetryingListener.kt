package com.danzucker.stitchpad.core.data

import com.danzucker.stitchpad.core.data.sync.backoffDelayMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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
 * single attempt (desired — that is the diagnosability signal). Without
 * deduping the downstream emission too, a screen driving an error snackbar off
 * this flow would re-show it on every retry cycle: several times in the first
 * few seconds, then once per [maxBackoffMs] forever. Consecutive [fallback]
 * emissions are therefore collapsed to one — only the FIRST fallback in a
 * failure run reaches collectors; a subsequent real data emission (even one
 * that happens to equal [fallback], e.g. `emptyMap()`) always passes, and a
 * fresh failure run after a recovery emits the fallback again.
 */
fun <T> Flow<T>.retryWithFallback(
    fallback: T,
    initialBackoffMs: Long = LISTENER_RETRY_INITIAL_BACKOFF_MS,
    maxBackoffMs: Long = LISTENER_RETRY_MAX_BACKOFF_MS,
    onError: (cause: Throwable, attempt: Long) -> Unit,
): Flow<T> = retryWhen { cause, attempt ->
    onError(cause, attempt)
    emit(fallback)
    delay(backoffDelayMs(attempt, initialBackoffMs, maxBackoffMs))
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
