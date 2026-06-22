package com.danzucker.stitchpad.core.config.domain

import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for whether the community banner is dismissed.
 * Wraps the persistent device-wide pref with an observable StateFlow so a
 * dismiss/join from ANY surface (Settings, Dashboard banner, debug reset)
 * updates an already-alive DashboardViewModel live, not just on recreation.
 */
class CommunityBannerDismissal(
    private val prefs: OnboardingPreferencesStore,
) {
    private val _dismissed = MutableStateFlow(false)
    val dismissed: StateFlow<Boolean> = _dismissed

    /** Seed the flow from the persisted value. Call once on first observe. */
    suspend fun hydrate() {
        _dismissed.value = prefs.hasDismissedCommunityBanner()
    }

    /** Mark dismissed (Join or X from any surface): update flow + persist. */
    suspend fun markDismissed() {
        _dismissed.value = true
        prefs.setCommunityBannerDismissed()
    }

    /** Debug-only: clear so the banner can re-appear. */
    suspend fun reset() {
        _dismissed.value = false
        prefs.clearCommunityBannerDismissed()
    }
}
