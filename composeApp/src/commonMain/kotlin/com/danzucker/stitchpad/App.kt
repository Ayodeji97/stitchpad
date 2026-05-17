package com.danzucker.stitchpad

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.danzucker.stitchpad.core.domain.preferences.ThemePreference
import com.danzucker.stitchpad.core.domain.preferences.ThemePreferencesStore
import com.danzucker.stitchpad.feature.freemium.domain.FreemiumRepository
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferences
import com.danzucker.stitchpad.navigation.StitchPadNavHost
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

@Composable
fun App() {
    val auth: FirebaseAuth = koinInject()
    val freemium: FreemiumRepository = koinInject()

    // Reuse the auth-state flow already present in the Koin graph to trigger
    // a best-effort, idempotent slot reconciliation whenever a user signs in
    // or the app comes to the foreground under a signed-in session.
    val uidFlow = remember(auth) { auth.authStateChanged.map { it?.uid } }
    val uid by uidFlow.collectAsState(initial = auth.currentUser?.uid)
    LaunchedEffect(uid) {
        if (uid != null) {
            freemium.reconcileSlots() // best-effort, idempotent; failure is swallowed
        }
    }

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
