package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.domain.AppleCredential
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.domain.GoogleCredential
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import com.danzucker.stitchpad.feature.auth.domain.SsoError
import com.danzucker.stitchpad.feature.auth.domain.SsoSignIn
import dev.gitlive.firebase.auth.EmailAuthProvider
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidUserException
import dev.gitlive.firebase.auth.FirebaseAuthRecentLoginRequiredException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseAuthWeakPasswordException
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.OAuthProvider
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

private const val TAG = "AuthRepo"

// Every method below catches a generic Exception to map Firebase failures into
// Result.Error. Each first rethrows CancellationException so structured-concurrency
// cancellation (e.g. the email-verification polling job cancelled mid-reload by
// logout, or a ViewModel being cleared) propagates cleanly instead of being
// swallowed into a bogus Result.Error + error log. Mirrors the same guard in
// GitLiveVerificationEmailSender.
@Suppress("TooManyFunctions")
class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth,
    private val ssoCredentialProvider: SsoCredentialProvider,
    private val userRepository: UserRepository,
    private val verificationEmailSender: VerificationEmailSender,
    private val passwordResetEmailSender: PasswordResetEmailSender,
) : AuthRepository {

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): Result<User, AuthError> {
        return try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password)
            val firebaseUser = authResult.user ?: run {
                AppLogger.w(tag = TAG) { "signUpWithEmail: createUser succeeded but authResult.user was null" }
                return Result.Error(AuthError.UNKNOWN)
            }
            // Best-effort: the account already exists at this point, so a failed
            // displayName write must not fail the whole signup (mirrors the Apple
            // path). The name is re-applied later via updateAuthDisplayName.
            runCatching { firebaseUser.updateProfile(displayName = displayName) }
                .onFailure {
                    AppLogger.w(tag = TAG, throwable = it) {
                        "signUpWithEmail: displayName update failed (non-fatal)"
                    }
                }
            Result.Success(firebaseUser.toDomainUser())
        } catch (e: CancellationException) {
            throw e
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
            val firebaseUser = authResult.user ?: run {
                AppLogger.w(tag = TAG) { "signInWithEmail: signIn succeeded but authResult.user was null" }
                return Result.Error(AuthError.UNKNOWN)
            }
            Result.Success(firebaseUser.toDomainUser())
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val error = e.toAuthError()
            AppLogger.e(tag = TAG, throwable = e) { "signInWithEmail failed error=$error" }
            Result.Error(error)
        }
    }

    override suspend fun signInWithGoogle(): Result<SsoSignIn, AuthError> {
        return when (val credResult = ssoCredentialProvider.getGoogleCredential()) {
            is Result.Error -> Result.Error(credResult.error.toAuthError())
            is Result.Success -> exchangeGoogleCredential(credResult.data)
        }
    }

    private suspend fun exchangeGoogleCredential(cred: GoogleCredential): Result<SsoSignIn, AuthError> {
        return try {
            val credential = GoogleAuthProvider.credential(cred.idToken, cred.accessToken)
            val authResult = firebaseAuth.signInWithCredential(credential)
            val firebaseUser = authResult.user
                ?: return Result.Error(AuthError.UNKNOWN)
            Result.Success(
                SsoSignIn(
                    user = firebaseUser.toDomainUser(),
                    // Absent additionalUserInfo counts as returning — undercounting
                    // sign_up is safer than overcounting it.
                    isNewUser = authResult.additionalUserInfo?.isNewUser ?: false,
                )
            )
        } catch (e: FirebaseAuthUserCollisionException) {
            AppLogger.e(tag = TAG, throwable = e) { "Google sign-in collision" }
            Result.Error(AuthError.EMAIL_REGISTERED_WITH_OTHER_PROVIDER)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "Google credential exchange failed" }
            Result.Error(e.toAuthError())
        }
    }

    override suspend fun signInWithApple(): Result<SsoSignIn, AuthError> {
        return when (val credResult = ssoCredentialProvider.getAppleCredential()) {
            is Result.Error -> Result.Error(credResult.error.toAuthError())
            is Result.Success -> exchangeAppleCredential(credResult.data)
        }
    }

    private suspend fun exchangeAppleCredential(cred: AppleCredential): Result<SsoSignIn, AuthError> {
        return try {
            val firebaseCredential = OAuthProvider.credential(
                providerId = "apple.com",
                idToken = cred.idToken,
                rawNonce = cred.rawNonce,
            )
            val authResult = firebaseAuth.signInWithCredential(firebaseCredential)
            val firebaseUser = authResult.user
                ?: return Result.Error(AuthError.UNKNOWN)

            if (firebaseUser.displayName.isNullOrBlank() && !cred.fullName.isNullOrBlank()) {
                runCatching { firebaseUser.updateProfile(displayName = cred.fullName) }
                    .onFailure { AppLogger.e(tag = TAG, throwable = it) { "Apple displayName update failed" } }
            }

            Result.Success(
                SsoSignIn(
                    user = firebaseUser.toDomainUser(),
                    // Absent additionalUserInfo counts as returning — undercounting
                    // sign_up is safer than overcounting it.
                    isNewUser = authResult.additionalUserInfo?.isNewUser ?: false,
                )
            )
        } catch (e: FirebaseAuthUserCollisionException) {
            AppLogger.e(tag = TAG, throwable = e) { "Apple sign-in collision" }
            Result.Error(AuthError.EMAIL_REGISTERED_WITH_OTHER_PROVIDER)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "Apple credential exchange failed" }
            Result.Error(e.toAuthError())
        }
    }

    override suspend fun deleteAccount(): EmptyResult<AuthError> {
        val user = firebaseAuth.currentUser
            ?: return Result.Error(AuthError.USER_NOT_FOUND)
        val uid = user.uid
        return try {
            user.delete()

            runCatching { firebaseAuth.signOut() }

            runCatching { userRepository.deleteUserDoc(uid) }
                .onFailure {
                    AppLogger.e(tag = TAG, throwable = it) {
                        "post-delete Firestore cleanup failed uid=$uid"
                    }
                }
            Result.Success(Unit)
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            AppLogger.e(tag = TAG, throwable = e) { "deleteAccount requires recent login uid=$uid" }
            Result.Error(AuthError.REQUIRES_RECENT_LOGIN)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "deleteAccount failed uid=$uid" }
            Result.Error(e.toAuthError())
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): EmptyResult<AuthError> {
        // Delegated to the sendPasswordResetEmail Cloud Function (branded email
        // via Resend on a custom domain) rather than Firebase Auth's default
        // sender, whose firebaseapp.com reputation lands the mail in spam.
        return passwordResetEmailSender.send(email)
    }

    override suspend fun sendEmailVerification(): EmptyResult<AuthError> {
        // Delegated to the sendVerificationEmail Cloud Function (branded email
        // via Resend on a custom domain) rather than Firebase Auth's default
        // sender, whose firebaseapp.com reputation lands the mail in spam.
        firebaseAuth.currentUser ?: return Result.Error(AuthError.USER_NOT_FOUND)
        return verificationEmailSender.send()
    }

    override suspend fun reloadUser(): EmptyResult<AuthError> {
        val user = firebaseAuth.currentUser ?: return Result.Error(AuthError.USER_NOT_FOUND)
        return try {
            user.reload()
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val error = e.toAuthError()
            AppLogger.e(tag = TAG, throwable = e) { "reloadUser failed error=$error" }
            Result.Error(error)
        }
    }

    override suspend fun isEmailVerified(): Boolean {
        return firebaseAuth.currentUser?.isEmailVerified ?: false
    }

    override suspend fun signOut(): Result<Unit, AuthError> {
        return try {
            firebaseAuth.signOut()
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
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

    override suspend fun getSignInProvider(): SignInProvider {
        val user = firebaseAuth.currentUser
        val providerId = user?.providerData
            ?.map { it.providerId }
            ?.firstOrNull { it != "firebase" }
        return when (providerId) {
            "password" -> SignInProvider.EMAIL_PASSWORD
            "apple.com" -> SignInProvider.APPLE
            "google.com" -> SignInProvider.GOOGLE
            else -> SignInProvider.UNKNOWN
        }
    }

    @Suppress("ReturnCount")
    override suspend fun reauthenticateWithPassword(password: String): EmptyResult<AuthError> {
        val user = firebaseAuth.currentUser ?: return Result.Error(AuthError.USER_NOT_FOUND)
        val email = user.email ?: return Result.Error(AuthError.INVALID_EMAIL)
        return try {
            val credential = EmailAuthProvider.credential(email, password)
            user.reauthenticate(credential)
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val error = e.toAuthError()
            AppLogger.e(tag = TAG, throwable = e) { "reauthenticateWithPassword failed error=$error" }
            Result.Error(error)
        }
    }

    override suspend fun reauthenticateWithApple(): EmptyResult<AuthError> {
        val user = firebaseAuth.currentUser ?: return Result.Error(AuthError.USER_NOT_FOUND)
        return when (val credResult = ssoCredentialProvider.getAppleCredential()) {
            is Result.Error -> Result.Error(credResult.error.toAuthError())
            is Result.Success -> reauthenticateWithAppleCredential(user, credResult.data)
        }
    }

    private suspend fun reauthenticateWithAppleCredential(
        user: FirebaseUser,
        cred: AppleCredential,
    ): EmptyResult<AuthError> {
        return try {
            val firebaseCredential = OAuthProvider.credential(
                providerId = "apple.com",
                idToken = cred.idToken,
                rawNonce = cred.rawNonce,
            )
            user.reauthenticate(firebaseCredential)
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val error = e.toAuthError()
            AppLogger.e(tag = TAG, throwable = e) { "reauthenticateWithApple failed error=$error" }
            Result.Error(error)
        }
    }

    override suspend fun reauthenticateWithGoogle(): EmptyResult<AuthError> {
        val user = firebaseAuth.currentUser ?: return Result.Error(AuthError.USER_NOT_FOUND)
        return when (val credResult = ssoCredentialProvider.getGoogleCredential()) {
            is Result.Error -> Result.Error(credResult.error.toAuthError())
            is Result.Success -> reauthenticateWithGoogleCredential(user, credResult.data)
        }
    }

    private suspend fun reauthenticateWithGoogleCredential(
        user: FirebaseUser,
        cred: GoogleCredential,
    ): EmptyResult<AuthError> {
        return try {
            val credential = GoogleAuthProvider.credential(cred.idToken, cred.accessToken)
            user.reauthenticate(credential)
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val error = e.toAuthError()
            AppLogger.e(tag = TAG, throwable = e) { "reauthenticateWithGoogle failed error=$error" }
            Result.Error(error)
        }
    }

    override suspend fun updateEmail(newEmail: String): EmptyResult<AuthError> {
        val user = firebaseAuth.currentUser ?: return Result.Error(AuthError.USER_NOT_FOUND)
        return try {
            user.verifyBeforeUpdateEmail(newEmail)
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val error = e.toAuthError()
            AppLogger.e(tag = TAG, throwable = e) { "updateEmail failed error=$error" }
            Result.Error(error)
        }
    }

    override suspend fun updatePassword(newPassword: String): EmptyResult<AuthError> {
        val user = firebaseAuth.currentUser ?: return Result.Error(AuthError.USER_NOT_FOUND)
        return try {
            user.updatePassword(newPassword)
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val error = e.toAuthError()
            AppLogger.e(tag = TAG, throwable = e) { "updatePassword failed error=$error" }
            Result.Error(error)
        }
    }

    override suspend fun updateAuthDisplayName(name: String?): EmptyResult<AuthError> {
        val user = firebaseAuth.currentUser ?: return Result.Error(AuthError.USER_NOT_FOUND)
        return try {
            // gitlive's updateProfile takes a nullable String — null clears the
            // displayName on Firebase Auth's side, which is what we want when the
            // user empties the "Your name" field in Edit Profile.
            user.updateProfile(displayName = name?.takeIf { it.isNotBlank() })
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val error = e.toAuthError()
            AppLogger.e(tag = TAG, throwable = e) { "updateAuthDisplayName failed error=$error" }
            Result.Error(error)
        }
    }
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

@Suppress("CyclomaticComplexMethod")
private fun Exception.toAuthError(): AuthError = when {
    this is FirebaseAuthRecentLoginRequiredException -> AuthError.REQUIRES_RECENT_LOGIN
    this is FirebaseAuthInvalidUserException -> AuthError.USER_NOT_FOUND
    this is FirebaseAuthUserCollisionException -> AuthError.EMAIL_ALREADY_IN_USE
    this is FirebaseAuthWeakPasswordException -> AuthError.WEAK_PASSWORD
    this is FirebaseAuthInvalidCredentialsException -> AuthError.INVALID_CREDENTIALS
    message?.contains("RECENT_LOGIN", ignoreCase = true) == true -> AuthError.REQUIRES_RECENT_LOGIN
    message?.contains("requires-recent-login", ignoreCase = true) == true -> AuthError.REQUIRES_RECENT_LOGIN
    message?.contains("EMAIL_ALREADY_IN_USE", ignoreCase = true) == true -> AuthError.EMAIL_ALREADY_IN_USE
    message?.contains("email-already-in-use", ignoreCase = true) == true -> AuthError.EMAIL_ALREADY_IN_USE
    message?.contains("already in use", ignoreCase = true) == true -> AuthError.EMAIL_ALREADY_IN_USE
    message?.contains("INVALID_EMAIL", ignoreCase = true) == true -> AuthError.INVALID_EMAIL
    message?.contains("invalid-email", ignoreCase = true) == true -> AuthError.INVALID_EMAIL
    message?.contains("WRONG_PASSWORD", ignoreCase = true) == true -> AuthError.INVALID_CREDENTIALS
    message?.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) == true -> AuthError.INVALID_CREDENTIALS
    message?.contains("USER_NOT_FOUND", ignoreCase = true) == true -> AuthError.USER_NOT_FOUND
    message?.contains("WEAK_PASSWORD", ignoreCase = true) == true -> AuthError.WEAK_PASSWORD
    message?.contains("TOO_MANY_ATTEMPTS", ignoreCase = true) == true -> AuthError.TOO_MANY_REQUESTS
    message?.contains("NETWORK", ignoreCase = true) == true -> AuthError.NETWORK_ERROR
    message?.contains("unable to resolve host", ignoreCase = true) == true -> AuthError.NETWORK_ERROR
    message?.contains("failed to connect", ignoreCase = true) == true -> AuthError.NETWORK_ERROR
    // Transport / backend-config failures (timeouts, "internal error has occurred",
    // and the API-key block "Requests from this Android client ... are blocked").
    // These previously fell through to UNKNOWN ("Something went wrong"), which hid
    // the API-key/SHA allow-list misconfig that blocked Play-signed builds. Surface
    // an actionable retry/contact-support message instead. See runbook note.
    message?.contains("blocked", ignoreCase = true) == true -> AuthError.SERVICE_UNAVAILABLE
    message?.contains("internal error", ignoreCase = true) == true -> AuthError.SERVICE_UNAVAILABLE
    message?.contains("INTERNAL", ignoreCase = false) == true -> AuthError.SERVICE_UNAVAILABLE
    message?.contains("timeout", ignoreCase = true) == true -> AuthError.SERVICE_UNAVAILABLE
    message?.contains("timed out", ignoreCase = true) == true -> AuthError.SERVICE_UNAVAILABLE
    message?.contains("unavailable", ignoreCase = true) == true -> AuthError.SERVICE_UNAVAILABLE
    else -> AuthError.UNKNOWN
}

private fun SsoError.toAuthError(): AuthError = when (this) {
    SsoError.CANCELLED -> AuthError.SSO_CANCELLED
    SsoError.NO_PROVIDER -> AuthError.SSO_UNAVAILABLE
    SsoError.NETWORK -> AuthError.NETWORK_ERROR
    SsoError.UNKNOWN -> AuthError.UNKNOWN
}
