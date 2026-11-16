package com.danzucker.stitchpad.core.data

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PermissionDeniedListenerTest {
    @Test
    fun deniedInnerListenerDoesNotRestartThroughAnOuterRetry() = runTest {
        var subscriptions = 0
        val values = flow<Int> {
            subscriptions++
            throw FirebaseFirestoreException("Denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }.retryWithFallback(fallback = -1) { _, _ -> }
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
            throw FirebaseFirestoreException("Denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }.retryWithFallback(fallback = -1) { _, _ -> }.toList()

        assertEquals(listOf(7, -1), values)
        assertEquals(1, subscriptions)
        assertEquals(0L, testScheduler.currentTime)
    }
}
