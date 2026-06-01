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
     * Stages `bytes` for the deterministic Firebase Storage path
     * (`users/{userId}/branding/logo.jpg`). Caller passes the returned
     * (localPath, storagePath) to [updateBrandLogo], which stores the pending
     * path and lets the upload outbox patch the final download URL.
     */
    suspend fun uploadUserLogo(
        userId: String,
        bytes: ByteArray,
    ): Result<Pair<String, String>, DataError.Network>

    /**
     * Stores pending logo state on `users/{userId}` and queues the staged local
     * file for upload. Passing (null, null) explicitly clears the logo.
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
