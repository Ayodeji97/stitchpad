package com.danzucker.stitchpad.feature.billing.data

import com.danzucker.stitchpad.feature.billing.domain.EntitlementsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * V2 default: everyone is premium. Holds the entitlement state in process so a
 * Settings toggle can flip it for paywall preview without round-tripping a
 * billing SDK. Replace this binding when real billing lands.
 */
class InMemoryEntitlementsRepository(
    initialIsPremium: Boolean = true
) : EntitlementsRepository {

    private val state = MutableStateFlow(initialIsPremium)

    override fun observeIsPremium(): Flow<Boolean> = state.asStateFlow()

    override suspend fun setIsPremium(value: Boolean) {
        state.value = value
    }
}
