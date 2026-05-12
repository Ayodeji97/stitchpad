import Foundation
import UIKit
import AuthenticationServices
import CryptoKit
import ComposeApp

/// Implements the Kotlin-defined `NativeAppleSignInLauncher` protocol so KMP
/// iOS code can trigger Apple sign-in. Generates a SHA-256-hashed nonce for
/// the ASAuthorization request and returns the raw nonce alongside the token
/// so Firebase's OAuthProvider.credential can re-validate.
///
/// Registered into Koin via PlatformModule_iosKt.iosNativeAppleSignInLauncher
/// from AppDelegate BEFORE doInitKoin runs.
@objc public class AppleSignInLauncherIos: NSObject, NativeAppleSignInLauncher {

    public func launchAppleSignIn() async throws -> any ComposeApp.Result {
        let rawNonce = randomNonceString()
        let hashedNonce = sha256(rawNonce)

        return await withCheckedContinuation { continuation in
            let delegate = AppleAuthDelegate(rawNonce: rawNonce) { result in
                continuation.resume(returning: result)
            }
            DispatchQueue.main.async {
                let provider = ASAuthorizationAppleIDProvider()
                let request = provider.createRequest()
                request.requestedScopes = [.fullName, .email]
                request.nonce = hashedNonce

                let controller = ASAuthorizationController(authorizationRequests: [request])
                controller.delegate = delegate
                controller.presentationContextProvider = delegate
                // Retain delegate for the duration of the request — the
                // controller only holds it weakly.
                objc_setAssociatedObject(
                    controller, "appleAuthDelegate", delegate,
                    .OBJC_ASSOCIATION_RETAIN_NONATOMIC
                )
                controller.performRequests()
            }
        }
    }

    private func randomNonceString(length: Int = 32) -> String {
        let charset: [Character] =
            Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remaining = length
        while remaining > 0 {
            var random: UInt8 = 0
            let status = SecRandomCopyBytes(kSecRandomDefault, 1, &random)
            if status == errSecSuccess && random < charset.count {
                result.append(charset[Int(random)])
                remaining -= 1
            }
        }
        return result
    }

    private func sha256(_ input: String) -> String {
        let data = Data(input.utf8)
        let hash = SHA256.hash(data: data)
        return hash.map { String(format: "%02x", $0) }.joined()
    }
}

private class AppleAuthDelegate: NSObject,
    ASAuthorizationControllerDelegate,
    ASAuthorizationControllerPresentationContextProviding {

    let rawNonce: String
    let completion: (any ComposeApp.Result) -> Void
    // withCheckedContinuation traps on double-resume in debug and is UB in
    // release. ASAuthorizationController shouldn't call both callbacks, but if a
    // framework edge case ever does we'd crash the app — guard explicitly.
    private var resumed = false

    init(rawNonce: String, completion: @escaping (any ComposeApp.Result) -> Void) {
        self.rawNonce = rawNonce
        self.completion = completion
    }

    private func resumeOnce(_ result: any ComposeApp.Result) {
        guard !resumed else { return }
        resumed = true
        completion(result)
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        guard let cred = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = cred.identityToken,
              let token = String(data: tokenData, encoding: .utf8) else {
            resumeOnce(ResultError(error: SsoError.unknown))
            return
        }
        let fullName: String? = {
            let given = cred.fullName?.givenName ?? ""
            let family = cred.fullName?.familyName ?? ""
            let joined = [given, family].filter { !$0.isEmpty }.joined(separator: " ")
            return joined.isEmpty ? nil : joined
        }()
        let appleCred = AppleCredential(
            idToken: token,
            rawNonce: rawNonce,
            fullName: fullName
        )
        resumeOnce(ResultSuccess(data: appleCred))
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: any Error
    ) {
        let nsError = error as! NSError
        let mapped: SsoError = (nsError.code == ASAuthorizationError.canceled.rawValue)
            ? .cancelled : .unknown
        resumeOnce(ResultError(error: mapped))
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        SsoPresentingViewControllerBridge.presentingViewController?.view.window
            ?? UIApplication.shared.connectedScenes
                .compactMap { ($0 as? UIWindowScene)?.keyWindow }
                .first
            ?? ASPresentationAnchor()
    }
}
