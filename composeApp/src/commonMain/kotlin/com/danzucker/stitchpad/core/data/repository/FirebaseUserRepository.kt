package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.data.dto.UserDto
import com.danzucker.stitchpad.core.data.mapper.toUser
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val TAG = "UserRepo"
private const val USERS = "users"
private val USER_SUBCOLLECTIONS = listOf("customers", "measurements", "orders", "styles", "goals")

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
            val document = firestore.collection(USERS).document(userId)
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
            firestore.collection(USERS).document(userId).delete()
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "deleteUserDoc failed userId=$userId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun updateProfile(
        userId: String,
        businessName: String?,
        displayName: String?,
        phoneNumber: String?,
        whatsappNumber: String?,
        avatarColorIndex: Int?
    ): EmptyResult<DataError.Network> {
        return try {
            val data = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp
            )
            businessName?.let { data["businessName"] = it }
            displayName?.let { data["displayName"] = it }
            phoneNumber?.let { data["phoneNumber"] = it }
            whatsappNumber?.let { data["whatsappNumber"] = it }
            avatarColorIndex?.let { data["avatarColorIndex"] = it }
            firestore.collection(USERS).document(userId).set(data, merge = true)
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "updateProfile failed userId=$userId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun deleteUserData(userId: String): EmptyResult<DataError.Network> {
        return try {
            val userDoc = firestore.collection(USERS).document(userId)
            USER_SUBCOLLECTIONS.forEach { subcollection ->
                runCatching {
                    val docs = userDoc.collection(subcollection).get().documents
                    docs.forEach { it.reference.delete() }
                }.onFailure { error ->
                    AppLogger.e(tag = TAG, throwable = error) {
                        "deleteUserData subcollection sweep failed sub=$subcollection userId=$userId"
                    }
                }
            }
            userDoc.delete()
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "deleteUserData failed userId=$userId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override fun observeUser(userId: String): Flow<User?> {
        return firestore.collection(USERS).document(userId).snapshots
            .map { snapshot ->
                if (!snapshot.exists) return@map null
                val dto = snapshot.data(UserDto.serializer())
                dto.copy(id = userId).toUser()
            }
            .catch { error ->
                AppLogger.e(tag = TAG, throwable = error) { "observeUser failed userId=$userId" }
                emit(null)
            }
    }
}
