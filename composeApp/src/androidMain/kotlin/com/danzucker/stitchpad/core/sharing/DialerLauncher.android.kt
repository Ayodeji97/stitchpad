package com.danzucker.stitchpad.core.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.danzucker.stitchpad.core.logging.AppLogger

private const val TAG = "DialerLauncher"

actual class DialerLauncher(private val context: Context) {
    actual suspend fun launch(phone: String): Boolean {
        val normalised = normaliseNigerianPhone(phone)
        if (normalised.isBlank()) return false
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$normalised")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.w(tag = TAG, throwable = e) { "Failed to launch dialer" }
            false
        }
    }
}
