package com.danzucker.stitchpad.feature.notification.push

import com.danzucker.stitchpad.core.data.appLifetimeScope
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.navigation.DeepLinkTarget
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

private val iosPushScope = appLifetimeScope(tag = "IosPushBridge")

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

/**
 * Called by Swift (UNUserNotificationCenter delegate) when the user taps a per-order push
 * notification (target: order, Task 2). Sets the pending ORDER deep link; MainRoot consumes it.
 */
fun iosOnPushOrderTap(orderId: String) {
    runCatching {
        KoinPlatform.getKoin().get<PendingDeepLinkHolder>().setOrder(orderId)
    }.onFailure { AppLogger.w { "iosOnPushOrderTap failed: ${it.message}" } }
}

/**
 * Called by Swift (UNUserNotificationCenter delegate) when the user taps the daily summary push
 * targeting the To-collect list (target: to_collect, Task 5). Sets the pending TO_COLLECT deep
 * link; MainRoot consumes it.
 */
fun iosOnPushToCollectTap() {
    runCatching {
        KoinPlatform.getKoin().get<PendingDeepLinkHolder>().set(DeepLinkTarget.TO_COLLECT)
    }.onFailure { AppLogger.w { "iosOnPushToCollectTap failed: ${it.message}" } }
}
