package com.danzucker.stitchpad.core.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun createUserProfile(
        userId: String,
        businessName: String?,
        whatsappNumber: String?,
    ): EmptyResult<DataError.Network>

    suspend fun deleteUserDoc(userId: String): EmptyResult<DataError.Network>

    suspend fun updateProfile(
        userId: String,
        businessName: String?,
        displayName: String?,
        phoneNumber: String?,
        whatsappNumber: String?,
        avatarColorIndex: Int?
    ): EmptyResult<DataError.Network>

    suspend fun deleteUserData(userId: String): EmptyResult<DataError.Network>

    fun observeUser(userId: String): Flow<User?>
}
