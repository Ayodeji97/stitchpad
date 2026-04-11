package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseAuthWeakPasswordException
import dev.gitlive.firebase.auth.FirebaseUser
import kotlin.math.abs

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth
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
            Result.Error(e.toAuthError())
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
            Result.Error(e.toAuthError())
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): EmptyResult<AuthError> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email)
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Result.Error(e.toAuthError())
        }
    }

    override suspend fun signOut(): Result<Unit, AuthError> {
        return try {
            firebaseAuth.signOut()
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
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
