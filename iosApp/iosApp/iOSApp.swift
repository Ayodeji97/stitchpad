import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        StitchPadAppKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
