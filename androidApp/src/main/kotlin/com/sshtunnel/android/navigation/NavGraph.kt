package com.sshtunnel.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sshtunnel.android.ui.screens.connection.ConnectionScreen
import com.sshtunnel.android.ui.screens.profiles.ProfilesScreen
import com.sshtunnel.android.ui.screens.settings.SettingsScreen

/**
 * Navigation graph for the SSH Tunnel Proxy app.
 * Defines all navigation routes and their corresponding screens.
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Profiles.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Profiles.route) {
            ProfilesScreen(
                onNavigateToConnection = {
                    navController.navigate(Screen.Connection.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        composable(Screen.Connection.route) {
            ConnectionScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
