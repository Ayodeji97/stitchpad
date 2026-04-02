import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        StitchPadAppKt.doInitKoin(platformConfig: { _ in })
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
