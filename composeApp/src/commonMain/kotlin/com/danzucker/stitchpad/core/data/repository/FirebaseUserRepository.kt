package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore

class FirebaseUserRepository(
    private val firestore: FirebaseFirestore
) : UserRepository {

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun createUserProfile(
        userId: String,
        businessName: String?,
        phone: String?
    ): EmptyResult<DataError.Network> {
        return try {
            val document = firestore.collection("users").document(userId)
            val exists = document.get().exists

            val data = if (exists) {
                mutableMapOf<String, Any>(
                    "updatedAt" to FieldValue.serverTimestamp
                )
            } else {
                mutableMapOf(
                    "subscriptionTier" to "free",
                    "subscriptionStatus" to "active",
                    "customerCount" to 0,
                    "createdAt" to FieldValue.serverTimestamp,
                    "updatedAt" to FieldValue.serverTimestamp
                )
            }
            businessName?.let { data["businessName"] = it }
            phone?.let { data["phone"] = it }
            document.set(data, merge = true)
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            Result.Error(DataError.Network.UNKNOWN)
        }
    }
}
