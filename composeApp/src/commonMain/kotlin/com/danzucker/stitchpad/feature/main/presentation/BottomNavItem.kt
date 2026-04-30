package com.danzucker.stitchpad.feature.main.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector
import com.danzucker.stitchpad.navigation.CustomerListRoute
import com.danzucker.stitchpad.navigation.DashboardRoute
import com.danzucker.stitchpad.navigation.OrderListRoute
import com.danzucker.stitchpad.navigation.ReportsRoute
import org.jetbrains.compose.resources.StringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.nav_customers
import stitchpad.composeapp.generated.resources.nav_dashboard
import stitchpad.composeapp.generated.resources.nav_orders
import stitchpad.composeapp.generated.resources.nav_reports

sealed class BottomNavItem(
    val route: Any,
    val icon: ImageVector,
    val labelRes: StringResource
) {
    data object Customers : BottomNavItem(
        route = CustomerListRoute,
        icon = Icons.Default.People,
        labelRes = Res.string.nav_customers
    )
    data object Orders : BottomNavItem(
        route = OrderListRoute,
        icon = Icons.Default.ShoppingBag,
        labelRes = Res.string.nav_orders
    )
    data object Dashboard : BottomNavItem(
        route = DashboardRoute,
        icon = Icons.Default.Dashboard,
        labelRes = Res.string.nav_dashboard
    )
    data object Reports : BottomNavItem(
        route = ReportsRoute,
        icon = Icons.Default.BarChart,
        labelRes = Res.string.nav_reports
    )

    companion object {
        val all = listOf(Dashboard, Customers, Orders, Reports)
    }
}
