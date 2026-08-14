package com.danzucker.stitchpad.core.data

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SafeCallTest {

    @Test
    fun successWrapsValue() = runTest {
        val result = safeCall(tag = "test", op = "op") { 42 }
        assertEquals(Result.Success(42), result)
    }

    @Test
    fun expectedFailureBecomesResultError() = runTest {
        val result = safeCall<Int>(tag = "test", op = "op") { error("network broke") }
        assertTrue(result is Result.Error && result.error == DataError.Network.UNKNOWN)
    }

    @Test
    fun cancellationIsRethrownNotSwallowed() = runTest {
        // The audit's core finding: converting cancellation into Result.Error makes
        // a torn-down screen take its error branch. Cancellation must propagate.
        assertFailsWith<CancellationException> {
            safeCall<Int>(tag = "test", op = "op") { throw CancellationException("cancelled") }
        }
    }
}
