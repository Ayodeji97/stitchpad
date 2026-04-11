package com.danzucker.stitchpad.feature.auth.domain

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User

interface AuthRepository {
    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<User, AuthError>
    suspend fun signInWithEmail(email: String, password: String): Result<User, AuthError>
    suspend fun sendPasswordResetEmail(email: String): EmptyResult<AuthError>
    suspend fun signOut(): Result<Unit, AuthError>
    suspend fun getCurrentUser(): User?
    val isLoggedIn: Boolean
}
