import SwiftUI
import ComposeApp
import FirebaseCore
import GoogleSignIn

class AppDelegate: NSObject, UIApplicationDelegate {
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
        StitchPadAppKt.doInitKoin(platformConfig: { _ in })
        return true
    }

    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        return GIDSignIn.sharedInstance.handle(url)
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
