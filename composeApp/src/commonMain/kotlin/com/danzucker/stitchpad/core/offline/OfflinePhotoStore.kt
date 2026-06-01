package com.danzucker.stitchpad.core.offline

expect class OfflinePhotoStore {
    suspend fun save(bytes: ByteArray, fileName: String): String
    suspend fun read(path: String): ByteArray
    suspend fun delete(path: String)
    suspend fun readUploadJobs(): String?
    suspend fun writeUploadJobs(json: String)
}
