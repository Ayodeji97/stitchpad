package com.danzucker.stitchpad.feature.smart.data

import com.danzucker.stitchpad.feature.smart.domain.SmartUsageStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class InMemorySmartUsageStore : SmartUsageStore {
    private val state = MutableStateFlow<Int?>(null)
    override val remainingFreeQuota: StateFlow<Int?> = state.asStateFlow()

    override fun update(remaining: Int?) {
        state.value = remaining
    }
}
