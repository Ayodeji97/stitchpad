package com.danzucker.stitchpad.core.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImagePreferencesStore
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

actual class ReceiptImagePreferences(context: Context) : ReceiptImagePreferencesStore {
    private val prefs = context.getSharedPreferences("receipt_image_prefs", Context.MODE_PRIVATE)

    override fun observeStyle(): Flow<ReceiptImageStyle> = callbackFlow {
        trySend(readStyle())
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_STYLE) trySend(readStyle())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override suspend fun getStyle(): ReceiptImageStyle = readStyle()

    override suspend fun setStyle(style: ReceiptImageStyle) {
        prefs.edit { putString(KEY_STYLE, style.name) }
    }

    private fun readStyle(): ReceiptImageStyle =
        runCatching { ReceiptImageStyle.valueOf(prefs.getString(KEY_STYLE, null) ?: ReceiptImageStyle.LIGHT.name) }
            .getOrDefault(ReceiptImageStyle.LIGHT)

    companion object {
        private const val KEY_STYLE = "receipt_image_style"
    }
}
