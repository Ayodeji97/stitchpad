import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let vc = MainViewControllerKt.MainViewController()
        // Expose the host VC so KMP iOS-side SsoCredentialProvider can present
        // Google / Apple sign-in sheets from a valid presenting context.
        SsoPresentingViewControllerBridge.presentingViewController = vc
        return vc
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}



