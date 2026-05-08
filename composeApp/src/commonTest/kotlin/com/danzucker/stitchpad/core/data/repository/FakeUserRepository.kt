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
    var deleteUserDataCalled: Boolean = false
    val userFlow = MutableStateFlow<User?>(null)

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

    override suspend fun deleteUserData(userId: String): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        deleteUserDataCalled = true
        lastUserId = userId
        return Result.Success(Unit)
    }

    override fun observeUser(userId: String): Flow<User?> = userFlow
}
