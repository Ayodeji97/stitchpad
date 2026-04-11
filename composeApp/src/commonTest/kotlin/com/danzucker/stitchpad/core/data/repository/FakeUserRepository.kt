package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.UserRepository

class FakeUserRepository : UserRepository {
    var shouldReturnError: DataError.Network? = null
    var lastUserId: String? = null
    var lastBusinessName: String? = null
    var lastPhone: String? = null

    override suspend fun createUserProfile(
        userId: String,
        businessName: String?,
        phone: String?
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastUserId = userId
        lastBusinessName = businessName
        lastPhone = phone
        return Result.Success(Unit)
    }
}
