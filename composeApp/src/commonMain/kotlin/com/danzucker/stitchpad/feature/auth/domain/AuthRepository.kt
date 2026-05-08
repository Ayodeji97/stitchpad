package com.danzucker.stitchpad.feature.auth.domain

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User

@Suppress("TooManyFunctions")
interface AuthRepository {
    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<User, AuthError>
    suspend fun signInWithEmail(email: String, password: String): Result<User, AuthError>
    suspend fun signInWithGoogle(): Result<User, AuthError>
    suspend fun signInWithApple(): Result<User, AuthError>
    suspend fun sendPasswordResetEmail(email: String): EmptyResult<AuthError>
    suspend fun signOut(): Result<Unit, AuthError>
    suspend fun deleteAccount(): EmptyResult<AuthError>
    suspend fun getCurrentUser(): User?
    val isLoggedIn: Boolean

    suspend fun getSignInProvider(): SignInProvider
    suspend fun reauthenticateWithPassword(password: String): EmptyResult<AuthError>
    suspend fun reauthenticateWithApple(): EmptyResult<AuthError>
    suspend fun reauthenticateWithGoogle(): EmptyResult<AuthError>
    suspend fun updateEmail(newEmail: String): EmptyResult<AuthError>
    suspend fun updatePassword(newPassword: String): EmptyResult<AuthError>
    suspend fun deleteAccount(): EmptyResult<AuthError>
}
