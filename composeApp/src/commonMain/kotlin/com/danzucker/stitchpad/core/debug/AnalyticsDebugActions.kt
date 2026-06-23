package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics

private const val TAG = "AnalyticsDebugActions"

/**
 * Debug-only control over analytics collection so manual smoke tests don't pollute
 * production funnels. Toggle OFF during testing; toggle ON (or relaunch) to verify
 * events in Firebase DebugView.
 *
 * DebugView itself is enabled per-run via the platform launch flag — this toggle only
 * controls whether events are sent at all:
 *  - Android: `adb shell setprop debug.firebase.analytics.app com.danzucker.stitchpad`
 *  - iOS: add `-FIRAnalyticsDebugEnabled` launch argument in the Xcode scheme.
 *
 * Release builds never expose this and always collect (Firebase default).
 */
interface AnalyticsDebugActions {
    fun setAnalyticsEnabled(enabled: Boolean)
}

/**
 * Calls [Firebase.analytics.setAnalyticsCollectionEnabled] which the GitLive SDK
 * persists internally across launches — no separate preferences store needed.
 *
 * NOTE: The GitLive SDK exposes no getter for the collection-enabled flag, so the
 * toggle's displayed state in the debug menu is optimistic and session-scoped
 * (defaults to `true` on every cold launch). The SDK's own persistence governs
 * actual collection behavior; the UI reflects what the user toggled this session.
 */
class DefaultAnalyticsDebugActions : AnalyticsDebugActions {
    override fun setAnalyticsEnabled(enabled: Boolean) {
        runCatching {
            Firebase.analytics.setAnalyticsCollectionEnabled(enabled)
        }.onFailure { t ->
            AppLogger.w(TAG, t) { "setAnalyticsCollectionEnabled($enabled) failed: ${t::class.simpleName}" }
        }
    }
}
