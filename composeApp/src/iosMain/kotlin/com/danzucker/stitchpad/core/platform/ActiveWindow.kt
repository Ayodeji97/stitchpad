package com.danzucker.stitchpad.core.platform

import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * Resolves the foreground-active [UIWindow] via connected scenes (iOS 13+). The older
 * `UIApplication.keyWindow` API is deprecated and returns `null` in multi-scene apps, which
 * would silently break any code that tries to present a view controller from it.
 */
internal fun activeKeyWindow(): UIWindow? {
    val scenes = UIApplication.sharedApplication.connectedScenes
    val activeScene = scenes.firstOrNull {
        it is UIWindowScene && it.activationState == UISceneActivationStateForegroundActive
    } as? UIWindowScene ?: scenes.firstOrNull { it is UIWindowScene } as? UIWindowScene
    val windows = activeScene?.windows.orEmpty()
    return windows.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow
        ?: windows.firstOrNull() as? UIWindow
}
