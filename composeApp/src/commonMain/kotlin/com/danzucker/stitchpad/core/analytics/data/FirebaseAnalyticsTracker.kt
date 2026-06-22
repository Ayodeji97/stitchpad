package com.danzucker.stitchpad.core.analytics.data

import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsUserProperty
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.analytics.FirebaseAnalytics

/** Minimal seam over the SDK so the tracker is unit-testable without a real Firebase object. */
interface AnalyticsSink {
    fun logEvent(name: String, parameters: Map<String, Any>?)
    fun setUserId(id: String?)
    fun setUserProperty(name: String, value: String)
}

/** [AnalyticsSink] backed by the GitLive Firebase Analytics SDK. */
class FirebaseAnalyticsSink(private val analytics: FirebaseAnalytics) : AnalyticsSink {
    override fun logEvent(name: String, parameters: Map<String, Any>?) =
        analytics.logEvent(name, parameters)
    override fun setUserId(id: String?) = analytics.setUserId(id)
    override fun setUserProperty(name: String, value: String) =
        analytics.setUserProperty(name, value)
}

/**
 * Fire-and-forget analytics over Firebase. Every call is wrapped so a failure logs a
 * warning and is swallowed — analytics must never affect a user flow.
 */
class FirebaseAnalyticsTracker(private val sink: AnalyticsSink) : Analytics {

    override fun logEvent(event: AnalyticsEvent) = safely("logEvent ${event.name}") {
        sink.logEvent(event.name, event.params.ifEmpty { null })
    }

    override fun logScreenView(screenName: String) = safely("screen_view") {
        sink.logEvent(SCREEN_VIEW_EVENT, mapOf(SCREEN_NAME_PARAM to screenName))
    }

    override fun setUserId(userId: String?) = safely("setUserId") {
        sink.setUserId(userId)
    }

    override fun setUserProperty(property: AnalyticsUserProperty, value: String) =
        safely("setUserProperty ${property.key}") {
            sink.setUserProperty(property.key, value)
        }

    private inline fun safely(label: String, block: () -> Unit) {
        runCatching { block() }.onFailure { t ->
            AppLogger.w(TAG, t) { "analytics $label failed: ${t::class.simpleName}" }
        }
    }

    private companion object {
        const val TAG = "Analytics"
        const val SCREEN_VIEW_EVENT = "screen_view"
        const val SCREEN_NAME_PARAM = "screen_name"
    }
}
