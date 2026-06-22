package com.danzucker.stitchpad.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenNameTest {
    @Test fun stripsPackageAndRouteSuffix() =
        assertEquals("Home", screenNameFor("com.danzucker.stitchpad.navigation.HomeRoute"))

    @Test fun stripsArgsAndKeepsClassName() =
        assertEquals(
            "OrderDetail",
            screenNameFor("com.danzucker.stitchpad.navigation.OrderDetailRoute/123abc"),
        )

    @Test fun stripsQueryStyleArgs() =
        assertEquals(
            "CustomerForm",
            screenNameFor("com.danzucker.stitchpad.navigation.CustomerFormRoute?customerId=9"),
        )

    @Test fun nullRouteFallsBackToUnknown() =
        assertEquals("Unknown", screenNameFor(null))
}
