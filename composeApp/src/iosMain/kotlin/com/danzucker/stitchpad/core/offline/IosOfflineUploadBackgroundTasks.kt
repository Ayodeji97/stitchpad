package com.danzucker.stitchpad.core.offline

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import platform.BackgroundTasks.BGProcessingTask
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate

private const val OFFLINE_UPLOAD_TASK_ID = "com.danzucker.stitchpad.offlineUploads"

@OptIn(ExperimentalForeignApi::class)
object IosOfflineUploadBackgroundTasks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = OFFLINE_UPLOAD_TASK_ID,
            usingQueue = null,
        ) { task ->
            val processingTask = task as? BGProcessingTask ?: return@registerForTaskWithIdentifier
            drain(task = processingTask)
        }
    }

    fun schedule(delayMs: Long = 0L) {
        val request = BGProcessingTaskRequest(identifier = OFFLINE_UPLOAD_TASK_ID).apply {
            requiresNetworkConnectivity = true
            requiresExternalPower = false
            if (delayMs > 0L) {
                val secondsFromNow = delayMs.toDouble() / MILLIS_PER_SECOND
                earliestBeginDate = NSDate(
                    timeIntervalSinceReferenceDate = NSDate().timeIntervalSinceReferenceDate + secondsFromNow,
                )
            }
        }
        runCatching {
            BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
        }.onFailure {
            // BGTaskScheduler may reject duplicate pending requests; foreground
            // drains and the next enqueue still cover the upload path.
        }
    }

    fun drainInForeground() {
        scope.launch {
            KoinPlatform.getKoin().get<OfflineUploadOutbox>().drain()
        }
    }

    private fun drain(task: BGProcessingTask) {
        val job = scope.launch {
            runCatching {
                KoinPlatform.getKoin().get<OfflineUploadOutbox>().drain()
            }.onSuccess {
                task.setTaskCompletedWithSuccess(true)
                schedule()
            }.onFailure {
                task.setTaskCompletedWithSuccess(false)
                schedule()
            }
        }
        task.expirationHandler = {
            job.cancel()
            task.setTaskCompletedWithSuccess(false)
        }
    }
}

private const val MILLIS_PER_SECOND = 1_000.0

fun registerIosOfflineUploadTasks() {
    IosOfflineUploadBackgroundTasks.register()
}

fun drainIosOfflineUploadsInForeground() {
    IosOfflineUploadBackgroundTasks.drainInForeground()
}
