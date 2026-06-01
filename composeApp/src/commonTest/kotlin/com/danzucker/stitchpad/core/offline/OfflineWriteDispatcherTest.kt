package com.danzucker.stitchpad.core.offline

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout

class OfflineWriteDispatcherTest {
    @Test
    fun enqueue_returns_after_scheduling_without_waiting_for_server_ack() = runTest {
        val dispatcher = OfflineWriteDispatcher(backgroundScope)

        val accepted = withTimeout(100) {
            dispatcher.enqueue("hangingWrite") {
                awaitCancellation()
            }
        }

        assertTrue(accepted)
    }
}
