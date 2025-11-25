package com.sshtunnel.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sshtunnel.android.ui.screens.approuting.AppRoutingScreen
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
                onNavigateToConnection = { profileId ->
                    navController.navigate(Screen.Connection.createRoute(profileId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        composable(
            route = Screen.Connection.route,
            arguments = listOf(
                navArgument("profileId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getLong("profileId")?.takeIf { it != -1L }
            ConnectionScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAppRouting = { id ->
                    navController.navigate(Screen.AppRouting.createRoute(id))
                },
                profileId = profileId
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = Screen.AppRouting.route,
            arguments = listOf(
                navArgument("profileId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getLong("profileId") ?: -1L
            AppRoutingScreen(
                profileId = profileId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
