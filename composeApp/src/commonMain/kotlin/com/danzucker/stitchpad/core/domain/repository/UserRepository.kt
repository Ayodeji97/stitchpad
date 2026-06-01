package com.danzucker.stitchpad.core.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun createUserProfile(
        userId: String,
        businessName: String?,
        whatsappNumber: String?,
        bankName: String? = null,
        bankAccountName: String? = null,
        bankAccountNumber: String? = null,
        whatsappConfirmed: Boolean = false,
    ): EmptyResult<DataError.Network>

    suspend fun deleteUserDoc(userId: String): EmptyResult<DataError.Network>

    @Suppress("LongParameterList")
    suspend fun updateProfile(
        userId: String,
        businessName: String?,
        displayName: String?,
        phoneNumber: String?,
        whatsappNumber: String?,
        avatarColorIndex: Int?,
        bankName: String? = null,
        bankAccountName: String? = null,
        bankAccountNumber: String? = null,
        whatsappConfirmed: Boolean = false,
    ): EmptyResult<DataError.Network>

    fun observeUser(userId: String): Flow<User?>

    /**
     * Uploads `bytes` to a deterministic Firebase Storage path (`users/{userId}/branding/logo.jpg`).
     * Overwrites any existing object at that path. Caller is responsible for invoking
     * [updateBrandLogo] afterwards to persist the returned (downloadUrl, storagePath) on the user doc.
     */
    suspend fun uploadUserLogo(
        userId: String,
        bytes: ByteArray,
    ): Result<Pair<String, String>, DataError.Network>

    /**
     * Writes both `businessLogoUrl` and `businessLogoStoragePath` to `users/{userId}`. Passing
     * (null, null) explicitly clears the logo. Uses `FieldValue.delete` for clears so the keys
     * actually drop from the Firestore document (matches the `updateProfile` pattern).
     */
    suspend fun updateBrandLogo(
        userId: String,
        logoUrl: String?,
        logoStoragePath: String?,
    ): EmptyResult<DataError.Network>

    /**
     * Deletes the Storage object at [storagePath]. Safe to call on a non-existent object
     * (treated as success). Does NOT update the user doc — callers must coordinate with
     * [updateBrandLogo] when removing a logo permanently.
     */
    suspend fun deleteUserLogo(
        storagePath: String,
    ): EmptyResult<DataError.Network>
}
