package com.danzucker.stitchpad.core.offline

expect class OfflineUploadScheduler {
    fun schedule(delayMs: Long = 0L)
}
