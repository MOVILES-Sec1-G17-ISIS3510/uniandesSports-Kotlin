package com.uniandes.sport.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.ui.theme.ArchivoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplePlaceholderScreen(
    title: String, 
    onNavigate: (String) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    isStandalone: Boolean = false, // If true, provides its own Scaffold/Header
    extraContent: @Composable ColumnScope.() -> Unit = {}
) {
    if (isStandalone) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, fontFamily = ArchivoFamily))
                            Text(
                                "FEATURE PREVIEW",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp, 
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    fontFamily = ArchivoFamily
                                ),
                                color = MaterialTheme.colorScheme.secondary // Teal
                            )
                        }
                    },
                    navigationIcon = {
                        if (onNavigateBack != null) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        if (title != "Profile") {
                            IconButton(onClick = { onNavigate(com.uniandes.sport.ui.navigation.Screen.Perfil.route) }) {
                                Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
                            }
                        }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            PlaceholderContent(title, Modifier.padding(paddingValues), extraContent)
        }
    } else {
        // Just the content for non-standalone screens (Home, etc.)
        PlaceholderContent(title, Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), extraContent)
    }
}

@Composable
fun PlaceholderContent(
    title: String,
    modifier: Modifier = Modifier,
    extraContent: @Composable ColumnScope.() -> Unit = {}
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "COMING SOON", 
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = ArchivoFamily,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            )
            Text(
                "$title is currently under development",
                fontSize = 14.sp, 
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )
            extraContent()
        }
    }
}


// Removed RetosScreen as it now has its own implementation file

// Removed PlayScreen as it now has its own implementation file

@Composable
fun ComunidadesScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Communities", onNavigate, isStandalone = false)

@Composable
fun TorneosScreen(onNavigate: (String) -> Unit, onNavigateBack: () -> Unit) = SimplePlaceholderScreen("Tournaments", onNavigate, onNavigateBack, isStandalone = true)

@Composable
fun StravaScreen(onNavigate: (String) -> Unit, onNavigateBack: () -> Unit) = SimplePlaceholderScreen("Strava", onNavigate, onNavigateBack, isStandalone = true)
