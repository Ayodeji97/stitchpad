import SwiftUI
import ComposeApp
import FirebaseCore
import FirebaseMessaging
import GoogleSignIn
import UserNotifications

class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        IosOfflineUploadBackgroundTasksKt.registerIosOfflineUploadTasks()
        // Must register both Swift launchers BEFORE doInitKoin so PlatformModule
        // can wire them into SsoCredentialProvider.
        PlatformModule_iosKt.iosNativeGoogleSignInLauncher = GoogleSignInLauncherIos()
        PlatformModule_iosKt.iosNativeAppleSignInLauncher = AppleSignInLauncherIos()

        // Push: set the messaging + notification delegates and the Swift→Kotlin
        // bridge BEFORE doInitKoin, mirroring the SSO launchers, so the shared
        // push layer can read the token / permission state through Koin.
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        PlatformModule_iosKt.iosNativePushService = PushServiceIos.shared
        PushServiceIos.shared.refreshAuthorizationStatus()

        StitchPadAppKt.doInitKoin(platformConfig: { _ in })
        return true
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        // Re-sync the cached authorization status in case the user changed it
        // in iOS Settings while the app was backgrounded.
        PushServiceIos.shared.refreshAuthorizationStatus()
    }

    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        return GIDSignIn.sharedInstance.handle(url)
    }

    // MARK: APNs registration

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        // Explicit APNs<->FCM handshake (robust whether or not FCM swizzling is on).
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: any Error
    ) {
        print("APNs registration failed: \(error)")
    }

    // MARK: MessagingDelegate

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        PushServiceIos.shared.updateToken(token)
        IosPushBridgeKt.iosOnFcmTokenReceived(token: token)
    }

    // MARK: UNUserNotificationCenterDelegate

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        // Show the push while the app is foreground (iOS analog of Android's manual post).
        completionHandler([.banner, .sound, .list])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        if let target = userInfo["target"] as? String, target == "inbox" {
            IosPushBridgeKt.iosOnPushInboxTap()
        }
        completionHandler()
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .onChange(of: scenePhase) { newPhase in
            if newPhase == .active {
                IosOfflineUploadBackgroundTasksKt.drainIosOfflineUploadsInForeground()
            }
        }
    }
}
