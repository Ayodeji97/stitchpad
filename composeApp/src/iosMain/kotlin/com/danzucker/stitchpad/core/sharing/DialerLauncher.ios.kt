package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import kotlin.coroutines.resume

private const val TAG = "DialerLauncher"

actual class DialerLauncher {
    actual suspend fun launch(phone: String): Boolean {
        val normalised = normaliseNigerianPhone(phone)
        if (normalised.isBlank()) return false
        return withContext(Dispatchers.Main) {
            try {
                val nsUrl = NSURL.URLWithString("tel:$normalised") ?: return@withContext false
                val app = UIApplication.sharedApplication
                if (!app.canOpenURL(nsUrl)) {
                    AppLogger.w(tag = TAG) { "canOpenURL=false for tel: — trying anyway" }
                }
                suspendCancellableCoroutine { cont ->
                    app.openURL(
                        nsUrl,
                        options = emptyMap<Any?, Any?>(),
                        completionHandler = { success ->
                            if (cont.isActive) cont.resume(success)
                        }
                    )
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                AppLogger.w(tag = TAG, throwable = e) { "Failed to launch dialer" }
                false
            }
        }
    }
}
