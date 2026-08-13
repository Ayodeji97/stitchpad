package com.danzucker.stitchpad.feature.auth.domain

import com.danzucker.stitchpad.core.data.staff.FakeStaffMembershipPrefsStore
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.notification.push.PushTokenRegistrar
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SignOutUseCaseTest {

    /** Records every side effect in call order so ordering can be asserted. */
    private val calls = mutableListOf<String>()

    private val gate = object : RemoteSyncGate {
        override suspend fun quiesce() {
            calls += "quiesce"
        }

        override suspend fun resume() {
            calls += "resume"
        }
    }

    private val registrar = object : PushTokenRegistrar {
        override suspend fun registerForUser(userId: String) = Unit
        override suspend fun register(userId: String, token: String) = Unit

        override suspend fun unregisterForUser(userId: String) {
            calls += "unregister"
        }

        override suspend fun invalidateToken() {
            calls += "invalidate"
        }
    }

    private val authRepository = FakeAuthRepository().apply {
        currentUser = User(
            id = "uid-1",
            email = "t@x.com",
            displayName = "T",
            businessName = null,
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
        )
    }

    private val prefs = FakeStaffMembershipPrefsStore()

    private fun useCase() = SignOutUseCase(
        authRepository = object : AuthRepository by authRepository {
            override suspend fun signOut(): Result<Unit, AuthError> {
                calls += "signOut"
                return authRepository.signOut()
            }
        },
        pushTokenRegistrar = registrar,
        pendingDeepLink = PendingDeepLinkHolder(),
        staffMembershipPrefs = prefs,
        remoteSyncGate = gate,
    )

    @Test
    fun quiescesStreamsAfterTokenCleanupAndBeforeSignOut() = runTest {
        val result = useCase()()

        assertIs<Result.Success<Unit>>(result)
        // The gate MUST close after the (auth-requiring) token-doc delete and
        // before auth is cleared — that ordering is the whole crash fix.
        assertContentEquals(listOf("unregister", "quiesce", "signOut", "invalidate"), calls)
    }

    @Test
    fun successLeavesGateClosedForNextSignInToReopen() = runTest {
        useCase()()

        assertFalse("resume" in calls, "gate must stay closed after a successful sign-out")
    }

    @Test
    fun failureReopensGateSoTheStillSignedInUserKeepsLiveData() = runTest {
        authRepository.signOutError = AuthError.UNKNOWN

        val result = useCase()()

        assertIs<Result.Error<AuthError>>(result)
        assertTrue(calls.indexOf("resume") > calls.indexOf("quiesce"), "resume must follow quiesce")
        assertFalse("invalidate" in calls, "failed sign-out must not rotate the FCM token")
    }
}
