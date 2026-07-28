package com.danzucker.stitchpad.core.domain.preferences

import kotlinx.coroutines.flow.Flow

interface ReceiptImagePreferencesStore {
    fun observeStyle(): Flow<ReceiptImageStyle>
    suspend fun getStyle(): ReceiptImageStyle
    suspend fun setStyle(style: ReceiptImageStyle)
}
