package com.danzucker.stitchpad.feature.tutorials.data

/**
 * Platform disk cache for tutorial clips. Clips are tiny (~0.5–2MB) so a plain
 * download-to-file (mirroring OfflinePhotoStore) is enough — no streaming cache layer.
 * All methods are best-effort: failures return null and playback falls back to streaming.
 */
expect class TutorialVideoCache {
    /** A local `file://` uri for a fully-cached clip, or null if [id] is not cached yet. */
    fun cachedUri(id: String): String?

    /** Downloads [remoteUrl] and stores it under [id]; returns the local uri, or null on failure. */
    suspend fun download(id: String, remoteUrl: String): String?
}
