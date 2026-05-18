package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.data.dto.UserDto
import com.danzucker.stitchpad.core.data.mapper.toUser
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
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
                buildInitialUserDoc()
            }
            businessName?.let { data["businessName"] = it }
            whatsappNumber?.let { data["whatsapp"] = it }
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
            // Required fields: a null here would be a programming error (the UI
            // never allows clearing them), so we skip the write defensively.
            businessName?.let { data["businessName"] = it }
            avatarColorIndex?.let { data["avatarColorIndex"] = it }
            // Optional fields: null is the explicit "clear this field" signal —
            // the user blanked the input in Edit Profile. Use FieldValue.delete
            // so the Firestore document drops the key instead of retaining the
            // old value (which would silently survive a "save with cleared field").
            data["displayName"] = displayName ?: FieldValue.delete
            // phoneNumber → Firestore `phone` (optional voice line).
            // whatsappNumber → Firestore `whatsapp` (optional primary contact).
            // Distinct slots; not aliases of each other.
            data["phone"] = phoneNumber ?: FieldValue.delete
            data["whatsapp"] = whatsappNumber ?: FieldValue.delete
            // Always clear the legacy `whatsappNumber` field on save. Without
            // this, a migrated user clearing the WhatsApp input would still
            // see the old value because UserMapper falls back to the legacy
            // slot when `whatsapp` is null.
            data["whatsappNumber"] = FieldValue.delete
            firestore.collection(USERS).document(userId).set(data, merge = true)
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "updateProfile failed userId=$userId" }
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

    /**
     * Initial user-doc shape written on first signup. Includes the
     * V1.0 freemium fields: welcome-bonus marker so EntitlementsCalculator
     * grants the 30-customer cap for the first calendar month, and a
     * `bonusCoins` field that's a fast path for the client UI (server
     * is still source of truth via the usage doc).
     *
     * Note: bonusCoins on the user doc is for display only — the server's
     * usage-doc `bonusBalance` is what actually gates Smart help. They're
     * seeded to the same value here so they're consistent at signup.
     */
    private fun buildInitialUserDoc(): MutableMap<String, Any> = mutableMapOf(
        "subscriptionTier" to SubscriptionTier.FREE.wireValue,
        "subscriptionStatus" to "active",
        "subscriptionRenews" to false,
        "customerCount" to 0,
        "welcomeBonusAppliedAt" to FieldValue.serverTimestamp,
        "bonusCoins" to WELCOME_BONUS_COIN_COUNT,
        "createdAt" to FieldValue.serverTimestamp,
        "updatedAt" to FieldValue.serverTimestamp
    )

    companion object {
        const val WELCOME_BONUS_COIN_COUNT: Int = 30
    }
}
