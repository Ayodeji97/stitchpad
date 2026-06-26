package com.danzucker.stitchpad.feature.tutorials.data

import android.content.Context
import com.danzucker.stitchpad.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private const val TAG = "TutorialVideoCache"
private const val CACHE_DIR = "tutorial_videos"

actual class TutorialVideoCache(
    private val context: Context,
) {
    actual fun cachedUri(id: String): String? =
        fileFor(id).takeIf { it.exists() && it.length() > 0 }?.let { "file://${it.absolutePath}" }

    actual suspend fun download(id: String, remoteUrl: String): String? = withContext(Dispatchers.IO) {
        val target = fileFor(id)
        if (target.exists() && target.length() > 0) return@withContext "file://${target.absolutePath}"
        runCatching {
            val bytes = URL(remoteUrl).openStream().use { it.readBytes() }
            val tmp = File(target.parentFile, "${target.name}.part")
            tmp.writeBytes(bytes)
            tmp.renameTo(target)
            "file://${target.absolutePath}"
        }.getOrElse { throwable ->
            AppLogger.e(tag = TAG, throwable = throwable) { "download failed id=$id" }
            runCatching { File(target.parentFile, "${target.name}.part").delete() }
            null
        }
    }

    private fun fileFor(id: String): File {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        return File(dir, "${id.replace(Regex("[^A-Za-z0-9._-]"), "_")}.mp4")
    }
}
