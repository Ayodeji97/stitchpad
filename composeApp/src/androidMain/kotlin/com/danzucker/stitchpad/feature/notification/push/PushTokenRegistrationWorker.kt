package com.danzucker.stitchpad.feature.notification.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import kotlinx.coroutines.CancellationException
import org.koin.core.context.GlobalContext

private const val PUSH_TOKEN_WORK_NAME = "push-token-registration"
private const val KEY_TOKEN = "token"
private const val MAX_ATTEMPTS = 5
private const val TAG = "PushTokenWorker"

/**
 * Persists a refreshed FCM token off the messaging service's wakelock budget.
 *
 * `onNewToken` used to block (bounded runBlocking) until the Firestore write
 * landed; WorkManager lets the callback return immediately while unique work
 * waits for network + auth. A newer token REPLACEs any still-queued
 * registration, so out-of-order tokens can't win.
 */
class PushTokenRegistrationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        registerToken(inputData.getString(KEY_TOKEN))
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        AppLogger.w(tag = TAG, throwable = e) { "token registration failed attempt=$runAttemptCount" }
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    private suspend fun registerToken(token: String?): Result {
        // Missing payload or signed out: drop quietly — the registrar
        // re-registers on the next authenticated app open (pre-existing path).
        if (token != null) {
            val koin = GlobalContext.get()
            val userId = koin.get<AuthRepository>().getCurrentUser()?.id
            if (userId != null) {
                // The repository directly, NOT PushTokenRegistrar.register: the
                // registrar swallows write failures by design (its call sites are
                // best-effort), which would turn every failed Firestore write into
                // Result.success() and dead-code this worker's retry path
                // (cursor, PR #360). Here the throw IS the retry signal.
                koin.get<PushTokenRepository>().registerToken(
                    userId = userId,
                    token = token,
                    platform = "android",
                )
            }
        }
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context, token: String) {
            val request = OneTimeWorkRequestBuilder<PushTokenRegistrationWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setInputData(workDataOf(KEY_TOKEN to token))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                PUSH_TOKEN_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
