package com.danzucker.stitchpad.core.config

import com.danzucker.stitchpad.core.config.domain.model.AppConfig
import com.danzucker.stitchpad.core.config.domain.repository.AppConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppConfigRepository(
    initial: AppConfig = AppConfig.Disabled,
) : AppConfigRepository {
    private val _config = MutableStateFlow(initial)
    override val config: Flow<AppConfig> = _config

    fun emit(config: AppConfig) {
        _config.value = config
    }
}
