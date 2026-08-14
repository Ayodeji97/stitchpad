package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthSessionValidatorTest {

    private class Harness(
        scope: CoroutineScope,
        refreshResult: () -> EmptyResult<AuthError>,
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
}
