package com.danzucker.stitchpad.feature.review.presentation

import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.feature.review.data.ReviewPreferencesStore
import com.danzucker.stitchpad.feature.review.domain.ReviewGate
import com.danzucker.stitchpad.feature.review.domain.ReviewOutcome
import com.danzucker.stitchpad.feature.review.domain.StoreReviewLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * App-lifetime owner of the review prompt ("tell, don't ask"): ViewModels report delight
 * moments; the controller decides whether the sentiment sheet shows. Install time and the
 * distinct-open-day count are stamped on [ensureRunning]. Any auth-user change clears the
 * visible prompt so it never leaks across accounts. The gate's ordersCreated>=3 threshold
 * structurally prevents overlap with the only three (first-time) milestone celebrations.
 */
class ReviewController(
    private val preferences: ReviewPreferencesStore,
    private val analytics: Analytics,
    private val launcher: StoreReviewLauncher,
    authUserIds: Flow<String?>,
    private val scope: CoroutineScope,
    private val now: () -> Long,
) : ReviewArmer {

    private val _current = MutableStateFlow(false)
    val current: StateFlow<Boolean> = _current.asStateFlow()

    private val _effects = Channel<ReviewEffect>(Channel.BUFFERED)
    val effects: Flow<ReviewEffect> = _effects.receiveAsFlow()

    private var currentUserId: String? = null
    private val mutex = Mutex()

    init {
        scope.launch {
            preferences.stampInstallIfAbsent(now())
            preferences.recordOpenDay(todayEpochDay())
        }
        scope.launch {
            authUserIds.distinctUntilChanged().collect { uid ->
                mutex.withLock {
                    currentUserId = uid
                    _current.value = false
                }
            }
        }
    }

    /** No-op; forces Koin to materialize the singleton at app start (see App.kt). */
    fun ensureRunning() = Unit

    override fun armFromDelight() {
        scope.launch {
            mutex.withLock {
                val uid = currentUserId ?: return@withLock
                if (_current.value) return@withLock
                val signals = preferences.loadSignals(uid)
                if (!ReviewGate.isEligible(signals, now())) return@withLock
                analytics.logEvent(AnalyticsEvent.ReviewPromptShown)
                _current.value = true
            }
        }
    }

    override fun recordOrderCreated() {
        scope.launch {
            val uid = mutex.withLock { currentUserId } ?: return@launch
            preferences.incrementOrdersCreated(uid)
        }
    }

    fun onLoveIt() {
        scope.launch {
            val uid = mutex.withLock {
                _current.value = false
                currentUserId
            }
            uid?.let { preferences.recordPrompt(it, ReviewOutcome.RATED, now()) }
            analytics.logEvent(AnalyticsEvent.ReviewSentiment("positive"))
            analytics.logEvent(AnalyticsEvent.ReviewInAppRequested)
            launcher.requestInAppReview()
        }
    }

    fun onNotReally() {
        scope.launch {
            val uid = mutex.withLock {
                _current.value = false
                currentUserId
            }
            uid?.let { preferences.recordPrompt(it, ReviewOutcome.GAVE_FEEDBACK, now()) }
            analytics.logEvent(AnalyticsEvent.ReviewSentiment("negative"))
            analytics.logEvent(AnalyticsEvent.ReviewFeedbackOpened)
            _effects.send(ReviewEffect.OpenFeedback(ReviewConfig.FEEDBACK_URL))
        }
    }

    fun onDismiss() {
        scope.launch {
            val uid = mutex.withLock {
                _current.value = false
                currentUserId
            }
            uid?.let { preferences.recordPrompt(it, ReviewOutcome.DISMISSED, now()) }
            analytics.logEvent(AnalyticsEvent.ReviewSentiment("dismissed"))
        }
    }

    /** Debug-menu only: bypass the gate and show the sheet immediately. */
    fun forceArmForDebug() {
        scope.launch { _current.value = true }
    }

    private fun todayEpochDay(): Long =
        Instant.fromEpochMilliseconds(now())
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toEpochDays()
            .toLong()
}
