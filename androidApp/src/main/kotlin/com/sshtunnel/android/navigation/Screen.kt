package com.sshtunnel.android.navigation

/**
 * Sealed class representing all navigation destinations in the app.
 */
sealed class Screen(val route: String) {
    object Profiles : Screen("profiles")
    object Connection : Screen("connection")
    object Settings : Screen("settings")
}
