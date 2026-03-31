package com.uniandes.sport.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.uniandes.sport.ui.screens.MainTabsScreen
import com.uniandes.sport.ui.screens.tabs.*

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startTabIndex: Int = 0,
    pendingOpenMatchEventId: String? = null,
    onOpenMatchConsumed: () -> Unit = {},
    onPageChanged: (Int) -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = "main_tabs/$startTabIndex",
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { 300 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -300 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -300 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { 300 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        composable(
            route = "main_tabs/{initialPage}",
            arguments = listOf(androidx.navigation.navArgument("initialPage") { 
                type = androidx.navigation.NavType.IntType
                defaultValue = 0
            })
        ) { backStackEntry ->
            val initialPage = backStackEntry.arguments?.getInt("initialPage") ?: 0
            MainTabsScreen(
                initialPage = initialPage,
                pendingOpenMatchEventId = pendingOpenMatchEventId,
                onOpenMatchConsumed = onOpenMatchConsumed,
                onPageChanged = onPageChanged,
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        
        composable(Screen.Perfil.route) {
            PerfilUsuarioScreen(onNavigate = { route -> navController.navigate(route) })
        }
        composable(Screen.Torneos.route) {
            TorneosScreen(onNavigate = { route -> navController.navigate(route) })
        }
        composable(Screen.Clima.route) {
            ClimaScreen(onNavigate = { route -> navController.navigate(route) })
        }
        composable(Screen.Strava.route) {
            StravaScreen(onNavigate = { route -> navController.navigate(route) })
        }
        composable(Screen.Historial.route) {
            HistorialScreen(onNavigate = { route -> navController.navigate(route) })
        }
        
        composable(
            route = Screen.CoachDashboard.route,
            arguments = listOf(androidx.navigation.navArgument("profesorId") { 
                type = androidx.navigation.NavType.StringType 
            })
        ) { backStackEntry ->
            val profId = backStackEntry.arguments?.getString("profesorId") ?: ""
            CoachDashboardScreen(
                profesorId = profId,
                onNavigate = { route -> navController.navigate(route) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.BookClass.route,
            arguments = listOf(androidx.navigation.navArgument("profesorId") { 
                type = androidx.navigation.NavType.StringType 
            })
        ) { backStackEntry ->
            val profId = backStackEntry.arguments?.getString("profesorId") ?: ""
            BookClassScreen(
                profesorId = profId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
