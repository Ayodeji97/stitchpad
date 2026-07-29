package com.danzucker.stitchpad.feature.main.presentation

import com.danzucker.stitchpad.core.domain.session.StaffRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomNavItemTest {

    @Test
    fun owner_sees_all_four_tabs_including_reports() {
        val tabs = BottomNavItem.forRole(StaffRole.OWNER)
        assertEquals(BottomNavItem.all, tabs)
        assertTrue(tabs.contains(BottomNavItem.Reports))
    }

    @Test
    fun staff_sees_dashboard_customers_orders_but_not_reports() {
        val tabs = BottomNavItem.forRole(StaffRole.STAFF)
        assertEquals(
            listOf(BottomNavItem.Dashboard, BottomNavItem.Customers, BottomNavItem.Orders),
            tabs,
        )
        assertFalse(tabs.contains(BottomNavItem.Reports))
    }
}
