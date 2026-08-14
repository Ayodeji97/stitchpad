package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthSessionValidatorTest {

    private class Harness(
        scope: CoroutineScope,
        refreshResult: suspend () -> EmptyResult<AuthError>,
    ) {
        val userIds = MutableStateFlow<String?>(null)
        var validateCalls = 0
        var signOutCalls = 0

        val validator = AuthSessionValidator(
            userIdSource = userIds,
            validateSession = {
                validateCalls += 1
                refreshResult()
            },
            currentUserId = { userIds.value },
            signOut = { signOutCalls += 1 },
            scope = scope,
        )
    }

    @Test
    fun invalidUserSessionForcesSignOut() = runTest {
        // The zombie-auth incident: refresh token dead, user object still cached.
        // forceRefreshIdToken maps that to USER_NOT_FOUND — the validator must
        // sign out so the app routes to login instead of freezing everywhere.
        val h = Harness(CoroutineScope(UnconfinedTestDispatcher(testScheduler))) {
            Result.Error(AuthError.USER_NOT_FOUND)
        }
        h.validator.start()
        h.userIds.value = "staff-uid"
        assertEquals(1, h.validateCalls)
        assertEquals(1, h.signOutCalls)
    }

    @Test
    fun transientNetworkFailureNeverSignsOut() = runTest {
        // A cold start in a dead zone must not eject a valid session.
        val h = Harness(CoroutineScope(UnconfinedTestDispatcher(testScheduler))) {
            Result.Error(AuthError.NETWORK_ERROR)
        }
        h.validator.start()
        h.userIds.value = "staff-uid"
        assertEquals(1, h.validateCalls)
        assertEquals(0, h.signOutCalls)
    }

    @Test
    fun healthySessionIsLeftAlone() = runTest {
        val h = Harness(CoroutineScope(UnconfinedTestDispatcher(testScheduler))) {
            Result.Success(Unit)
        }
        h.validator.start()
        h.userIds.value = "owner-uid"
        assertEquals(1, h.validateCalls)
        assertEquals(0, h.signOutCalls)
    }

    @Test
    fun signedOutStateIsNotValidated() = runTest {
        val h = Harness(CoroutineScope(UnconfinedTestDispatcher(testScheduler))) {
            Result.Success(Unit)
        }
        h.validator.start()
        // userIds stays null (start-up before sign-in) — no token refresh attempted.
        assertEquals(0, h.validateCalls)
        assertEquals(0, h.signOutCalls)
    }

    @Test
    fun slowValidationIsCancelledWhenTheUserSignsOutMidFlight() = runTest {
        // Cursor finding, PR #363: a slow refresh for the zombie user must not
        // complete after a sign-out and eject whoever signed in next.
        val h = Harness(CoroutineScope(StandardTestDispatcher(testScheduler))) {
            delay(1_000)
            Result.Error(AuthError.USER_NOT_FOUND)
        }
        h.validator.start()
        h.userIds.value = "zombie-uid"
        advanceTimeBy(500)
        runCurrent()
        h.userIds.value = null // user signed out while the refresh was in flight
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, h.validateCalls)
        assertEquals(0, h.signOutCalls)
    }

    @Test
    fun staleInvalidResultForADifferentUserNeverSignsOut() = runTest {
        // Even if the stale result lands before the new uid emission is
        // dispatched, the identity guard must refuse to eject the new session.
        val h = Harness(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        {
            Result.Error(AuthError.USER_NOT_FOUND)
        }
        // Simulate the emission-latency gap: the flow says "zombie-uid" but by
        // the time validation completes the live auth state is a NEW user.
        val validator = AuthSessionValidator(
            userIdSource = h.userIds,
            validateSession = {
                h.validateCalls += 1
                Result.Error(AuthError.USER_NOT_FOUND)
            },
            currentUserId = { "fresh-uid" },
            signOut = { h.signOutCalls += 1 },
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        validator.start()
        h.userIds.value = "zombie-uid"
        assertEquals(1, h.validateCalls)
        assertEquals(0, h.signOutCalls)
    }
}
