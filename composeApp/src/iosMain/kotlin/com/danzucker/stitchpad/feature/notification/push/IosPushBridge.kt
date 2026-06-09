package com.danzucker.stitchpad.feature.notification.push

import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.navigation.DeepLinkTarget
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

private val iosPushScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Called by Swift (MessagingDelegate.didReceiveRegistrationToken) when an FCM token is
 * received or refreshed. Registers it for the signed-in user — the iOS analog of Android's
 * FirebaseMessagingService.onNewToken. No-ops when logged out.
 */
fun iosOnFcmTokenReceived(token: String) {
    iosPushScope.launch {
        runCatching {
            val koin = KoinPlatform.getKoin()
            val uid = koin.get<AuthRepository>().getCurrentUser()?.id ?: return@runCatching
            koin.get<PushTokenRegistrar>().register(uid, token)
        }.onFailure { AppLogger.w { "iosOnFcmTokenReceived failed: ${it.message}" } }
    }
}

/**
 * Called by Swift (UNUserNotificationCenter delegate) when the user taps a push targeting the
 * inbox. Sets the pending deep link; the existing PushDeepLinkRedirectEffect + MainRoot consume it.
 */
fun iosOnPushInboxTap() {
    runCatching {
        KoinPlatform.getKoin().get<PendingDeepLinkHolder>().set(DeepLinkTarget.INBOX)
    }.onFailure { AppLogger.w { "iosOnPushInboxTap failed: ${it.message}" } }
}
