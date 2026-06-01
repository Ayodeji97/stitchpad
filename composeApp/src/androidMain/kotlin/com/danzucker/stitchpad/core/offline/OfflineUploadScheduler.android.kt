package com.danzucker.stitchpad.core.offline

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val OFFLINE_UPLOAD_WORK_NAME = "offline-upload-outbox"

actual class OfflineUploadScheduler(
    private val context: Context,
) {
    actual fun schedule(delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<OfflineUploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            OFFLINE_UPLOAD_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
