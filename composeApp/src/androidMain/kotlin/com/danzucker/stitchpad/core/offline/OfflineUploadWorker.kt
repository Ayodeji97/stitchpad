package com.danzucker.stitchpad.core.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.context.GlobalContext

class OfflineUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val outbox = GlobalContext.get().get<OfflineUploadOutbox>()
        outbox.drain()
        return Result.success()
    }
}
