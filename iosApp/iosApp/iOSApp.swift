import SwiftUI
import ComposeApp
import FirebaseCore
import FirebaseCrashlytics
import FirebaseMessaging
import GoogleSignIn
import UserNotifications

@objc public class IosCrashReporterIos: NSObject, NativeCrashReporter {
    private let collectionEnabled: Bool

    init(collectionEnabled: Bool) {
        self.collectionEnabled = collectionEnabled
    }

    public func log(message: String) {
        guard collectionEnabled else { return }
        Crashlytics.crashlytics().log(message)
    }

    public func recordNonFatal(name: String, message: String?, stackTrace: String?) {
        guard collectionEnabled else { return }
        var userInfo: [String: Any] = [
            NSLocalizedDescriptionKey: message ?? name,
            "kmp_error_name": name
        ]
        if let stackTrace {
            userInfo["kmp_stack_trace"] = stackTrace
        }
        let error = NSError(
            domain: "com.danzucker.stitchpad.kmp",
            code: stableCode(for: name),
            userInfo: userInfo
        )
        Crashlytics.crashlytics().record(error: error)
    }

    public func setUserId(userId_ userId: String) {
        guard collectionEnabled else { return }
        Crashlytics.crashlytics().setUserID(userId)
    }

    public func setCustomKey(key: String, value: String) {
        guard collectionEnabled else { return }
        Crashlytics.crashlytics().setCustomValue(value, forKey: key)
    }

    private func stableCode(for name: String) -> Int {
        var hash = 5381
        for scalar in name.unicodeScalars {
            hash = ((hash << 5) &+ hash) &+ Int(scalar.value)
        }
        return abs(hash % 10_000)
    }
}

class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        #if DEBUG
        let crashlyticsEnabled = false
        #else
        let crashlyticsEnabled = true
        #endif
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(crashlyticsEnabled)
        IosCrashReporterKt.iosNativeCrashReporter = IosCrashReporterIos(
            collectionEnabled: crashlyticsEnabled
        )
        IosOfflineUploadBackgroundTasksKt.registerIosOfflineUploadTasks()
        // Must register both Swift launchers BEFORE doInitKoin so PlatformModule
        // can wire them into SsoCredentialProvider.
        PlatformModule_iosKt.iosNativeGoogleSignInLauncher = GoogleSignInLauncherIos()
        PlatformModule_iosKt.iosNativeAppleSignInLauncher = AppleSignInLauncherIos()

        // Apple In-App Purchase (StoreKit 2). Register before doInitKoin so the
        // iOS PaymentRepository (StoreKitPaymentRepository) can resolve it. The
        // Transaction.updates listener is started AFTER doInitKoin (below) because
        // it forwards into Koin and can fire immediately at launch (Ask-to-Buy /
        // recovery), which would crash/drop if Koin weren't initialized yet.
        let storeKitPurchaser = StoreKitPurchaserIos()
        PlatformModule_iosKt.iosNativeStoreKitPurchaser = storeKitPurchaser

        // Push: set the messaging + notification delegates and the Swift→Kotlin
        // bridge BEFORE doInitKoin, mirroring the SSO launchers, so the shared
        // push layer can read the token / permission state through Koin.
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        PlatformModule_iosKt.iosNativePushService = PushServiceIos.shared
        PushServiceIos.shared.refreshAuthorizationStatus()

        StitchPadAppKt.doInitKoin(platformConfig: { _ in })

        // Now that Koin is initialized, start observing StoreKit transactions —
        // the listener's iosOnStoreKitTransaction bridge resolves PaymentRepository
        // from Koin, so it must not run before doInitKoin.
        storeKitPurchaser.startObservingTransactions()
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
        // Renewal-reminder email "Renew" button: stitchpad://upgrade
        if IosDeepLinkKt.handleIosDeepLink(url: url.absoluteString) {
            return true
        }
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
                .onOpenURL { url in
                    // SwiftUI App-lifecycle apps deliver custom-scheme opens HERE (a View
                    // modifier on the root view), not via the AppDelegate's
                    // application(_:open:) — so the renewal email's stitchpad://upgrade
                    // link must be handled on this path. Falls through to Google Sign-In.
                    if !IosDeepLinkKt.handleIosDeepLink(url: url.absoluteString) {
                        _ = GIDSignIn.sharedInstance.handle(url)
                    }
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    // https Universal Links (the renewal email's link.getstitchpad.com URL)
                    // arrive as a browsing-web user activity, NOT via .onOpenURL. Gmail's iOS
                    // app refuses custom schemes, so the email uses the https form.
                    if let url = activity.webpageURL {
                        _ = IosDeepLinkKt.handleIosDeepLink(url: url.absoluteString)
                    }
                }
        }
        .onChange(of: scenePhase) { newPhase in
            if newPhase == .active {
                IosOfflineUploadBackgroundTasksKt.drainIosOfflineUploadsInForeground()
            }
        }
    }
}
