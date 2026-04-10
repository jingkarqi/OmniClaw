package com.sora.omniclaw.navigation

sealed class Routes(
    val route: String,
    val label: String,
) {
    data object Home : Routes(
        route = "home",
        label = "Home",
    )

    data object Provider : Routes(
        route = "provider",
        label = "Provider",
    )

    data object Runtime : Routes(
        route = "runtime",
        label = "Runtime",
    )

    data object Permissions : Routes(
        route = "permissions",
        label = "Permissions",
    )

    companion object {
        val topLevel: List<Routes>
            get() = listOf(
                Home,
                Provider,
                Runtime,
                Permissions,
            )

        const val startDestination: String = "home"
    }
}
