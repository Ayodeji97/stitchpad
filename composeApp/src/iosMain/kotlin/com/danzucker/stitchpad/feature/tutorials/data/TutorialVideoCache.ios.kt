package com.danzucker.stitchpad.feature.tutorials.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile

/**
 * iOS tutorial clip cache. Stores clips under Caches/ (the OS may evict them under storage
 * pressure, which is fine — playback re-downloads). [download] blocks on a background
 * dispatcher via the synchronous NSData(contentsOfURL:) — acceptable for ~1–2MB clips.
 */
@OptIn(ExperimentalForeignApi::class)
actual class TutorialVideoCache {

    actual fun cachedUri(id: String): String? {
        val path = filePath(id)
        return if (NSFileManager.defaultManager.fileExistsAtPath(path)) "file://$path" else null
    }

    actual suspend fun download(id: String, remoteUrl: String): String? = withContext(Dispatchers.Default) {
        val path = filePath(id)
        if (NSFileManager.defaultManager.fileExistsAtPath(path)) return@withContext "file://$path"
        val url = NSURL.URLWithString(remoteUrl) ?: return@withContext null
        val data = NSData.dataWithContentsOfURL(url) ?: return@withContext null
        if (data.writeToFile(path, atomically = true)) "file://$path" else null
    }

    private fun filePath(id: String): String {
        val urls = NSFileManager.defaultManager.URLsForDirectory(
            directory = NSCachesDirectory,
            inDomains = NSUserDomainMask,
        )
        val caches = (urls.firstOrNull() as? NSURL)?.path ?: error("Caches directory is unavailable")
        val dir = "$caches/tutorial_videos"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        val safe = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "$dir/$safe.mp4"
    }
}
