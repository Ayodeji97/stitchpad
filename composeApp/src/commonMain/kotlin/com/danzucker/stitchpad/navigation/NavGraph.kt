package com.danzucker.stitchpad.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.auth.presentation.login.LoginRoot
import com.danzucker.stitchpad.feature.auth.presentation.signup.SignUpRoot
import com.danzucker.stitchpad.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun StitchPadNavHost(
    navController: NavHostController,
    isLoggedIn: Boolean
) {
    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) HomeRoute else LoginRoute
    ) {
        composable<LoginRoute> {
            LoginRoot(
                onNavigateToSignUp = { navController.navigate(SignUpRoute) },
                onNavigateToHome = {
                    navController.navigate(HomeRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
                    }
                }
            )
        }
        composable<SignUpRoute> {
            SignUpRoot(
                onNavigateToLogin = { navController.navigateUp() },
                onNavigateToHome = {
                    navController.navigate(HomeRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
                    }
                }
            )
        }
        composable<HomeRoute> {
            HomePlaceholder()
        }
    }
}

@Composable
private fun HomePlaceholder() {
    val authRepository: AuthRepository = koinInject()
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Welcome to StitchPad!",
                style = MaterialTheme.typography.headlineLarge
            )
            Button(
                onClick = {
                    scope.launch { authRepository.signOut() }
                },
                modifier = Modifier.padding(top = DesignTokens.space4)
            ) {
                Text("Sign Out")
            }
        }
    }
}
