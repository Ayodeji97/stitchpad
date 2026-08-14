package com.danzucker.stitchpad.core.data.staff

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.staff.StaffError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StaffCallTimeoutTest {

    @Test
    fun hangingCallableTimesOutToNetworkError() = runTest {
        // The zombie-auth incident: a callable whose response never arrives left
        // the redeem spinner frozen forever. The wrapper must bound every staff
        // callable and surface the existing NETWORK error instead of hanging.
        val result = staffCall<Unit>("redeemStaffInvite") { awaitCancellation() }
        assertTrue(result is Result.Error && result.error == StaffError.NETWORK)
    }

    @Test
    fun fastCallableStillSucceeds() = runTest {
        val result = staffCall("generateStaffInvite") {
            delay(100)
            42
        }
        assertEquals(Result.Success(42), result)
    }

    @Test
    fun externalCancellationStillPropagates() = runTest {
        // A timeout is NETWORK, but a real cancellation (screen torn down) must
        // rethrow — TimeoutCancellationException is a CancellationException
        // subtype, so the catch order in staffCall is load-bearing.
        assertFailsWith<CancellationException> {
            staffCall<Unit>("approveStaffMember") { throw CancellationException("collector cancelled") }
        }
    }
}
