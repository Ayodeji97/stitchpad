package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository

class FakeAuthRepository : AuthRepository {
    var shouldReturnError: AuthError? = null
    var resetEmailSentTo: String? = null
    private var currentUser: User? = null

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): Result<User, AuthError> {
        shouldReturnError?.let { return Result.Error(it) }
        val user = User(
            id = "test-uid",
            email = email,
            displayName = displayName,
            businessName = null,
            phoneNumber = null,
            avatarColorIndex = 0
        )
        currentUser = user
        return Result.Success(user)
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String
    ): Result<User, AuthError> {
        shouldReturnError?.let { return Result.Error(it) }
        val user = currentUser ?: return Result.Error(AuthError.USER_NOT_FOUND)
        return Result.Success(user)
    }

    override suspend fun sendPasswordResetEmail(email: String): EmptyResult<AuthError> {
        shouldReturnError?.let { return Result.Error(it) }
        resetEmailSentTo = email
        return Result.Success(Unit)
    }

    override suspend fun signOut(): Result<Unit, AuthError> {
        currentUser = null
        return Result.Success(Unit)
    }

    override suspend fun getCurrentUser(): User? = currentUser
    override val isLoggedIn: Boolean get() = currentUser != null
}
