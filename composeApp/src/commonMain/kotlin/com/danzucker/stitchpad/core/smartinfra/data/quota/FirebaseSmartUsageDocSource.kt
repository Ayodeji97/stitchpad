package com.danzucker.stitchpad.core.smartinfra.data.quota

import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageDocSource
import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val TAG = "SmartUsageDocSrc"
private const val USERS = "users"
private const val USAGE = "usage"
private const val SMART_DRAFTS = "smart_drafts"

class FirebaseSmartUsageDocSource(
    private val firestore: FirebaseFirestore,
) : SmartUsageDocSource {

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override fun observeSnapshot(userId: String): Flow<SmartUsageSnapshot> {
        return firestore.collection(USERS).document(userId)
            .collection(USAGE).document(SMART_DRAFTS)
            .snapshots
            .map { snapshot ->
                if (!snapshot.exists) return@map SmartUsageSnapshot.Empty
                val dto = snapshot.data(SmartUsageDto.serializer())
                SmartUsageSnapshot(
                    bonusBalance = dto.bonusBalance,
                    monthlyCount = dto.count,
                )
            }
            .catch { error ->
                AppLogger.e(tag = TAG, throwable = error) {
                    "observeSnapshot failed userId=$userId"
                }
                emit(SmartUsageSnapshot.Empty)
            }
    }
}
