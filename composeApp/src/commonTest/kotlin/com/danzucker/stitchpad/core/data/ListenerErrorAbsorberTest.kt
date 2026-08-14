package com.danzucker.stitchpad.core.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ListenerErrorAbsorberTest {

    /**
     * Reproduces the production crash shape: the SDK's error callback closes the
     * callbackFlow channel with a cause WHILE the collector is being cancelled
     * (here: from inside awaitClose, which runs during teardown — exactly when a
     * server rejection races listener removal). Without the absorber, that cause
     * is undeliverable and reaches the global handler as a fatal crash; with it,
     * the wrapper's handler receives it and the test completes normally.
     */
    @Test
    fun lateCloseCauseIsAbsorbedNotFatal() = runTest {
        val absorbed = CompletableDeferred<Throwable>()
        val upstream = callbackFlow {
            trySend(1)
            awaitClose {
                // The SDK error callback firing during listener teardown.
                close(IllegalStateException("PERMISSION_DENIED after teardown"))
            }
        }

        val firstItem = CompletableDeferred<Int>()
        val collector = launch {
            upstream
                .absorbLateListenerErrors { absorbed.complete(it) }
                .collect {
                    firstItem.complete(it)
                    awaitCancellation()
                }
        }

        assertEquals(1, firstItem.await())
        collector.cancelAndJoin() // triggers awaitClose -> late close(cause)

        assertTrue(absorbed.isCompleted, "late close cause must reach the absorber")
        assertIs<IllegalStateException>(absorbed.await())
    }

    /** Normal error delivery (collector alive) must still flow to downstream catch. */
    @Test
    fun liveErrorsStillReachDownstreamCatch() = runTest {
        val absorbed = mutableListOf<Throwable>()
        val caught = CompletableDeferred<Throwable>()
        val upstream = callbackFlow<Int> {
            close(IllegalStateException("live error"))
            awaitClose { }
        }

        launch {
            try {
                upstream.absorbLateListenerErrors { absorbed.add(it) }.collect { }
            } catch (e: IllegalStateException) {
                caught.complete(e)
            }
        }

        assertEquals("live error", caught.await().message)
        assertTrue(absorbed.isEmpty(), "live errors must not be absorbed")
    }
}
