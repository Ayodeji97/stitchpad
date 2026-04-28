package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

private const val TAG = "WhatsAppLauncher"

actual class WhatsAppLauncher {
    actual suspend fun launch(phone: String, message: String): Boolean {
        val url = buildWhatsAppUrl(phone, message)
        return withContext(Dispatchers.Main) {
            try {
                val nsUrl = NSURL.URLWithString(url) ?: return@withContext false
                val app = UIApplication.sharedApplication
                if (!app.canOpenURL(nsUrl)) {
                    AppLogger.w(tag = TAG) { "canOpenURL=false for wa.me — opening anyway" }
                }
                app.openURL(nsUrl, options = emptyMap<Any?, Any?>(), completionHandler = null)
                true
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                AppLogger.w(tag = TAG, throwable = e) { "Failed to launch WhatsApp" }
                false
            }
        }
    }
}
