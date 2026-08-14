package com.danzucker.stitchpad.core.config.data

import com.danzucker.stitchpad.core.config.data.dto.AppConfigDto
import com.danzucker.stitchpad.core.config.data.mapper.toAppConfig
import com.danzucker.stitchpad.core.config.domain.model.AppConfig
import com.danzucker.stitchpad.core.config.domain.repository.AppConfigRepository
import com.danzucker.stitchpad.core.data.retryWithFallback
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

private const val TAG = "AppConfigRepo"
private const val CONFIG_COLLECTION = "config"
private const val CONFIG_DOC_ID = "app"

class FirebaseAppConfigRepository(
    private val firestore: FirebaseFirestore,
) : AppConfigRepository {

    override val config: Flow<AppConfig> =
        firestore.collection(CONFIG_COLLECTION)
            .document(CONFIG_DOC_ID)
            .snapshots
            .map { snapshot ->
                if (snapshot.exists) {
                    runCatching { snapshot.data<AppConfigDto>().toAppConfig() }
                        .getOrElse { AppConfig.Disabled }
                } else {
                    AppConfig.Disabled
                }
            }
            .retryWithFallback(fallback = AppConfig.Disabled) { throwable, attempt ->
                AppLogger.w(tag = TAG, throwable = throwable) {
                    "observe app config failed; retrying (attempt ${attempt + 1})"
                }
            }
            // onStart DOWNSTREAM of the retry (matching FirebaseTutorialsRepository):
            // upstream of it, every retry resubscription re-fires onStart, and that
            // emission resets retryWithFallback's errors-since-last-emission counter —
            // pinning a permanently-failing listener at the initial 500ms backoff for
            // process life (cursor, PR #360). Down here it fires once per collection.
            .onStart { emit(AppConfig.Disabled) }
}
