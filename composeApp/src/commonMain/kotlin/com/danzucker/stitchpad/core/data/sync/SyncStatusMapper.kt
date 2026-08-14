package com.danzucker.stitchpad.core.data.sync

import com.danzucker.stitchpad.core.domain.model.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest

/**
 * Pure projection of Firestore snapshot metadata onto [SyncStatus].
 *
 * Kept free of Firestore types so it can be unit-tested directly; the Firestore
 * plumbing lives in [SyncStatusObserver].
 *
 * `isFromCache` wins over `hasPendingWrites`: if we are not reaching the server at
 * all, "Offline" is the more useful thing to say than "Syncing".
 */
fun syncStatusOf(isFromCache: Boolean, hasPendingWrites: Boolean): SyncStatus = when {
    isFromCache -> SyncStatus.OFFLINE
    hasPendingWrites -> SyncStatus.SYNCING
    else -> SyncStatus.SYNCED
}

/**
 * Delays transitions INTO [SyncStatus.OFFLINE] by [delayMs], passing every other
 * status through immediately.
 *
 * The first snapshot after launch is always served from cache while the server
 * round-trip is still in flight, so without this the banner would flash on every
 * cold start. `transformLatest` cancels the pending delay the moment a newer status
 * arrives, so a healthy connection suppresses the blip while a genuine outage still
 * surfaces once the delay elapses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<SyncStatus>.debounceOffline(delayMs: Long): Flow<SyncStatus> =
    transformLatest { status ->
        if (status == SyncStatus.OFFLINE) {
            delay(delayMs)
        }
        emit(status)
    }

/**
 * Exponential backoff for retry [attempt] (0-based), starting at [initialMs] and
 * capped at [maxMs].
 *
 * The cap matters as much as the growth: this operator retries forever, so an
 * unbounded curve would drift into effectively-never territory after a handful of
 * failures.
 */
fun backoffDelayMs(attempt: Long, initialMs: Long, maxMs: Long): Long {
    val shift = attempt.coerceIn(0, MAX_BACKOFF_SHIFT).toInt()
    val grown = initialMs shl shift
    return if (grown <= 0L) maxMs else grown.coerceAtMost(maxMs)
}

private const val MAX_BACKOFF_SHIFT = 20L
