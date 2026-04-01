package com.danzucker.stitchpad

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.navigation.StitchPadNavHost
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.koin.compose.koinInject

@Composable
fun App() {
    StitchPadTheme {
        val navController = rememberNavController()
        val authRepository: AuthRepository = koinInject()
        StitchPadNavHost(
            navController = navController,
            isLoggedIn = authRepository.isLoggedIn
        )
    }
}
