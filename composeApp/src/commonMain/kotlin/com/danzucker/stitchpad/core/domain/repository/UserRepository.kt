package com.danzucker.stitchpad.core.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult

interface UserRepository {
    suspend fun createUserProfile(
        userId: String,
        businessName: String?,
        whatsappNumber: String?,
    ): EmptyResult<DataError.Network>
    suspend fun deleteUserDoc(userId: String): EmptyResult<DataError.Network>
}
