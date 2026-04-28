package com.danzucker.stitchpad.core.sharing

/**
 * Launches a WhatsApp conversation with a pre-filled message via the cross-platform
 * `https://wa.me/<phone>?text=<message>` URL. Falls back to the device browser when
 * WhatsApp isn't installed (the wa.me URL itself handles that gracefully).
 */
expect class WhatsAppLauncher {
    /**
     * Opens a WhatsApp chat to [phone] with [message] pre-filled. The phone is normalised
     * to digits-only with country code (Nigerian numbers leading with `0` are converted
     * to `234...`). Returns true if the URL was launched, false on failure.
     */
    suspend fun launch(phone: String, message: String): Boolean
}
