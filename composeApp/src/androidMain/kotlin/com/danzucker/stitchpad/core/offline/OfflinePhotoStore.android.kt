package com.danzucker.stitchpad.core.offline

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual class OfflinePhotoStore(
    private val context: Context,
) {
    actual suspend fun save(bytes: ByteArray, fileName: String): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "offline_uploads").apply { mkdirs() }
        val file = File(dir, fileName.safeFileName())
        file.writeBytes(bytes)
        file.absolutePath
    }

    actual suspend fun read(path: String): ByteArray = withContext(Dispatchers.IO) {
        File(path).readBytes()
    }

    actual suspend fun delete(path: String) {
        withContext(Dispatchers.IO) {
            runCatching { File(path).delete() }
        }
    }

    actual suspend fun readUploadJobs(): String? = withContext(Dispatchers.IO) {
        uploadJobsFile().takeIf { it.exists() }?.readText()
    }

    actual suspend fun writeUploadJobs(json: String) {
        withContext(Dispatchers.IO) {
            uploadJobsFile().apply {
                parentFile?.mkdirs()
                writeText(json)
            }
        }
    }

    private fun uploadJobsFile(): File =
        File(context.filesDir, "offline_uploads/upload_jobs.json")
}

private fun String.safeFileName(): String =
    replace(Regex("[^A-Za-z0-9._-]"), "_")
