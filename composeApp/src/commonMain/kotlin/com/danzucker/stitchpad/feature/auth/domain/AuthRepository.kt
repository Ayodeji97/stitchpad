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

    /** Sends a verification link to the signed-in user's email address. */
    suspend fun sendEmailVerification(): EmptyResult<AuthError>

    /**
     * Refreshes the locally cached FirebaseUser from the server so a freshly
     * tapped verification link is reflected by [isEmailVerified].
     */
    suspend fun reloadUser(): EmptyResult<AuthError>

    /** Whether the signed-in user's email is verified (cached value — call [reloadUser] first). */
    suspend fun isEmailVerified(): Boolean

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

    /**
     * Sync the Firebase Auth displayName so it matches what's stored in
     * Firestore via the user's Edit Profile save. Pass null/blank to clear.
     * Needed because the Auth-side displayName is cached on the FirebaseUser
     * and used as a fallback in load paths — without this, clearing the name
     * in Settings would reappear from the cached Auth value.
     */
    suspend fun updateAuthDisplayName(name: String?): EmptyResult<AuthError>
}
