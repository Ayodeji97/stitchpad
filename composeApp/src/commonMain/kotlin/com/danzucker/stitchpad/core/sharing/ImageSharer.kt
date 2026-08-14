package com.danzucker.stitchpad.core.sharing

/**
 * Shares a single image (raw bytes) plus an optional caption — or a plain text
 * payload — via the platform share sheet (Android ACTION_SEND chooser / iOS
 * UIActivityViewController). Feature-agnostic; used by style sharing and the
 * team invite link, reusable elsewhere.
 *
 * Returns true when the share sheet was actually presented, false when it
 * could not be (e.g. empty/undecodable bytes, no key window, or no app to
 * handle the intent). Callers surface a failure to the user on false, so a
 * silent no-op never looks like success.
 */
expect class ImageSharer {
    suspend fun shareImage(bytes: ByteArray, caption: String?): Boolean

    /** Text-only share (invite links, codes): the user picks the target app. */
    suspend fun shareText(text: String): Boolean
}
