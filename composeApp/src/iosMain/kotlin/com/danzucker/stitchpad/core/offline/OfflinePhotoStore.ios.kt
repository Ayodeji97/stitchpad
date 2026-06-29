package com.danzucker.stitchpad.core.offline

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
actual class OfflinePhotoStore {
    actual suspend fun save(bytes: ByteArray, fileName: String): String {
        val dir = ensureOfflineUploadsDirectory()
        val path = "$dir/${fileName.safeFileName()}"
        val data = if (bytes.isEmpty()) {
            NSData.create(bytes = null, length = 0u)
        } else {
            bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
        }
        data.writeToFile(path, atomically = true)
        return path
    }

    actual suspend fun read(path: String): ByteArray {
        val data = NSData.dataWithContentsOfFile(path)
            ?: error("Offline upload file is unavailable")
        return data.toByteArray()
    }

    actual suspend fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    actual suspend fun readUploadJobs(): String? {
        val path = uploadJobsPath()
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        return data.toByteArray().decodeToString()
    }

    actual suspend fun writeUploadJobs(json: String) {
        ensureOfflineUploadsDirectory()
        json.encodeToByteArray().toNSData().writeToFile(uploadJobsPath(), atomically = true)
    }

    private fun ensureOfflineUploadsDirectory(): String {
        val dir = offlineUploadsDirectory()
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        // Exclude pending customer/style/order/logo images from iCloud/iTunes
        // backup — customer PII / business assets queued for upload, not user docs.
        NSURL.fileURLWithPath(dir, isDirectory = true)
            .setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
        return dir
    }

    private fun offlineUploadsDirectory(): String {
        val urls = NSFileManager.defaultManager.URLsForDirectory(
            directory = NSDocumentDirectory,
            inDomains = NSUserDomainMask,
        )
        val documents = urls.firstOrNull() as? NSURL
            ?: error("Documents directory is unavailable")
        return "${documents.path}/offline_uploads"
    }

    private fun uploadJobsPath(): String =
        "${offlineUploadsDirectory()}/upload_jobs.json"
}

private fun String.safeFileName(): String =
    replace(Regex("[^A-Za-z0-9._-]"), "_")

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        platform.posix.memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) {
        NSData.create(bytes = null, length = 0u)
    } else {
        usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    }
