package com.danzucker.stitchpad.core.sharing

/**
 * Shares a single image (raw bytes) plus an optional caption via the platform
 * share sheet (Android ACTION_SEND chooser / iOS UIActivityViewController).
 * Feature-agnostic; used by style sharing and reusable elsewhere.
 *
 * Returns true when the share sheet was actually presented, false when it
 * could not be (e.g. empty/undecodable bytes, no key window, or no app to
 * handle the intent). Callers surface a failure to the user on false, so a
 * silent no-op never looks like success.
 */
expect class ImageSharer {
    suspend fun shareImage(bytes: ByteArray, caption: String?): Boolean
}
