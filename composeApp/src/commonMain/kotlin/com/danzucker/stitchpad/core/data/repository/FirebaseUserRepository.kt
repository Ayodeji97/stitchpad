package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore

private const val TAG = "UserRepo"

class FirebaseUserRepository(
    private val firestore: FirebaseFirestore
) : UserRepository {

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun createUserProfile(
        userId: String,
        businessName: String?,
        whatsappNumber: String?,
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
            whatsappNumber?.let { data["whatsappNumber"] = it }
            document.set(data, merge = true)
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "createUserProfile failed userId=$userId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun deleteUserDoc(userId: String): EmptyResult<DataError.Network> {
        return try {
            firestore.collection("users").document(userId).delete()
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "deleteUserDoc failed userId=$userId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }
}
