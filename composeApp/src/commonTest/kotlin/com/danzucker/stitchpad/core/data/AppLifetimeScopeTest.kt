package com.danzucker.stitchpad.core.data

import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppLifetimeScopeTest {

    @Test
    fun uncaughtThrowIsRoutedToHandlerAndSiblingsSurvive() = runTest {
        val absorbed = mutableListOf<Throwable>()
        val scope = appLifetimeScope(tag = "test") { absorbed += it }

        val failing = scope.launch { throw IllegalStateException("boom") }
        failing.join()

        val sibling = scope.launch { /* still schedulable */ }
        sibling.join()

        assertEquals(1, absorbed.size)
        assertEquals("boom", absorbed.first().message)
        assertTrue(scope.coroutineContext.job.isActive, "SupervisorJob must survive a child failure")
    }
}
