import Foundation
import UIKit
import FirebaseMessaging
import UserNotifications
import ComposeApp

/// Swift implementation of the Kotlin `NativePushService` bridge interface.
/// Assigned to `PlatformModule_iosKt.iosNativePushService` in the AppDelegate
/// before `doInitKoin`, so the shared Kotlin layer can pull the FCM token,
/// the permission state, and trigger the system permission prompt.
final class PushServiceIos: NativePushService {
    static let shared = PushServiceIos()
    private init() {}

    /// Latest FCM registration token (set from `messaging(_:didReceiveRegistrationToken:)`).
    private var latestToken: String?

    func updateToken(_ token: String?) {
        latestToken = token
    }

    /// (Re)register for remote notifications when already authorized. Called on launch
    /// (didFinishLaunching, before any UI) and every `applicationDidBecomeActive`, because
    /// iOS does NOT persist the APNs token across launches and FCM needs a fresh one to
    /// mint/deliver the FCM registration token.
    func refreshAuthorizationStatus() {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            guard settings.authorizationStatus == .authorized else { return }
            DispatchQueue.main.async {
                UIApplication.shared.registerForRemoteNotifications()
            }
        }
    }

    // MARK: NativePushService (Kotlin pull-direction)

    func currentFcmToken() -> String? {
        latestToken
    }

    /// Read the OS authorization status fresh (no cache) and report whether it is
    /// `.notDetermined`. The Kotlin side awaits this, so the pre-prompt decision never
    /// races a stale value.
    func authorizationUndetermined(callback: BooleanCallback) {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            callback.onResult(value: settings.authorizationStatus == .notDetermined)
        }
    }

    func requestAuthorization() {
        UNUserNotificationCenter.current()
            .requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
                guard granted else { return }
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
    }

    func deleteToken() {
        Messaging.messaging().deleteToken { _ in }
        latestToken = nil
    }
}
