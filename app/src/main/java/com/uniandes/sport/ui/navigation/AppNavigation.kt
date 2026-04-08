package com.uniandes.sport.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.foundation.layout.padding
import androidx.navigation.compose.rememberNavController
import com.uniandes.sport.ui.screens.MainTabsScreen
import com.uniandes.sport.ui.screens.tabs.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniandes.sport.viewmodels.profesores.FirestoreProfesoresViewModel

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startTabIndex: Int = 0,
    pendingOpenMatchEventId: String? = null,
    onOpenMatchConsumed: () -> Unit = {},
    innerPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    onPageChanged: (Int) -> Unit = {},
    searchQuery: String = ""
) {
    NavHost(
        navController = navController,
        startDestination = "main_tabs/$startTabIndex",
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(350))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 3 },
                animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(350))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 3 },
                animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(350))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(350))
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
                pendingOpenEventId = pendingOpenMatchEventId,
                onOpenEventConsumed = onOpenMatchConsumed,
                onPageChanged = onPageChanged,
                onNavigate = { route -> navController.navigate(route) },
                searchQuery = searchQuery,
                modifier = Modifier.padding(innerPadding)
            )
        }
        
        composable(Screen.Perfil.route) {
            PerfilUsuarioScreen(
                onNavigate = { route -> navController.navigate(route) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Torneos.route) {
            TorneosScreen(
                onNavigate = { route -> navController.navigate(route) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Clima.route) {
            ClimaScreen(
                onNavigate = { route -> navController.navigate(route) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Strava.route) {
            StravaScreen(
                onNavigate = { route -> navController.navigate(route) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Historial.route) {
            HistorialScreen(
                onNavigate = { route -> navController.navigate(route) },
                onNavigateBack = { navController.popBackStack() }
            )
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
                onNavigateBack = { navController.popBackStack() },
                onOpenProfile = { navController.navigate(Screen.Perfil.route) }
            )
        }

        composable(
            route = Screen.CoachProfile.route,
            arguments = listOf(androidx.navigation.navArgument("profesorId") { 
                type = androidx.navigation.NavType.StringType 
            })
        ) { backStackEntry ->
            val profId = backStackEntry.arguments?.getString("profesorId") ?: ""
            val profesoresViewModel: FirestoreProfesoresViewModel = viewModel()
            CoachProfileScreen(
                profesorId = profId,
                profesoresViewModel = profesoresViewModel,
                onNavigateBack = { navController.popBackStack() },
                onBookClass = { id -> navController.navigate(Screen.BookClass.route.replace("{profesorId}", id)) }
            )
        }
    }
}

