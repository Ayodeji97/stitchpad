package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider

class FakeAuthRepository : AuthRepository {
    var shouldReturnError: AuthError? = null
    var resetEmailSentTo: String? = null
    var signUpInvocationCount = 0
    var deleteAccountInvocationCount = 0
    var currentUser: User? = null
    var signInProvider: SignInProvider = SignInProvider.EMAIL_PASSWORD
    var lastReauthPassword: String? = null
    var lastUpdatedEmail: String? = null
    var lastUpdatedPassword: String? = null
    var deleteAccountCalled: Boolean = false

    // --- Email verification controls ---
    var emailVerificationSentCount = 0
    var reloadCount = 0
    var isEmailVerifiedValue = false

    /**
     * When set, [reloadUser] flips [isEmailVerifiedValue] to true once
     * [reloadCount] reaches this value — simulates the user tapping the link
     * partway through the verification screen's polling loop.
     */
    var verifyAfterReloads: Int? = null

    var currentBusinessName: String?
        get() = currentUser?.businessName
        set(value) {
            currentUser = currentUser?.copy(businessName = value)
        }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): Result<User, AuthError> {
        signUpInvocationCount++
        shouldReturnError?.let { return Result.Error(it) }
        val user = User(
            id = "test-uid",
            email = email,
            displayName = displayName,
            businessName = null,
            phoneNumber = null,
            whatsappNumber = null,
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

    override suspend fun signInWithGoogle(): Result<User, AuthError> {
        shouldReturnError?.let { return Result.Error(it) }
        val user = currentUser ?: User(
            id = "test-google-uid",
            email = "google@example.com",
            displayName = "Google User",
            businessName = null,
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0
        )
        currentUser = user
        return Result.Success(user)
    }

    override suspend fun signInWithApple(): Result<User, AuthError> {
        shouldReturnError?.let { return Result.Error(it) }
        val user = currentUser ?: User(
            id = "test-apple-uid",
            email = "apple@privaterelay.appleid.com",
            displayName = "Apple User",
            businessName = null,
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0
        )
        currentUser = user
        return Result.Success(user)
    }

    override suspend fun sendPasswordResetEmail(email: String): EmptyResult<AuthError> {
        shouldReturnError?.let { return Result.Error(it) }
        resetEmailSentTo = email
        return Result.Success(Unit)
    }

    override suspend fun sendEmailVerification(): EmptyResult<AuthError> {
        shouldReturnError?.let { return Result.Error(it) }
        emailVerificationSentCount++
        return Result.Success(Unit)
    }

    override suspend fun reloadUser(): EmptyResult<AuthError> {
        reloadCount++
        verifyAfterReloads?.let { threshold ->
            if (reloadCount >= threshold) isEmailVerifiedValue = true
        }
        shouldReturnError?.let { return Result.Error(it) }
        return Result.Success(Unit)
    }

    override suspend fun isEmailVerified(): Boolean = isEmailVerifiedValue

    override suspend fun signOut(): Result<Unit, AuthError> {
        currentUser = null
        return Result.Success(Unit)
    }

    override suspend fun getCurrentUser(): User? = currentUser
    override val isLoggedIn: Boolean get() = currentUser != null

    override suspend fun getSignInProvider(): SignInProvider = signInProvider

    override suspend fun reauthenticateWithPassword(password: String): EmptyResult<AuthError> {
        shouldReturnError?.let { return Result.Error(it) }
        lastReauthPassword = password
        return Result.Success(Unit)
    }

    override suspend fun reauthenticateWithApple(): EmptyResult<AuthError> {
        shouldReturnError?.let { return Result.Error(it) }
        return Result.Error(AuthError.PROVIDER_NOT_SUPPORTED)
    }

    override suspend fun reauthenticateWithGoogle(): EmptyResult<AuthError> {
        shouldReturnError?.let { return Result.Error(it) }
        return Result.Error(AuthError.PROVIDER_NOT_SUPPORTED)
    }

    override suspend fun updateEmail(newEmail: String): EmptyResult<AuthError> {
        shouldReturnError?.let { return Result.Error(it) }
        lastUpdatedEmail = newEmail
        return Result.Success(Unit)
    }

    override suspend fun updatePassword(newPassword: String): EmptyResult<AuthError> {
        shouldReturnError?.let { return Result.Error(it) }
        lastUpdatedPassword = newPassword
        return Result.Success(Unit)
    }

    override suspend fun updateAuthDisplayName(name: String?): EmptyResult<AuthError> {
        shouldReturnError?.let { return Result.Error(it) }
        currentUser = currentUser?.copy(displayName = name?.takeIf { it.isNotBlank() } ?: "")
        return Result.Success(Unit)
    }

    override suspend fun deleteAccount(): EmptyResult<AuthError> {
        deleteAccountInvocationCount++
        shouldReturnError?.let { return Result.Error(it) }
        deleteAccountCalled = true
        currentUser = null
        return Result.Success(Unit)
    }
}
