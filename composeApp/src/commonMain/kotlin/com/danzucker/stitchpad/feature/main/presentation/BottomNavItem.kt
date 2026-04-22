package com.danzucker.stitchpad.feature.main.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.danzucker.stitchpad.navigation.CustomerListRoute
import com.danzucker.stitchpad.navigation.DashboardRoute
import com.danzucker.stitchpad.navigation.OrderListRoute
import com.danzucker.stitchpad.navigation.SettingsPlaceholderRoute
import org.jetbrains.compose.resources.StringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.nav_customers
import stitchpad.composeapp.generated.resources.nav_dashboard
import stitchpad.composeapp.generated.resources.nav_orders
import stitchpad.composeapp.generated.resources.nav_settings

sealed class BottomNavItem(
    val route: Any,
    val icon: ImageVector,
    val labelRes: StringResource
) {
    data object Customers : BottomNavItem(
        route = CustomerListRoute,
        icon = Icons.Default.Person,
        labelRes = Res.string.nav_customers
    )
    data object Orders : BottomNavItem(
        route = OrderListRoute,
        icon = Icons.Default.ShoppingCart,
        labelRes = Res.string.nav_orders
    )
    data object Dashboard : BottomNavItem(
        route = DashboardRoute,
        icon = Icons.Default.Home,
        labelRes = Res.string.nav_dashboard
    )
    data object Settings : BottomNavItem(
        route = SettingsPlaceholderRoute,
        icon = Icons.Default.Settings,
        labelRes = Res.string.nav_settings
    )

    companion object {
        val all = listOf(Dashboard, Customers, Orders, Settings)
    }
}
