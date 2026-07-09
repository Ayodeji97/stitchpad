package com.danzucker.stitchpad.core.presentation.celebration

import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-lifetime owner of milestone celebrations ("tell, don't ask"): ViewModels
 * report every milestone via [trigger]; the controller decides whether it shows.
 * The one-shot flag persists at trigger time — not dismissal — so a crash
 * mid-confetti can never re-show, and "first" never re-fires even if the user
 * later deletes everything. Back-to-back milestones queue FIFO. Any auth-user
 * change clears both the visible celebration and the queue so confetti never
 * plays over the login screen or leaks across accounts.
 */
class CelebrationController(
    private val preferences: OnboardingPreferencesStore,
    private val analytics: Analytics,
    authUserIds: Flow<String?>,
    private val scope: CoroutineScope,
) {
    private val _current = MutableStateFlow<Milestone?>(null)
    val current: StateFlow<Milestone?> = _current.asStateFlow()

    private val queue = ArrayDeque<Milestone>()
    private val mutex = Mutex()

    init {
        scope.launch {
            authUserIds.distinctUntilChanged().collect {
                mutex.withLock {
                    queue.clear()
                    _current.value = null
                }
            }
        }
    }

    suspend fun trigger(userId: String, milestone: Milestone) {
        mutex.withLock {
            if (preferences.hasCelebrated(userId, milestone.key)) return
            preferences.setCelebrated(userId, milestone.key)
            analytics.logEvent(AnalyticsEvent.CelebrationShown(milestone.key))
            if (_current.value == null) {
                _current.value = milestone
            } else {
                queue.addLast(milestone)
            }
        }
    }

    fun dismiss() {
        scope.launch {
            mutex.withLock { _current.value = queue.removeFirstOrNull() }
        }
    }
}
