package com.danzucker.stitchpad.core.data.preferences

import com.danzucker.stitchpad.core.domain.preferences.ReceiptImagePreferencesStore
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

actual class ReceiptImagePreferences : ReceiptImagePreferencesStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val flow = MutableStateFlow(readStyle())

    override fun observeStyle(): Flow<ReceiptImageStyle> = flow.asStateFlow()

    override suspend fun getStyle(): ReceiptImageStyle = readStyle()

    override suspend fun setStyle(style: ReceiptImageStyle) {
        defaults.setObject(style.name, forKey = KEY_STYLE)
        flow.value = style
    }

    private fun readStyle(): ReceiptImageStyle =
        runCatching { ReceiptImageStyle.valueOf(defaults.stringForKey(KEY_STYLE) ?: ReceiptImageStyle.LIGHT.name) }
            .getOrDefault(ReceiptImageStyle.LIGHT)

    companion object {
        private const val KEY_STYLE = "receipt_image_style"
    }
}
