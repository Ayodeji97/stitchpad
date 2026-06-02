package com.danzucker.stitchpad.core.offline

actual class OfflineUploadScheduler {
    actual fun schedule(delayMs: Long) {
        IosOfflineUploadBackgroundTasks.schedule(delayMs)
    }
}
