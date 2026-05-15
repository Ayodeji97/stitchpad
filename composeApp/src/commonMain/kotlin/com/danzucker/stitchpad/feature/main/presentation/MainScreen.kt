package com.danzucker.stitchpad.feature.main.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
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
import com.danzucker.stitchpad.feature.dashboard.presentation.AddCustomerFirstScreen
import com.danzucker.stitchpad.feature.dashboard.presentation.DashboardRoot
import com.danzucker.stitchpad.feature.goals.presentation.setup.GoalSetupRoot
import com.danzucker.stitchpad.feature.measurement.presentation.form.MeasurementFormRoot
import com.danzucker.stitchpad.feature.order.presentation.detail.OrderDetailRoot
import com.danzucker.stitchpad.feature.order.presentation.form.OrderFormRoot
import com.danzucker.stitchpad.feature.order.presentation.list.OrderListRoot
import com.danzucker.stitchpad.feature.reports.presentation.ReportsRoot
import com.danzucker.stitchpad.feature.settings.presentation.changeemail.ChangeEmailRoot
import com.danzucker.stitchpad.feature.settings.presentation.changepassword.ChangePasswordRoot
import com.danzucker.stitchpad.feature.settings.presentation.deleteaccount.DeleteAccountRoot
import com.danzucker.stitchpad.feature.settings.presentation.editprofile.EditProfileRoot
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsRoot
import com.danzucker.stitchpad.feature.style.presentation.form.StyleFormRoot
import com.danzucker.stitchpad.feature.style.presentation.gallery.StyleGalleryRoot
import com.danzucker.stitchpad.navigation.AddCustomerFirstRoute
import com.danzucker.stitchpad.navigation.ChangeEmailRoute
import com.danzucker.stitchpad.navigation.ChangePasswordRoute
import com.danzucker.stitchpad.navigation.CustomerDetailRoute
import com.danzucker.stitchpad.navigation.CustomerFormRoute
import com.danzucker.stitchpad.navigation.CustomerListRoute
import com.danzucker.stitchpad.navigation.DashboardRoute
import com.danzucker.stitchpad.navigation.DeleteAccountRoute
import com.danzucker.stitchpad.navigation.EditProfileRoute
import com.danzucker.stitchpad.navigation.GoalSetupRoute
import com.danzucker.stitchpad.navigation.MeasurementFormRoute
import com.danzucker.stitchpad.navigation.OrderDetailRoute
import com.danzucker.stitchpad.navigation.OrderFormRoute
import com.danzucker.stitchpad.navigation.OrderListRoute
import com.danzucker.stitchpad.navigation.ReportsRoute
import com.danzucker.stitchpad.navigation.SettingsRoute
import com.danzucker.stitchpad.navigation.StyleFormRoute
import com.danzucker.stitchpad.navigation.StyleGalleryRoute
import org.jetbrains.compose.resources.stringResource

@Composable
fun MainRoot(
    onSignedOut: () -> Unit,
    onNavigateToDebugMenu: () -> Unit,
) {
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
                                            popUpTo(DashboardRoute) { saveState = true }
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
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
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
            onNavigateToDebugMenu = onNavigateToDebugMenu,
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
    onNavigateToDebugMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = DashboardRoute,
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
                onNavigateToCustomerForm = { customerId ->
                    navController.navigate(CustomerFormRoute(customerId = customerId))
                },
                onNavigateToMeasurementForm = { customerId, linkToOrderId ->
                    navController.navigate(
                        MeasurementFormRoute(customerId = customerId, linkToOrderId = linkToOrderId),
                    )
                },
                onNavigateToDuplicateOrder = { sourceOrderId ->
                    navController.navigate(OrderFormRoute(seedFromOrderId = sourceOrderId))
                },
                onNavigateToStyleForm = { customerId, linkToOrderId ->
                    navController.navigate(
                        StyleFormRoute(customerId = customerId, linkToOrderId = linkToOrderId),
                    )
                },
                onNavigateBack = { navController.navigateUp() },
            )
        }
        composable<DashboardRoute> {
            DashboardRoot(
                onNavigateToOrderDetail = { orderId ->
                    navController.navigate(OrderDetailRoute(orderId = orderId))
                },
                onNavigateToOrders = {
                    navController.navigate(OrderListRoute) {
                        launchSingleTop = true
                    }
                },
                onNavigateToOrderForm = {
                    navController.navigate(OrderFormRoute())
                },
                onNavigateToEditOrder = { orderId ->
                    navController.navigate(OrderFormRoute(orderId = orderId))
                },
                onNavigateToCustomerForm = {
                    navController.navigate(CustomerFormRoute())
                },
                onNavigateToCustomers = {
                    navController.navigate(CustomerListRoute) {
                        launchSingleTop = true
                    }
                },
                onNavigateToGoalSetup = {
                    navController.navigate(GoalSetupRoute)
                },
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute) {
                        launchSingleTop = true
                    }
                },
                onNavigateToAddCustomerFirst = {
                    navController.navigate(AddCustomerFirstRoute)
                },
                onNavigateToCustomerDetail = { customerId ->
                    navController.navigate(CustomerDetailRoute(customerId = customerId))
                }
            )
        }
        composable<AddCustomerFirstRoute> {
            AddCustomerFirstScreen(
                onAddCustomerClick = {
                    navController.navigate(CustomerFormRoute()) {
                        // Pop the gate so back-from-CustomerForm returns to dashboard
                        // rather than re-showing the gate.
                        popUpTo(AddCustomerFirstRoute) { inclusive = true }
                    }
                },
                onBack = { navController.navigateUp() },
            )
        }
        composable<ReportsRoute> {
            ReportsRoot(
                onNavigateToCustomerDetail = { customerId ->
                    navController.navigate(CustomerDetailRoute(customerId = customerId))
                }
            )
        }
        composable<GoalSetupRoute> {
            GoalSetupRoot(
                onNavigateBack = { navController.navigateUp() }
            )
        }
        composable<SettingsRoute> {
            SettingsRoot(
                onNavigateToEditProfile = { navController.navigate(EditProfileRoute) },
                onNavigateToChangeEmail = { navController.navigate(ChangeEmailRoute) },
                onNavigateToChangePassword = { navController.navigate(ChangePasswordRoute) },
                onNavigateToDeleteAccount = { navController.navigate(DeleteAccountRoute) },
                onSignedOut = onSignedOut,
                onNavigateToDebugMenu = onNavigateToDebugMenu,
            )
        }
        composable<EditProfileRoute> {
            EditProfileRoot(
                onNavigateBack = { navController.navigateUp() },
            )
        }
        composable<ChangeEmailRoute> {
            ChangeEmailRoot(onNavigateBack = { navController.navigateUp() })
        }
        composable<ChangePasswordRoute> {
            ChangePasswordRoot(onNavigateBack = { navController.navigateUp() })
        }
        composable<DeleteAccountRoute> {
            DeleteAccountRoot(
                onNavigateBack = { navController.navigateUp() },
                onSignedOut = onSignedOut,
            )
        }
    }
}
