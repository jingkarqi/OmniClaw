package com.sora.omniclaw.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test
    fun topLevelRoutes_exposeExpectedOrder() {
        assertEquals(
            listOf(
                Routes.Home.route,
                Routes.Provider.route,
                Routes.Runtime.route,
                Routes.Permissions.route,
            ),
            Routes.topLevel.map { it.route }
        )
    }

    @Test
    fun startDestination_isHome() {
        assertEquals(Routes.Home.route, Routes.startDestination)
    }
}
