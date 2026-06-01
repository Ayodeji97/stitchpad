package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.feature.auth.domain.AuthError

/**
 * Sends the branded verification email via the `sendVerificationEmail` Cloud
 * Function (Resend on a custom domain) instead of Firebase Auth's default
 * sender, which lands in spam. A test seam so [FirebaseAuthRepository] stays
 * unit-testable without a Functions client.
 */
interface VerificationEmailSender {
    suspend fun send(): EmptyResult<AuthError>
}
