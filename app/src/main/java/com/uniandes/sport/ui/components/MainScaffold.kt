package com.uniandes.sport.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.widget.Toast
import com.uniandes.sport.ui.navigation.Screen
import com.uniandes.sport.ui.navigation.AppNavigation
import com.uniandes.sport.ui.theme.ThemeMode
import com.uniandes.sport.ui.theme.ArchivoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    initialTabIndex: Int = 0,
    pendingOpenMatchEventId: String? = null,
    onOpenMatchConsumed: () -> Unit = {},
    pendingCoachRequest: Boolean = false,
    onCoachRequestConsumed: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeChange: (ThemeMode) -> Unit = {},
    onExitApp: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val fullRoute = navBackStackEntry?.destination?.route ?: "main_tabs/0"
    val isMainTabsRoute = fullRoute.startsWith("main_tabs")
    val playTabIndex = 2
    val context = LocalContext.current
    
    var activeTabPageIndex by remember { mutableIntStateOf(0) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var lastBackPressTime by remember { mutableStateOf(0L) }
    var showBackToast by remember { mutableStateOf(false) }
    
    // Core screens list for BottomBar syncing
    val coreScreens = listOf(Screen.Home, Screen.Challenges, Screen.Play, Screen.Comunidades, Screen.Profesores)

    // Update activeTabPageIndex when navigation occurs (e.g. back button or direct navigate)
    LaunchedEffect(navBackStackEntry) {
        if (fullRoute.startsWith("main_tabs")) {
            val page = navBackStackEntry?.arguments?.getInt("initialPage") ?: 0
            activeTabPageIndex = page
        }
    }

    // React to notification deep-links even when the app is already running.
    LaunchedEffect(pendingOpenMatchEventId) {
        if (!pendingOpenMatchEventId.isNullOrBlank()) {
            navController.navigate("main_tabs/$playTabIndex") {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            onOpenMatchConsumed()
        }
    }

    // React to coach request notifications
    LaunchedEffect(pendingCoachRequest) {
        if (pendingCoachRequest) {
            navController.navigate("main_tabs/4") {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            onCoachRequestConsumed()
        }
    }

    // Reset toast visibility and back press time after 2 seconds
    LaunchedEffect(showBackToast) {
        if (showBackToast) {
            kotlinx.coroutines.delay(2000)
            lastBackPressTime = 0L
            showBackToast = false
        }
    }

    // Determine the logical screen based on the active tab or the full route
    val currentRoute = when {
        fullRoute.startsWith("main_tabs") -> coreScreens[activeTabPageIndex].route
        else -> fullRoute
    }

    // Back gesture handler - TikTok style
    BackHandler {
        if (isMainTabsRoute) {
            if (activeTabPageIndex == 0) {
                // Estamos en HOME
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < 2000) {
                    // Segunda pulsación en menos de 2 segundos - salir
                    onExitApp()
                } else {
                    // Primera pulsación - mostrar toast
                    Toast.makeText(context, "Presiona de nuevo para salir", Toast.LENGTH_SHORT).show()
                    lastBackPressTime = currentTime
                    showBackToast = true
                }
            } else {
                // Si no estamos en HOME, ir a HOME
                navController.navigate("main_tabs/0") {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        } else {
            // Si estamos en otra pantalla (Perfil, Torneos, etc.), volver a HOME
            navController.popBackStack("main_tabs/0", false)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (isMainTabsRoute && currentRoute != Screen.Perfil.route) {
                    TopAppBarDynamic(
                        currentRoute = currentRoute, 
                        onProfileClick = { navController.navigate(Screen.Perfil.route) },
                        themeMode = themeMode,
                        onThemeChange = onThemeChange,
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onToggleSearch = { isSearchActive = !isSearchActive; if(!isSearchActive) searchQuery = "" },
                        onHistoryClick = { navController.navigate(Screen.Historial.route) } // Or a new History screen
                    )
                }
            },
            bottomBar = {
                if (isMainTabsRoute && currentRoute != Screen.Perfil.route) {
                    BottomNavigationBar(
                        navController = navController, 
                        currentRoute = currentRoute, 
                        onTabClick = { index ->
                            // Navigate to the tabs route with the specific page
                            navController.navigate("main_tabs/$index") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            AppNavigation(
                navController = navController, 
                startTabIndex = initialTabIndex,
                pendingOpenMatchEventId = pendingOpenMatchEventId,
                onOpenMatchConsumed = onOpenMatchConsumed,
                innerPadding = innerPadding,
                modifier = Modifier.fillMaxSize(),
                onPageChanged = { page -> 
                    activeTabPageIndex = page
                },
                searchQuery = searchQuery
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarDynamic(
    currentRoute: String, 
    onProfileClick: () -> Unit,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onToggleSearch: () -> Unit = {},
    onHistoryClick: () -> Unit = {}
) {
    var showThemeMenu by remember { mutableStateOf(false) }

    val themeIcon = when (themeMode) {
        ThemeMode.LIGHT -> Icons.Default.LightMode
        ThemeMode.DARK -> Icons.Default.DarkMode
        ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
        ThemeMode.AUTO -> Icons.Default.BrightnessAuto
    }

    if (currentRoute == Screen.Home.route) {
        CenterAlignedTopAppBar(
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            navigationIcon = {
                Box {
                    IconButton(onClick = { showThemeMenu = true }) {
                        Icon(themeIcon, contentDescription = "Theme options")
                    }
                    DropdownMenu(
                        expanded = showThemeMenu,
                        onDismissRequest = { showThemeMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("System Theme") },
                            onClick = { onThemeChange(ThemeMode.SYSTEM); showThemeMenu = false },
                            leadingIcon = { Icon(Icons.Default.SettingsBrightness, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Light Theme") },
                            onClick = { onThemeChange(ThemeMode.LIGHT); showThemeMenu = false },
                            leadingIcon = { Icon(Icons.Default.LightMode, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Dark Theme") },
                            onClick = { onThemeChange(ThemeMode.DARK); showThemeMenu = false },
                            leadingIcon = { Icon(Icons.Default.DarkMode, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Auto (Sensor)") },
                            onClick = { onThemeChange(ThemeMode.AUTO); showThemeMenu = false },
                            leadingIcon = { Icon(Icons.Default.BrightnessAuto, null) }
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "USports",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Black,
                        fontFamily = ArchivoFamily
                    )
                )
            },
            actions = {
                IconButton(onClick = onProfileClick) {
                    Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
                }
            }
        )
    } else {
        val title = when (currentRoute) {
            Screen.Challenges.route -> "Challenges"
            Screen.Play.route -> "Play"
            Screen.Comunidades.route -> "Communities"
            Screen.Profesores.route -> "Coaches"
            Screen.Torneos.route -> "Tournaments"
            Screen.Clima.route -> "Weather"
            Screen.Strava.route -> "Strava"
            Screen.Historial.route -> "History"
            Screen.Perfil.route -> "Profile"
            else -> ""
        }

        val subtitle = when (currentRoute) {
            Screen.Challenges.route -> "COMPETE AND IMPROVE"
            Screen.Play.route -> "FIND YOUR NEXT MATCH"
            Screen.Comunidades.route -> "YOUR SPORTS NETWORK"
            Screen.Profesores.route -> "LEARN FROM EXPERTS"
            Screen.Torneos.route -> "COMPETITIVE EVENTS"
            Screen.Clima.route -> "TRAIN SMARTER"
            Screen.Strava.route -> "PERFORMANCE INSIGHTS"
            Screen.Historial.route -> "RECENT ACTIVITY"
            Screen.Perfil.route -> "ACCOUNT SETTINGS"
            else -> ""
        }

        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(

                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            title = {
                Column {
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ArchivoFamily
                            ),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = ArchivoFamily
                        )
                    )
                }
            },
            actions = {
                if (currentRoute == Screen.Challenges.route) {
                    IconButton(onClick = onHistoryClick) {
                        Icon(Icons.Default.EventNote, contentDescription = "History")
                    }
                }
                IconButton(onClick = onProfileClick) {
                    Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
                }
            }
        )
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController, currentRoute: String?, onTabClick: (Int) -> Unit = {}) {
    var playIconIndex by remember { mutableIntStateOf(0) }
    val playIconsList = remember { 
        listOf(
            Icons.Default.DirectionsRun,
            Icons.Default.SportsSoccer,
            Icons.Default.SportsBasketball,
            Icons.Default.Handshake,
            Icons.Default.SportsTennis,
            Icons.Default.TrackChanges,
            Icons.Default.FitnessCenter,
            Icons.Default.SportsEsports
        ) 
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(12000L)
            playIconIndex = (playIconIndex + 1) % playIconsList.size
        }
    }

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        val items = listOf(
            Screen.Home to Icons.Default.Home,
            Screen.Challenges to Icons.Default.EmojiEvents,
            Screen.Play to playIconsList[playIconIndex],
            Screen.Comunidades to Icons.Default.Group,
            Screen.Profesores to Icons.Default.School
        )

        items.forEachIndexed { index, (screen, icon) ->
            val selected = currentRoute == screen.route
            NavigationBarItem(
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary, // Navy Blue
                    selectedTextColor = MaterialTheme.colorScheme.primary, // Navy Blue
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer, // Mint Green for general tabs
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                icon = { 
                    if (screen == Screen.Play) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)), // Teal for Play FAB
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.animation.Crossfade(
                                targetState = icon,
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 600),
                                label = "PlayIconFade"
                            ) { currentIcon ->
                                Icon(currentIcon, contentDescription = screen.route, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }
                    } else {
                        Icon(icon, contentDescription = screen.route) 
                    }
                },
                label = { 
                    if (screen == Screen.Play) {
                        Text(
                            text = screen.route.replaceFirstChar { it.uppercase() }, 
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                        )
                    } else {
                        Text(
                            text = screen.route.replaceFirstChar { it.uppercase() }, 
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        )
                    }
                },
                selected = selected,
                onClick = {
                    onTabClick(index)
                }
            )
        }
    }
}

