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
    /// Cached authorization status. Defaults to `true` (undetermined) for a fresh
    /// install so the pre-prompt fires; refreshed asynchronously on launch + foreground.
    private var notDetermined = true

    func updateToken(_ token: String?) {
        latestToken = token
    }

    /// Re-read the OS authorization status into the cache. Called on launch and
    /// every `applicationDidBecomeActive` (catches the user changing it in Settings).
    /// When already authorized, (re)register for remote notifications so a fresh
    /// APNs token is delivered every launch — iOS does NOT persist the APNs token
    /// across launches, and FCM needs it to mint/deliver the FCM registration token.
    func refreshAuthorizationStatus() {
        UNUserNotificationCenter.current().getNotificationSettings { [weak self] settings in
            self?.notDetermined = settings.authorizationStatus == .notDetermined
            if settings.authorizationStatus == .authorized {
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
        }
    }

    // MARK: NativePushService (Kotlin pull-direction)

    func currentFcmToken() -> String? {
        latestToken
    }

    func authorizationUndetermined() -> Bool {
        notDetermined
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
