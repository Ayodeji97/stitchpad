package com.danzucker.stitchpad.core.data

import com.danzucker.stitchpad.core.data.sync.backoffDelayMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
