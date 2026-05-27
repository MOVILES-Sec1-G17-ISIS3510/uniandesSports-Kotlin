package com.uniandes.sport.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.uniandes.sport.utils.observeConnectivityAsFlow
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
    var pendingBestMatchEventId by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var pendingBestMatchIsRecommendation by remember { androidx.compose.runtime.mutableStateOf(false) }

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
            val effectivePendingOpenMatchEventId = pendingBestMatchEventId ?: pendingOpenMatchEventId
            val effectivePendingOpenMatchFromBestMatch = pendingBestMatchIsRecommendation
            MainTabsScreen(
                initialPage = initialPage,
                pendingOpenEventId = effectivePendingOpenMatchEventId,
                pendingOpenEventFromBestMatch = effectivePendingOpenMatchFromBestMatch,
                onOpenEventConsumed = {
                    pendingBestMatchEventId = null
                    pendingBestMatchIsRecommendation = false
                    onOpenMatchConsumed()
                },
                onPageChanged = onPageChanged,
                onNavigate = { route -> 
                    val tabRoute = when {
                        route.startsWith("play_best_match/") -> {
                            pendingBestMatchEventId = route.substringAfterLast("/")
                            pendingBestMatchIsRecommendation = true
                            "main_tabs/2"
                        }
                        route == "challenges" -> "main_tabs/1"
                        route == "play" -> "main_tabs/2"
                        route == "social" -> "main_tabs/3"
                        route == "coaches" -> "main_tabs/4"
                        else -> route
                    }
                    navController.navigate(tabRoute) 
                },
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
        composable(Screen.MyStats.route) {
            val context = LocalContext.current
            val connectivityFlow = remember { context.observeConnectivityAsFlow() }
            com.uniandes.sport.ui.screens.stats.MyStatsScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = com.uniandes.sport.viewmodels.stats.MyStatsViewModel.provideFactory(
                        com.uniandes.sport.data.repositories.MyStatsRepository(
                            com.uniandes.sport.data.database.StatsDatabase.getDatabase(context),
                            com.uniandes.sport.data.cache.BadgeArrayMapCache()
                        ),
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "",
                        connectivityFlow = connectivityFlow
                    )
                ),
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
        
        composable(Screen.LiveRun.route) {
            com.uniandes.sport.ui.screens.tabs.running.LiveRunScreen(
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

        composable(
            route = Screen.CoachComparison.route,
            arguments = listOf(androidx.navigation.navArgument("coachIds") {
                type = androidx.navigation.NavType.StringType
            })
        ) { backStackEntry ->
            val coachIds = backStackEntry.arguments?.getString("coachIds") ?: ""
            val comparisonViewModel: com.uniandes.sport.viewmodels.profesores.CoachComparisonViewModel = viewModel()
            CoachComparisonScreen(
                coachIds = coachIds,
                viewModel = comparisonViewModel,
                onNavigateBack = { navController.popBackStack() },
                onBookClass = { id -> navController.navigate(Screen.BookClass.route.replace("{profesorId}", id)) },
                onViewProfile = { id -> navController.navigate(Screen.CoachProfile.route.replace("{profesorId}", id)) }
            )
        }

        composable(Screen.SportTools.route) {
            com.uniandes.sport.ui.screens.sport_tools.SportToolsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToWarmup = { navController.navigate(Screen.WarmupConfig.route) }
            )
        }

        composable(Screen.WarmupConfig.route) {
            com.uniandes.sport.ui.screens.sport_tools.WarmupConfigScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToExercises = { category, intensity ->
                    navController.navigate("warmup_exercises/$category/$intensity")
                }
            )
        }

        composable(
            route = Screen.WarmupExercises.route,
            arguments = listOf(
                androidx.navigation.navArgument("category") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("intensity") { type = androidx.navigation.NavType.StringType }
            )
        ) { backStackEntry ->
            val cat = backStackEntry.arguments?.getString("category")?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: ""
            val int = backStackEntry.arguments?.getString("intensity")?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: ""
            com.uniandes.sport.ui.screens.sport_tools.WarmupExercisesScreen(
                category = cat,
                intensity = int,
                onNavigateBack = { navController.popBackStack() }
            )
        }

    }
}

