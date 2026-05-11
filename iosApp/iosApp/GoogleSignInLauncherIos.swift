import Foundation
import UIKit
import GoogleSignIn
import ComposeApp

/// Implements the Kotlin-defined `NativeGoogleSignInLauncher` protocol so KMP
/// iOS code can trigger Google sign-in without depending on the Swift-only
/// `GoogleSignIn-iOS` 8.x module from Kotlin/Native.
///
/// Registered into Koin via `PlatformModule_iosKt.iosNativeGoogleSignInLauncher`
/// from AppDelegate BEFORE doInitKoin runs.
///
/// Swift sees the Kotlin `suspend fun` as:
///   func launchGoogleSignIn() async throws -> any ComposeApp.Result
/// where `ComposeApp.Result` is the Kotlin sealed interface bridged via Obj-C.
/// `ComposeApp.` prefix disambiguates from Swift stdlib's `Result` type.
@objc public class GoogleSignInLauncherIos: NSObject, NativeGoogleSignInLauncher {

    public func launchGoogleSignIn() async throws -> any ComposeApp.Result {
        guard let presentingVC = await MainActor.run(body: {
            SsoPresentingViewControllerBridge.presentingViewController
        }) else {
            return ResultError(error: SsoError.unknown)
        }

        return await withCheckedContinuation { continuation in
            DispatchQueue.main.async {
                GIDSignIn.sharedInstance.signIn(withPresenting: presentingVC) { signInResult, error in
                    if let error = error as NSError? {
                        // GIDSignInError.canceled raw value is -5
                        let mappedError: SsoError = (error.code == -5) ? .cancelled : .unknown
                        continuation.resume(returning: ResultError(error: mappedError))
                        return
                    }
                    guard let token = signInResult?.user.idToken?.tokenString else {
                        continuation.resume(returning: ResultError(error: SsoError.unknown))
                        return
                    }
                    continuation.resume(returning: ResultSuccess(data: token as NSString))
                }
            }
        }
    }
}
