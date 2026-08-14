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
}
