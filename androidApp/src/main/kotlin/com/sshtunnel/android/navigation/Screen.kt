package com.sshtunnel.android.navigation

/**
 * Sealed class representing all navigation destinations in the app.
 */
sealed class Screen(val route: String) {
    object Profiles : Screen("profiles")
    object Connection : Screen("connection/{profileId}") {
        fun createRoute(profileId: Long) = "connection/$profileId"
    }
    object Settings : Screen("settings")
    object AppRouting : Screen("app-routing/{profileId}") {
        fun createRoute(profileId: Long) = "app-routing/$profileId"
    }
}
