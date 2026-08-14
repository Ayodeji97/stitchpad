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
}
