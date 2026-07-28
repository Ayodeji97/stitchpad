package com.danzucker.stitchpad.core.config

import com.danzucker.stitchpad.core.config.data.dto.AppConfigDto
import com.danzucker.stitchpad.core.config.data.mapper.toAppConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppConfigMapperTest {
    @Test
    fun settingsHubEnabled_defaultsFalse_whenAbsent() {
        assertFalse(AppConfigDto().toAppConfig().settingsHubEnabled)
    }

    @Test
    fun settingsHubEnabled_mapsThrough_whenTrue() {
        assertEquals(true, AppConfigDto(settingsHubEnabled = true).toAppConfig().settingsHubEnabled)
    }
}
