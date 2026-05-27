package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUserRepository : UserRepository {
    var shouldReturnError: DataError.Network? = null
    var lastUserId: String? = null
    var lastBusinessName: String? = null
    var lastWhatsAppNumber: String? = null
    var deletedUserId: String? = null
    var lastPhone: String? = null
    var lastDisplayName: String? = null
    var lastAvatarColorIndex: Int? = null
    val userFlow = MutableStateFlow<User?>(null)

    /** Paths passed to [deleteUserLogo], in call order. */
    val deletedLogoPaths: MutableList<String> = mutableListOf()

    /** Last (url, path) pair passed to [updateBrandLogo], or null if never called. */
    var lastBrandLogoUpdate: Pair<String?, String?>? = null

    /**
     * Optional override for [uploadUserLogo]. When non-null, this result is returned
     * regardless of [shouldReturnError]. Set to null to use the default behaviour.
     */
    var uploadLogoResult: Result<Pair<String, String>, DataError.Network>? = null

    override suspend fun createUserProfile(
        userId: String,
        businessName: String?,
        whatsappNumber: String?,
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastUserId = userId
        lastBusinessName = businessName
        lastWhatsAppNumber = whatsappNumber
        return Result.Success(Unit)
    }

    override suspend fun deleteUserDoc(userId: String): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        deletedUserId = userId
        return Result.Success(Unit)
    }

    override suspend fun updateProfile(
        userId: String,
        businessName: String?,
        displayName: String?,
        phoneNumber: String?,
        whatsappNumber: String?,
        avatarColorIndex: Int?
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastUserId = userId
        lastBusinessName = businessName
        lastDisplayName = displayName
        lastPhone = phoneNumber
        lastWhatsAppNumber = whatsappNumber
        lastAvatarColorIndex = avatarColorIndex
        return Result.Success(Unit)
    }

    override fun observeUser(userId: String): Flow<User?> = userFlow

    override suspend fun uploadUserLogo(
        userId: String,
        bytes: ByteArray,
    ): Result<Pair<String, String>, DataError.Network> {
        uploadLogoResult?.let { return it }
        shouldReturnError?.let { return Result.Error(it) }
        return Result.Success("https://example.com/logo.jpg" to "users/$userId/branding/logo.jpg")
    }

    override suspend fun updateBrandLogo(
        userId: String,
        logoUrl: String?,
        logoStoragePath: String?,
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastBrandLogoUpdate = logoUrl to logoStoragePath
        return Result.Success(Unit)
    }

    override suspend fun deleteUserLogo(
        storagePath: String,
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        deletedLogoPaths.add(storagePath)
        return Result.Success(Unit)
    }
}
