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
        // Must register the Swift Google launcher BEFORE doInitKoin so PlatformModule
        // can wire it into SsoCredentialProvider.
        PlatformModule_iosKt.iosNativeGoogleSignInLauncher = GoogleSignInLauncherIos()
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

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
