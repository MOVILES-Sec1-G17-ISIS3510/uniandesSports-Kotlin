package com.uniandes.sport.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.uniandes.sport.ui.navigation.Screen
import com.uniandes.sport.ui.navigation.AppNavigation
import com.uniandes.sport.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeChange: (ThemeMode) -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val fullRoute = navBackStackEntry?.destination?.route ?: "main_tabs/0"
    
    var activeTabPageIndex by remember { mutableIntStateOf(0) }
    
    // Core screens list for BottomBar syncing
    val coreScreens = listOf(Screen.Home, Screen.Retos, Screen.Play, Screen.Comunidades, Screen.Profesores)

    // Update activeTabPageIndex when navigation occurs (e.g. back button or direct navigate)
    LaunchedEffect(navBackStackEntry) {
        if (fullRoute.startsWith("main_tabs")) {
            val page = navBackStackEntry?.arguments?.getInt("initialPage") ?: 0
            activeTabPageIndex = page
        }
    }

    // Determine the logical screen based on the active tab or the full route
    val currentRoute = when {
        fullRoute.startsWith("main_tabs") -> coreScreens[activeTabPageIndex].route
        else -> fullRoute
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (currentRoute != Screen.Perfil.route) {
                    TopAppBarDynamic(
                        currentRoute = currentRoute, 
                        onProfileClick = { navController.navigate(Screen.Perfil.route) },
                        themeMode = themeMode,
                        onThemeChange = onThemeChange
                    )
                }
            },
            bottomBar = {
                if (currentRoute != Screen.Perfil.route) {
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
                modifier = Modifier.padding(innerPadding),
                onPageChanged = { page -> 
                    activeTabPageIndex = page
                }
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
    onThemeChange: (ThemeMode) -> Unit
) {
    var showThemeMenu by remember { mutableStateOf(false) }

    if (currentRoute == Screen.Home.route) {
        CenterAlignedTopAppBar(
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            navigationIcon = {
                IconButton(onClick = onProfileClick) {
                    Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
                }
            },
            title = {
                Text(
                    text = "USports",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                )
            },
            actions = {
                Box {
                    IconButton(onClick = { showThemeMenu = true }) {
                        val themeIcon = when (themeMode) {
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                            ThemeMode.DARK -> Icons.Default.DarkMode
                            ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                        }
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
                    }
                }
                IconButton(onClick = { /* TODO: Open app settings screen */ }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        )
        return
    }

    val title = when (currentRoute) {
        Screen.Home.route -> "USports"
        Screen.Retos.route -> "Challenges"
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
        Screen.Home.route -> ""
        Screen.Retos.route -> "COMPETE AND IMPROVE"
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
                    Text(text = subtitle, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.tertiary)
                }
                Text(text = title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black))
            }
        },
        actions = {
            if (currentRoute == Screen.Retos.route) {
                IconButton(onClick = { /* TODO */ }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
                IconButton(onClick = { /* TODO: Challenge history */ }) {
                    Icon(Icons.Default.EventNote, contentDescription = "History")
                }
            }
        }
    )
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
            Screen.Retos to Icons.Default.EmojiEvents,
            Screen.Play to playIconsList[playIconIndex],
            Screen.Comunidades to Icons.Default.Group,
            Screen.Profesores to Icons.Default.School
        )

        items.forEachIndexed { index, (screen, icon) ->
            val selected = currentRoute == screen.route
            NavigationBarItem(
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = if (screen == Screen.Play) Color.White else MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = if (screen == Screen.Play) Color.Transparent else MaterialTheme.colorScheme.secondary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                icon = { 
                    if (screen == Screen.Play) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f)),
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

@Composable
fun FabMenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Text(
                text = text.uppercase(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(imageVector = icon, contentDescription = text, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
