package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SsoError
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseAuthWeakPasswordException
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import kotlin.math.abs

private const val TAG = "AuthRepo"

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth,
    private val ssoCredentialProvider: SsoCredentialProvider,
) : AuthRepository {

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): Result<User, AuthError> {
        return try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password)
            val firebaseUser = authResult.user ?: return Result.Error(AuthError.UNKNOWN)
            firebaseUser.updateProfile(displayName = displayName)
            Result.Success(firebaseUser.toDomainUser())
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val error = e.toAuthError()
            AppLogger.e(tag = TAG, throwable = e) { "signUpWithEmail failed error=$error" }
            Result.Error(error)
        }
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String
    ): Result<User, AuthError> {
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password)
            val firebaseUser = authResult.user ?: return Result.Error(AuthError.UNKNOWN)
            Result.Success(firebaseUser.toDomainUser())
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val error = e.toAuthError()
            AppLogger.e(tag = TAG, throwable = e) { "signInWithEmail failed error=$error" }
            Result.Error(error)
        }
    }

    override suspend fun signInWithGoogle(): Result<User, AuthError> {
        return when (val tokenResult = ssoCredentialProvider.getGoogleIdToken()) {
            is Result.Error -> Result.Error(tokenResult.error.toAuthError())
            is Result.Success -> exchangeGoogleToken(tokenResult.data)
        }
    }

    private suspend fun exchangeGoogleToken(idToken: String): Result<User, AuthError> {
        return try {
            val credential = GoogleAuthProvider.credential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential)
            val firebaseUser = authResult.user
                ?: return Result.Error(AuthError.UNKNOWN)
            Result.Success(firebaseUser.toDomainUser())
        } catch (e: FirebaseAuthUserCollisionException) {
            AppLogger.e(tag = TAG, throwable = e) { "Google sign-in collision" }
            Result.Error(AuthError.EMAIL_REGISTERED_WITH_OTHER_PROVIDER)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "Google credential exchange failed" }
            Result.Error(e.toAuthError())
        }
    }

    override suspend fun signInWithApple(): Result<User, AuthError> {
        // TODO(Task 18): real impl
        return Result.Error(AuthError.UNKNOWN)
    }

    override suspend fun deleteAccount(): EmptyResult<AuthError> {
        // TODO(Task 21): Auth-first delete + best-effort Firestore cleanup
        return Result.Error(AuthError.UNKNOWN)
    }

    override suspend fun sendPasswordResetEmail(email: String): EmptyResult<AuthError> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email)
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val error = e.toAuthError()
            AppLogger.e(tag = TAG, throwable = e) { "sendPasswordResetEmail failed error=$error" }
            Result.Error(error)
        }
    }

    override suspend fun signOut(): Result<Unit, AuthError> {
        return try {
            firebaseAuth.signOut()
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "signOut failed" }
            Result.Error(AuthError.UNKNOWN)
        }
    }

    override suspend fun getCurrentUser(): User? {
        return firebaseAuth.currentUser?.toDomainUser()
    }

    override val isLoggedIn: Boolean
        get() = firebaseAuth.currentUser != null
}

private fun FirebaseUser.toDomainUser(): User = User(
    id = uid,
    email = email ?: "",
    displayName = displayName ?: "",
    businessName = null,
    phoneNumber = phoneNumber,
    whatsappNumber = null,
    avatarColorIndex = abs((displayName ?: email ?: uid).hashCode() % 6)
)

private fun Exception.toAuthError(): AuthError = when {
    this is FirebaseAuthUserCollisionException -> AuthError.EMAIL_ALREADY_IN_USE
    this is FirebaseAuthWeakPasswordException -> AuthError.WEAK_PASSWORD
    this is FirebaseAuthInvalidCredentialsException -> AuthError.INVALID_CREDENTIALS
    message?.contains("EMAIL_ALREADY_IN_USE", ignoreCase = true) == true -> AuthError.EMAIL_ALREADY_IN_USE
    message?.contains("email-already-in-use", ignoreCase = true) == true -> AuthError.EMAIL_ALREADY_IN_USE
    message?.contains("already in use", ignoreCase = true) == true -> AuthError.EMAIL_ALREADY_IN_USE
    message?.contains("WRONG_PASSWORD", ignoreCase = true) == true -> AuthError.INVALID_CREDENTIALS
    message?.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) == true -> AuthError.INVALID_CREDENTIALS
    message?.contains("USER_NOT_FOUND", ignoreCase = true) == true -> AuthError.USER_NOT_FOUND
    message?.contains("WEAK_PASSWORD", ignoreCase = true) == true -> AuthError.WEAK_PASSWORD
    message?.contains("TOO_MANY_ATTEMPTS", ignoreCase = true) == true -> AuthError.TOO_MANY_REQUESTS
    message?.contains("NETWORK", ignoreCase = true) == true -> AuthError.NETWORK_ERROR
    else -> AuthError.UNKNOWN
}

private fun SsoError.toAuthError(): AuthError = when (this) {
    SsoError.CANCELLED -> AuthError.SSO_CANCELLED
    SsoError.NO_PROVIDER -> AuthError.SSO_UNAVAILABLE
    SsoError.NETWORK -> AuthError.NETWORK_ERROR
    SsoError.UNKNOWN -> AuthError.UNKNOWN
}
