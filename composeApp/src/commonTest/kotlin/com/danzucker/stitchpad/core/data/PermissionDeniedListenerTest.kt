package com.danzucker.stitchpad.core.data

import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PermissionDeniedListenerTest {
    // The Android SDK exception constructor needs Android's TextUtils. Use a
    // coded listener failure here and the same code policy as the SDK adapter.
    private class ListenerFailure(val code: FirestoreExceptionCode) : Exception()

    private fun shouldRetry(cause: Throwable) = retryableFirestoreCode((cause as ListenerFailure).code)

    @Test
    fun deniedInnerListenerDoesNotRestartThroughAnOuterRetry() = runTest {
        var subscriptions = 0
        val values = flow<Int> {
            subscriptions++
            throw ListenerFailure(FirestoreExceptionCode.PERMISSION_DENIED)
        }.retryWithFallback(fallback = -1, shouldRetry = ::shouldRetry) { _, _ -> }
            .retryWithFallback(fallback = -2) { _, _ -> }
            .toList()

        assertEquals(listOf(-1), values)
        assertEquals(1, subscriptions)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun permissionDeniedEmitsFallbackAndNeverResubscribesEvenAfterCachedData() = runTest {
        var subscriptions = 0
        val values = flow {
            subscriptions++
            emit(7)
            throw ListenerFailure(FirestoreExceptionCode.PERMISSION_DENIED)
        }.retryWithFallback(fallback = -1, shouldRetry = ::shouldRetry) { _, _ -> }.toList()

        assertEquals(listOf(7, -1), values)
        assertEquals(1, subscriptions)
        assertEquals(0L, testScheduler.currentTime)
    }
}
