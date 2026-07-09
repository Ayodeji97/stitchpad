package com.danzucker.stitchpad.core.sharing

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

actual class ImageSharer(private val context: Context) {

    actual suspend fun shareImage(bytes: ByteArray, caption: String?): Boolean {
        if (bytes.isEmpty()) return false
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val f = File(dir, "style_${System.currentTimeMillis()}.png")
            FileOutputStream(f).use { it.write(bytes) }
            pruneOld(dir)
            f
        }
        return withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                if (!caption.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(
                    Intent.createChooser(intent, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }

    private fun pruneOld(dir: File) {
        val files = dir.listFiles().orEmpty()
        if (files.size <= CACHE_LIMIT) return
        files.sortedByDescending { it.lastModified() }.drop(CACHE_LIMIT).forEach { it.delete() }
    }

    private companion object {
        const val CACHE_LIMIT = 10
    }
}
