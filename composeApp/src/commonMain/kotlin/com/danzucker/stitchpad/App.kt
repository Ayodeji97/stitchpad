package com.danzucker.stitchpad

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.danzucker.stitchpad.core.config.presentation.AppGateRoot
import com.danzucker.stitchpad.core.domain.preferences.ThemePreference
import com.danzucker.stitchpad.core.domain.preferences.ThemePreferencesStore
import com.danzucker.stitchpad.core.offline.OfflineUploadOutbox
import com.danzucker.stitchpad.feature.freemium.presentation.reconcile.ReconcileCoordinator
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferences
import com.danzucker.stitchpad.navigation.StitchPadNavHost
import com.danzucker.stitchpad.ui.components.celebration.CelebrationOverlayHost
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.koin.compose.koinInject

@Composable
fun App() {
    // Slot reconcile lifecycle. The coordinator's init block subscribes to auth +
    // entitlements changes on the app-lifetime CoroutineScope wired in FreemiumModule.
    // We just need to force the Koin singleton to materialize once per process —
    // ensureRunning() is a no-op call that guarantees the side effect of Koin
    // instantiating the object. See ReconcileCoordinator for the full rationale
    // (V1.0 design spec decision #4).
    koinInject<ReconcileCoordinator>().ensureRunning()
    koinInject<OfflineUploadOutbox>().ensureRunning()

    val themeStore: ThemePreferencesStore = koinInject()
    val themeFlow = remember(themeStore) { themeStore.observeTheme() }
    val themePreference by themeFlow.collectAsState(initial = ThemePreference.SYSTEM)
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> systemDark
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    StitchPadTheme(darkTheme = darkTheme) {
        // Break-glass gate: replaces the whole app with a blocking screen when the
        // remote config forces an update or flips on maintenance mode. Fail-open —
        // renders the nav host normally until the config actively says to block.
        AppGateRoot {
            val navController = rememberNavController()
            val onboardingPreferences: OnboardingPreferences = koinInject()
            CelebrationOverlayHost {
                StitchPadNavHost(
                    navController = navController,
                    onboardingPreferences = onboardingPreferences
                )
            }
        }
    }
}
