package com.danzucker.stitchpad.core.presentation

import androidx.compose.ui.platform.UriHandler
import com.danzucker.stitchpad.core.logging.AppLogger

/**
 * [UriHandler.openUri] throws when no installed app can handle the URI —
 * on Android, `AndroidUriHandler` rethrows ActivityNotFoundException as
 * IllegalArgumentException. Every event-effect call site runs inside a
 * LaunchedEffect coroutine, so an unguarded call is a process-killing crash
 * (a tailor without WhatsApp tapping any "share via WhatsApp" action).
 *
 * Never log the URL: share links can carry invite/gift tokens.
 */
fun UriHandler.openUriSafely(url: String, tag: String) {
    runCatching { openUri(url) }
        .onFailure { throwable ->
            AppLogger.e(tag = tag, throwable = throwable) { "No handler available to open URI" }
        }
}
