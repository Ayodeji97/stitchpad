package com.danzucker.stitchpad.core.config.data

import com.danzucker.stitchpad.core.config.data.dto.AppConfigDto
import com.danzucker.stitchpad.core.config.data.mapper.toAppConfig
import com.danzucker.stitchpad.core.config.domain.model.AppConfig
import com.danzucker.stitchpad.core.config.domain.repository.AppConfigRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
            .onStart { emit(AppConfig.Disabled) }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observe app config failed" }
                emit(AppConfig.Disabled)
            }
}
