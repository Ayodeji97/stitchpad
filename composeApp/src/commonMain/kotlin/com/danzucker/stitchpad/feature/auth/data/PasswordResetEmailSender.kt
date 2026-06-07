package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.feature.auth.domain.AuthError

/**
 * Sends the branded password-reset email via the `sendPasswordResetEmail` Cloud
 * Function (Resend on a custom domain) instead of Firebase Auth's default
 * sender, whose firebaseapp.com reputation lands the mail in spam. A test seam
 * so [FirebaseAuthRepository] stays unit-testable without a Functions client.
 */
interface PasswordResetEmailSender {
    suspend fun send(email: String): EmptyResult<AuthError>
}
