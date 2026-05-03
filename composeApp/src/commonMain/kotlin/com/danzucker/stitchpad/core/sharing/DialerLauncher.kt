package com.danzucker.stitchpad.core.sharing

/**
 * Opens the platform dialer with the phone number prefilled (no auto-call).
 * Android: ACTION_DIAL with tel: URI. iOS: UIApplication.openURL with tel: URL.
 * Returns true on launch, false if the URL/intent could not be handled.
 */
expect class DialerLauncher {
    suspend fun launch(phone: String): Boolean
}
