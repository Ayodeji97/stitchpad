package com.danzucker.stitchpad.feature.referral.data

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.referral.domain.InstallReferrerReader
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "InstallReferrer"

/**
 * Bridges Play's callback-based InstallReferrerClient into a one-shot suspend read.
 * Resumes exactly once (OK → referrer string, anything else → null) and always tears
 * down the service connection.
 */
class AndroidInstallReferrerReader(
    private val context: Context,
) : InstallReferrerReader {

    override suspend fun readReferrer(): String? = suspendCancellableCoroutine { cont ->
        val client = InstallReferrerClient.newBuilder(context).build()
        var resumed = false

        fun finish(value: String?) {
            endQuietly(client)
            if (!resumed) {
                resumed = true
                cont.resume(value)
            }
        }

        cont.invokeOnCancellation { endQuietly(client) }

        try {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    val referrer = if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                        readReferrerString(client)
                    } else {
                        null
                    }
                    finish(referrer)
                }

                override fun onInstallReferrerServiceDisconnected() {
                    finish(null)
                }
            })
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            AppLogger.e(tag = TAG, throwable = e) { "startConnection failed: ${e.message}" }
            finish(null)
        }
    }

    private fun readReferrerString(client: InstallReferrerClient): String? =
        try {
            client.installReferrer.installReferrer
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            AppLogger.e(tag = TAG, throwable = e) { "reading installReferrer failed: ${e.message}" }
            null
        }

    private fun endQuietly(client: InstallReferrerClient) {
        try {
            client.endConnection()
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Throwable) {
            // Already disconnected / never connected — nothing to recover.
        }
    }
}
