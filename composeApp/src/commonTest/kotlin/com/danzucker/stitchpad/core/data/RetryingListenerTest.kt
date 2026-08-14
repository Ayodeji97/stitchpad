package com.danzucker.stitchpad.core.data

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RetryingListenerTest {

    @Test
    fun emitsFallbackOnFailureThenResubscribes() = runTest {
        var subscriptions = 0
        val upstream = flow {
            subscriptions += 1
            if (subscriptions == 1) throw IllegalStateException("transient permission-denied")
            emit(7)
        }
        val errors = mutableListOf<Throwable>()

        val collected = upstream
            .retryWithFallback(fallback = -1, initialBackoffMs = 1, maxBackoffMs = 1) { cause, _ ->
                errors += cause
            }
            .take(2)
            .toList()

        // The terminating-catch bug this replaces: one transient error must NOT
        // end the flow — it emits the fallback, then recovers with live data.
        assertEquals(listOf(-1, 7), collected)
        assertEquals(1, errors.size)
        assertEquals(2, subscriptions)
    }

    @Test
    fun repeatedFailuresEmitTheFallbackOnlyOnceBeforeRecovering() = runTest {
        var subscriptions = 0
        val consecutiveFailures = 3
        val upstream = flow {
            subscriptions += 1
            if (subscriptions <= consecutiveFailures) {
                throw IllegalStateException("still down, attempt $subscriptions")
            }
            emit(7)
        }
        val errors = mutableListOf<Throwable>()

        val collected = upstream
            .retryWithFallback(fallback = -1, initialBackoffMs = 1, maxBackoffMs = 1) { cause, _ ->
                errors += cause
            }
            .take(2)
            .toList()

        // A permanently (or long) failing listener retries forever and reports through
        // onError on EVERY attempt — that's the diagnosability signal, unchanged — but
        // collectors (e.g. an error-snackbar screen) must see the fallback only ONCE per
        // failure run, not once per retry, or a stuck listener spams the snackbar forever.
        assertEquals(listOf(-1, 7), collected)
        assertEquals(consecutiveFailures, errors.size)
        assertEquals(consecutiveFailures + 1, subscriptions)
    }

    @Test
    fun backoffResetsToInitialAfterARecoveredFailureRun() = runTest {
        var subscriptions = 0
        // Failure run 1: three consecutive failures (retry attempts 0, 1, 2).
        // Subscription 4 recovers with a real emission, THEN fails again within
        // the same subscription (no resubscribe boundary needed) — that failure
        // must be treated as attempt 0 of a NEW failure run, not attempt 3 of
        // the same one. Subscription 5 recovers again.
        val upstream = flow {
            subscriptions += 1
            when (subscriptions) {
                1, 2, 3 -> throw IllegalStateException("failure run 1, subscription $subscriptions")
                4 -> {
                    emit(7)
                    throw IllegalStateException("failure run 2, subscription $subscriptions")
                }
                else -> emit(8)
            }
        }
        val attempts = mutableListOf<Long>()

        val collected = upstream
            .retryWithFallback(fallback = -1, initialBackoffMs = 100, maxBackoffMs = 100_000) { _, attempt ->
                attempts += attempt
            }
            .take(4)
            .toList()

        // Bug this guards against: if the backoff counter were cumulative for the
        // collection's lifetime (retryWhen's own `attempt`) instead of per-failure-run,
        // failure run 2's only retry would be attempt 3 (800ms delay) instead of attempt
        // 0 (100ms delay) — a listener that hiccuped hours after an earlier hiccup would
        // wrongly start at a much longer backoff.
        assertEquals(listOf(-1, 7, -1, 8), collected)
        assertEquals(listOf(0L, 1L, 2L, 0L), attempts)

        // Virtual-time cross-check: delays are 100 + 200 + 400ms for failure run 1's
        // three attempts, then 100ms (INITIAL, not 800ms) for failure run 2's single
        // attempt before subscription 5 recovers with 8 — total 800ms. With the bug,
        // the last delay would be 800ms instead, totalling 1500ms.
        assertEquals(800L, testScheduler.currentTime)
    }
}
