package com.danzucker.stitchpad.core.config.domain.repository

import com.danzucker.stitchpad.core.config.domain.model.AppConfig
import kotlinx.coroutines.flow.Flow

interface AppConfigRepository {
    /**
     * Hot stream of remote app config, backed by a Firestore snapshot listener.
     * Emits [AppConfig.Disabled] before first load and on any read error, so
     * consumers never see a broken/partial config.
     */
    val config: Flow<AppConfig>
}
