import UIKit

/// Static bridge so KMP iOS-side actuals can reach the foreground UIViewController.
/// `ComposeView.makeUIViewController` sets this; the iOS Kotlin SsoCredentialProvider
/// reads it when launching the OS sign-in sheet.
///
/// Holds a weak reference so SwiftUI lifecycle doesn't leak the VC.
@objc public class SsoPresentingViewControllerBridge: NSObject {
    @objc public static weak var presentingViewController: UIViewController?
}
