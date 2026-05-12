package com.danzucker.stitchpad

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.danzucker.stitchpad.core.domain.preferences.ThemePreference
import com.danzucker.stitchpad.core.domain.preferences.ThemePreferencesStore
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferences
import com.danzucker.stitchpad.navigation.StitchPadNavHost
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.koin.compose.koinInject

@Composable
fun App() {
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
        val navController = rememberNavController()
        val onboardingPreferences: OnboardingPreferences = koinInject()
        StitchPadNavHost(
            navController = navController,
            onboardingPreferences = onboardingPreferences
        )
    }
}
