package com.danzucker.stitchpad.core.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.danzucker.stitchpad.core.logging.AppLogger

private const val TAG = "WhatsAppLauncher"

actual class WhatsAppLauncher(private val context: Context) {
    actual suspend fun launch(phone: String, message: String): Boolean {
        val url = buildWhatsAppUrl(phone, message)
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.w(tag = TAG, throwable = e) { "Failed to launch WhatsApp" }
            false
        }
    }
}
