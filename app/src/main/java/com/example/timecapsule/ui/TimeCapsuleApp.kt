package com.example.timecapsule.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.timecapsule.ui.screens.CapsuleDetailScreen
import com.example.timecapsule.ui.screens.CreateCapsuleScreen
import com.example.timecapsule.ui.screens.HomeScreen
import com.example.timecapsule.ui.screens.ProfileScreen
import com.example.timecapsule.ui.screens.WelcomeScreen

private object Route {
    const val Welcome = "welcome"
    const val Home = "home"
    const val Create = "create"
    const val Profile = "profile"
    const val Detail = "detail/{capsuleId}"
}

/** Temporary string routes: revisit typed routes after navigation design review. */
@Composable
fun TimeCapsuleApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Route.Welcome) {
        composable(Route.Welcome) {
            WelcomeScreen(onExplorePreview = {
                // UI preview only. This does NOT authenticate the user.
                navController.navigate(Route.Home) {
                    popUpTo(Route.Welcome) { inclusive = true }
                }
            })
        }
        composable(Route.Home) {
            HomeScreen(
                onCreate = { navController.navigate(Route.Create) },
                onProfile = { navController.navigate(Route.Profile) },
            )
        }
        composable(Route.Create) {
            CreateCapsuleScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.Profile) {
            ProfileScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.Detail) { entry ->
            CapsuleDetailScreen(
                capsuleId = entry.arguments?.getString("capsuleId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
