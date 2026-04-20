package com.danzucker.stitchpad.feature.main.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.danzucker.stitchpad.feature.customer.presentation.detail.CustomerDetailRoot
import com.danzucker.stitchpad.feature.customer.presentation.form.CustomerFormRoot
import com.danzucker.stitchpad.feature.customer.presentation.list.CustomerListRoot
import com.danzucker.stitchpad.feature.measurement.presentation.form.MeasurementFormRoot
import com.danzucker.stitchpad.feature.order.presentation.detail.OrderDetailRoot
import com.danzucker.stitchpad.feature.order.presentation.form.OrderFormRoot
import com.danzucker.stitchpad.feature.order.presentation.list.OrderListRoot
import com.danzucker.stitchpad.feature.style.presentation.form.StyleFormRoot
import com.danzucker.stitchpad.feature.style.presentation.gallery.StyleGalleryRoot
import com.danzucker.stitchpad.navigation.CustomerDetailRoute
import com.danzucker.stitchpad.navigation.CustomerFormRoute
import com.danzucker.stitchpad.navigation.CustomerListRoute
import com.danzucker.stitchpad.navigation.DashboardPlaceholderRoute
import com.danzucker.stitchpad.navigation.MeasurementFormRoute
import com.danzucker.stitchpad.navigation.OrderDetailRoute
import com.danzucker.stitchpad.navigation.OrderFormRoute
import com.danzucker.stitchpad.navigation.OrderListRoute
import com.danzucker.stitchpad.navigation.SettingsPlaceholderRoute
import com.danzucker.stitchpad.navigation.StyleFormRoute
import com.danzucker.stitchpad.navigation.StyleGalleryRoute
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.home_sign_out
import stitchpad.composeapp.generated.resources.nav_dashboard
import stitchpad.composeapp.generated.resources.nav_settings

@Composable
fun MainRoot(onSignedOut: () -> Unit) {
    val innerNavController = rememberNavController()
    val navBackStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = BottomNavItem.all.any { item ->
        currentDestination?.hasRoute(item.route::class) == true
    } || currentDestination?.hasRoute<CustomerDetailRoute>() == true

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180))
            ) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        BottomNavItem.all.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (selected) return@NavigationBarItem
                                    val popped = innerNavController.popBackStack(
                                        route = item.route,
                                        inclusive = false
                                    )
                                    if (!popped) {
                                        innerNavController.navigate(item.route) {
                                            popUpTo(CustomerListRoute) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DesignTokens.primary700,
                                    selectedTextColor = DesignTokens.primary600,
                                    indicatorColor = DesignTokens.primary50,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        MainNavGraph(
            navController = innerNavController,
            onSignedOut = onSignedOut,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        )
    }
}

@Composable
private fun MainNavGraph(
    navController: NavHostController,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = CustomerListRoute,
        enterTransition = { slideInHorizontally(tween(280)) { it } + fadeIn(tween(280)) },
        exitTransition = { slideOutHorizontally(tween(220)) { -it / 3 } + fadeOut(tween(220)) },
        popEnterTransition = { slideInHorizontally(tween(280)) { -it / 3 } + fadeIn(tween(280)) },
        popExitTransition = { slideOutHorizontally(tween(220)) { it } + fadeOut(tween(220)) },
        modifier = modifier
    ) {
        composable<CustomerListRoute> {
            CustomerListRoot(
                onNavigateToAddCustomer = {
                    navController.navigate(CustomerFormRoute())
                },
                onNavigateToCustomerDetail = { customerId ->
                    navController.navigate(CustomerDetailRoute(customerId = customerId))
                }
            )
        }
        composable<CustomerDetailRoute> {
            CustomerDetailRoot(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToEditCustomer = { customerId ->
                    navController.navigate(CustomerFormRoute(customerId = customerId))
                },
                onNavigateToAddMeasurement = { customerId ->
                    navController.navigate(MeasurementFormRoute(customerId = customerId))
                },
                onNavigateToEditMeasurement = { customerId, measurementId ->
                    navController.navigate(MeasurementFormRoute(customerId = customerId, measurementId = measurementId))
                },
                onNavigateToStyleGallery = { customerId ->
                    navController.navigate(StyleGalleryRoute(customerId = customerId))
                }
            )
        }
        composable<CustomerFormRoute> {
            CustomerFormRoot(
                onNavigateBack = { navController.navigateUp() }
            )
        }
        composable<MeasurementFormRoute> {
            MeasurementFormRoot(
                onNavigateBack = { navController.navigateUp() }
            )
        }
        composable<StyleGalleryRoute> {
            StyleGalleryRoot(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToAddStyle = { customerId ->
                    navController.navigate(StyleFormRoute(customerId = customerId))
                },
                onNavigateToEditStyle = { customerId, styleId ->
                    navController.navigate(StyleFormRoute(customerId = customerId, styleId = styleId))
                }
            )
        }
        composable<StyleFormRoute> {
            StyleFormRoot(
                onNavigateBack = { navController.navigateUp() }
            )
        }
        composable<OrderListRoute> {
            OrderListRoot(
                onNavigateToOrderForm = {
                    navController.navigate(OrderFormRoute())
                },
                onNavigateToOrderDetail = { orderId ->
                    navController.navigate(OrderDetailRoute(orderId = orderId))
                }
            )
        }
        composable<OrderFormRoute> {
            OrderFormRoot(
                onNavigateBack = { navController.navigateUp() }
            )
        }
        composable<OrderDetailRoute> {
            OrderDetailRoot(
                onNavigateToOrderForm = { orderId ->
                    navController.navigate(OrderFormRoute(orderId = orderId))
                },
                onNavigateToCustomerDetail = { customerId ->
                    navController.navigate(CustomerDetailRoute(customerId = customerId))
                },
                onNavigateBack = { navController.navigateUp() }
            )
        }
        composable<DashboardPlaceholderRoute> {
            TabPlaceholder(title = Res.string.nav_dashboard)
        }
        composable<SettingsPlaceholderRoute> {
            TabPlaceholder(title = Res.string.nav_settings, onSignedOut = onSignedOut)
        }
    }
}

@Composable
private fun TabPlaceholder(title: StringResource, onSignedOut: (() -> Unit)? = null) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(title), style = MaterialTheme.typography.headlineMedium)
            if (onSignedOut != null) {
                Button(
                    onClick = onSignedOut,
                    modifier = Modifier.padding(top = DesignTokens.space4)
                ) {
                    Text(stringResource(Res.string.home_sign_out))
                }
            }
        }
    }
}
