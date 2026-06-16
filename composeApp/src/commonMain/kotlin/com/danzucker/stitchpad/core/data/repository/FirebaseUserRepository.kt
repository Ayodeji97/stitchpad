@file:Suppress("TooManyFunctions")

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
import com.danzucker.stitchpad.core.offline.OfflinePhotoStore
import com.danzucker.stitchpad.core.offline.OfflineUploadJob
import com.danzucker.stitchpad.core.offline.OfflineUploadJobType
import com.danzucker.stitchpad.core.offline.OfflineUploadOutbox
import com.danzucker.stitchpad.core.offline.OfflineWriteDispatcher
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private const val TAG = "UserRepo"
private const val USERS = "users"

class FirebaseUserRepository(
    private val firestore: FirebaseFirestore,
    private val offlineWrites: OfflineWriteDispatcher,
    private val photoStore: OfflinePhotoStore,
    private val uploadOutbox: OfflineUploadOutbox,
) : UserRepository {

    private fun logoStoragePath(userId: String): String =
        "users/$userId/branding/logo.jpg"

    private fun logoUploadJobId(storagePath: String): String =
        "${OfflineUploadJobType.PROFILE_LOGO}:$storagePath"

    private fun logoStorageDeleteJobId(storagePath: String): String =
        "${OfflineUploadJobType.STORAGE_DELETE}:$storagePath"

    private fun userIdFromLogoPath(storagePath: String): String? =
        storagePath
            .takeIf { it.startsWith("users/") && it.endsWith("/branding/logo.jpg") }
            ?.substringAfter("users/")
            ?.substringBefore("/branding/logo.jpg")
            ?.takeIf { it.isNotBlank() }

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun createUserProfile(
        userId: String,
        businessName: String?,
        whatsappNumber: String?,
        bankName: String?,
        bankAccountName: String?,
        bankAccountNumber: String?,
        whatsappConfirmed: Boolean,
    ): EmptyResult<DataError.Network> {
        val document = firestore.collection(USERS).document(userId)
        val data = mutableMapOf<String, Any>()
        businessName?.let { data["businessName"] = it }
        whatsappNumber?.let { data["whatsapp"] = it }
        // Boolean (not null-guarded): always reflects the current confirm state.
        // The form layer sends false when there is no number or it was edited.
        data["whatsappConfirmed"] = whatsappConfirmed
        bankName?.let { data["bankName"] = it }
        bankAccountName?.let { data["bankAccountName"] = it }
        bankAccountNumber?.let { data["bankAccountNumber"] = it }
        data["updatedAt"] = FieldValue.serverTimestamp
        // Profile fields are fire-and-forget so Workshop Setup returns instantly
        // offline (the whole point of OfflineWriteDispatcher). They never include
        // the billing/entitlement defaults — those are seeded once below.
        val accepted = offlineWrites.enqueue("createUserProfile userId=$userId") {
            document.set(data, merge = true)
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        // Seed the billing/entitlement defaults exactly once, at first creation.
        // A transaction (not the old blocking `document.get().exists` pre-read,
        // which stalled the form offline) guards the write so re-entering Workshop
        // Setup — skip-then-complete, reinstall, or a doc that already exists on the
        // server — can NEVER reset a paying user's tier or re-grant the welcome
        // window. We key off `subscriptionTier`, not doc existence, because the
        // fire-and-forget profile write above may have created the doc first;
        // absence of the tier field is the real "not yet seeded" signal.
        // Enqueued fire-and-forget: the transaction needs the server, so it lands
        // once connectivity is available (sign-up is always online). Until then the
        // entitlements provider safely treats the user as FREE.
        offlineWrites.enqueue("seedInitialUserDoc userId=$userId") {
            firestore.runTransaction {
                val snapshot = get(document)
                val existingTier =
                    if (snapshot.exists) snapshot.data<UserSeedStateDto>().subscriptionTier else null
                if (shouldSeedInitialUserDoc(snapshot.exists, existingTier)) {
                    set(document, buildInitialUserDoc(), merge = true)
                }
            }
        }
        return Result.Success(Unit)
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
        avatarColorIndex: Int?,
        bankName: String?,
        bankAccountName: String?,
        bankAccountNumber: String?,
        whatsappConfirmed: Boolean,
    ): EmptyResult<DataError.Network> {
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
        data["whatsappConfirmed"] = whatsappConfirmed
        // Always clear the legacy `whatsappNumber` field on save. Without
        // this, a migrated user clearing the WhatsApp input would still
        // see the old value because UserMapper falls back to the legacy
        // slot when `whatsapp` is null.
        data["whatsappNumber"] = FieldValue.delete
        // Bank fields are a logical group (all set or all cleared). Validation
        // in EditProfileViewModel enforces this; here we just honor whatever
        // came in and use FieldValue.delete for nulls so cleared values
        // actually drop from the document.
        data["bankName"] = bankName ?: FieldValue.delete
        data["bankAccountName"] = bankAccountName ?: FieldValue.delete
        data["bankAccountNumber"] = bankAccountNumber ?: FieldValue.delete
        val accepted = offlineWrites.enqueue("updateProfile userId=$userId") {
            firestore.collection(USERS).document(userId).set(data, merge = true)
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
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

    @Suppress("INLINE_FROM_HIGHER_PLATFORM", "TooGenericExceptionCaught")
    override suspend fun hasWorkshopProfile(userId: String): Boolean {
        return try {
            val snapshot = firestore.collection(USERS).document(userId).get()
            if (!snapshot.exists) return false
            val dto = snapshot.data(UserDto.serializer())
            !dto.businessName.isNullOrBlank()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline / not-yet-cached / malformed doc: fall back to showing setup.
            AppLogger.w(tag = TAG, throwable = e) { "hasWorkshopProfile read failed userId=$userId" }
            false
        }
    }

    override suspend fun uploadUserLogo(
        userId: String,
        bytes: ByteArray,
    ): Result<Pair<String, String>, DataError.Network> {
        val path = logoStoragePath(userId)
        return try {
            val localPath = photoStore.save(bytes, "user-logo-$userId.jpg")
            Result.Success(localPath to path)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Callers (ViewModels) cancel in-flight uploads when the user picks again
            // or skips. We must not convert cancellation into a Result.Error — that
            // would race the cancelled coroutine's failure path against the newer state.
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "uploadUserLogo failed userId=$userId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun updateBrandLogo(
        userId: String,
        logoUrl: String?,
        logoStoragePath: String?,
    ): EmptyResult<DataError.Network> {
        val data = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp)
        if (logoUrl == null && logoStoragePath == null) {
            data["businessLogoUrl"] = FieldValue.delete
            data["businessLogoStoragePath"] = FieldValue.delete
            data["businessLogoUploadId"] = FieldValue.delete
        } else if (logoUrl != null && logoStoragePath != null) {
            // Keep the current remote URL visible while the replacement is queued.
            data["businessLogoStoragePath"] = logoStoragePath
            data["businessLogoUploadId"] = logoUploadJobId(logoStoragePath)
        }
        val accepted = offlineWrites.enqueue("updateBrandLogo userId=$userId") {
            firestore.collection(USERS).document(userId).set(data, merge = true)
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return try {
            if (logoUrl != null && logoStoragePath != null) {
                uploadOutbox.cancel(logoStorageDeleteJobId(logoStoragePath))
                uploadOutbox.enqueue(
                    OfflineUploadJob(
                        id = logoUploadJobId(logoStoragePath),
                        type = OfflineUploadJobType.PROFILE_LOGO,
                        userId = userId,
                        storagePath = logoStoragePath,
                        localPath = logoUrl,
                    )
                )
            }
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "updateBrandLogo failed userId=$userId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun deleteUserLogo(
        storagePath: String,
    ): EmptyResult<DataError.Network> {
        return try {
            uploadOutbox.cancel(logoUploadJobId(storagePath))
            userIdFromLogoPath(storagePath)?.let { userId ->
                val accepted = offlineWrites.enqueue("clearDeletedUserLogo userId=$userId") {
                    firestore.collection(USERS).document(userId).update(
                        "businessLogoUrl" to FieldValue.delete,
                        "businessLogoStoragePath" to FieldValue.delete,
                        "businessLogoUploadId" to FieldValue.delete,
                        "updatedAt" to FieldValue.serverTimestamp,
                    )
                }
                if (!accepted) {
                    AppLogger.w(tag = TAG) {
                        "clearDeletedUserLogo not locally accepted userId=$userId"
                    }
                }
            }
            uploadOutbox.enqueue(
                OfflineUploadJob(
                    id = logoStorageDeleteJobId(storagePath),
                    type = OfflineUploadJobType.STORAGE_DELETE,
                    userId = "",
                    storagePath = storagePath,
                )
            )
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // A delete on a non-existent object throws; treat as success so callers can
            // fire-and-forget on Skip without surfacing a benign 404 to the user.
            AppLogger.w(tag = TAG, throwable = e) { "deleteUserLogo treated as no-op path=$storagePath" }
            Result.Success(Unit)
        }
    }

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun setDailyDigestEmailEnabled(
        userId: String,
        enabled: Boolean,
    ): EmptyResult<DataError.Network> {
        val data = mapOf<String, Any>(
            "dailyDigestEmailEnabled" to enabled,
            "updatedAt" to FieldValue.serverTimestamp,
        )
        val accepted = offlineWrites.enqueue("setDailyDigestEmailEnabled userId=$userId") {
            firestore.collection(USERS).document(userId).set(data, merge = true)
        }
        return if (accepted) Result.Success(Unit) else Result.Error(DataError.Network.UNKNOWN)
    }

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun setDailyPushEnabled(userId: String, enabled: Boolean): EmptyResult<DataError.Network> {
        val data = mapOf<String, Any>(
            "dailyPushEnabled" to enabled,
            "updatedAt" to FieldValue.serverTimestamp,
        )
        val accepted = offlineWrites.enqueue("setDailyPushEnabled userId=$userId") {
            firestore.collection(USERS).document(userId).set(data, merge = true)
        }
        return if (accepted) Result.Success(Unit) else Result.Error(DataError.Network.UNKNOWN)
    }

    companion object {
        const val WELCOME_BONUS_COIN_COUNT: Int = 30
    }

    /**
     * Initial user-doc shape, seeded exactly once when the doc is first created
     * (guarded by the transaction in [createUserProfile]). Profile edit paths and
     * repeat Workshop Setup completions never rewrite these
     * billing/entitlement-owned fields, so a paying user can't be reset to FREE
     * or have their welcome window re-granted.
     */
    private fun buildInitialUserDoc(): MutableMap<String, Any> = mutableMapOf(
        "subscriptionTier" to SubscriptionTier.FREE.wireValue,
        "subscriptionStatus" to "active",
        "subscriptionRenews" to false,
        "customerCount" to 0,
        "welcomeBonusAppliedAt" to FieldValue.serverTimestamp,
        "bonusCoins" to WELCOME_BONUS_COIN_COUNT,
        "createdAt" to FieldValue.serverTimestamp,
        "updatedAt" to FieldValue.serverTimestamp,
    )
}

/**
 * The slice of `users/{uid}` the seed guard reads. A typed DTO (not
 * `Map<String, Any?>`) because kotlinx.serialization on Kotlin/Native has no
 * runtime serializer for `Any?` — see [UserDto] / the entitlements provider for
 * the same constraint. Unknown fields are ignored by GitLive's decoder.
 */
@Serializable
private data class UserSeedStateDto(
    val subscriptionTier: String? = null,
)

/**
 * Whether [com.danzucker.stitchpad.core.domain.repository.UserRepository.createUserProfile]
 * should seed the one-time billing/entitlement defaults. Seed only when the doc
 * has never been seeded — i.e. it doesn't exist yet, or it exists (the
 * fire-and-forget profile write may have created it) but carries no
 * `subscriptionTier`. A doc that already has a tier belongs to a returning user
 * whose billing state must be preserved.
 *
 * Pure + `internal` so the reseed guard is unit-testable without a Firestore fake.
 */
internal fun shouldSeedInitialUserDoc(
    docExists: Boolean,
    existingSubscriptionTier: String?,
): Boolean = !docExists || existingSubscriptionTier == null
