package com.danzucker.stitchpad.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class PermissionDeniedListenerTest {
    // The Android SDK exception constructor needs Android's TextUtils. Use a
    // coded listener failure here and the same code-name policy as the SDK adapter.
    private class ListenerFailure(val code: String) : Exception()

    private fun shouldRetry(cause: Throwable) = retryableFirestoreCode((cause as ListenerFailure).code)

    @Test
    fun sharedBaseListenerRecoversFromTransientPermissionDenialWithSameSubscriber() = runTest {
        var subscriptions = 0
        val errors = mutableListOf<Throwable>()
        val shared = flow {
            subscriptions++
            if (subscriptions == 1) throw ListenerFailure("PERMISSION_DENIED")
            emit(7)
            emit(8)
        }.retryWithFallback(fallback = -1, initialBackoffMs = 10) { cause, _ -> errors += cause }
            .shareIn(backgroundScope, SharingStarted.WhileSubscribed(), replay = 1)

        assertEquals(listOf(-1, 7, 8), shared.take(3).toList())
        assertEquals(2, subscriptions)
        assertEquals("PERMISSION_DENIED", (errors.single() as ListenerFailure).code)
        assertEquals(10L, testScheduler.currentTime)
    }

    @Test
    fun deniedInnerListenerDoesNotRestartThroughAnOuterRetry() = runTest {
        var subscriptions = 0
        val values = flow<Int> {
            subscriptions++
            throw ListenerFailure("PERMISSION_DENIED")
        }.retryWithFallback(fallback = -1, shouldRetry = ::shouldRetry) { cause, _ ->
            assertEquals("PERMISSION_DENIED", (cause as ListenerFailure).code)
        }
            .retryWithFallback(fallback = -2) { _, _ -> }
            .toList()

        assertEquals(listOf(-1), values)
        assertEquals(1, subscriptions)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun explicitTerminalPolicyEmitsFallbackAndNeverResubscribesEvenAfterCachedData() = runTest {
        var subscriptions = 0
        val values = flow {
            subscriptions++
            emit(7)
            throw ListenerFailure("PERMISSION_DENIED")
        }.retryWithFallback(fallback = -1, shouldRetry = ::shouldRetry) { cause, _ ->
            assertEquals("PERMISSION_DENIED", (cause as ListenerFailure).code)
        }.toList()

        assertEquals(listOf(7, -1), values)
        assertEquals(1, subscriptions)
        assertEquals(0L, testScheduler.currentTime)
    }
}
