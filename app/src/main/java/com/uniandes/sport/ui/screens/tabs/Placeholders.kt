package com.uniandes.sport.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplePlaceholderScreen(title: String, onNavigate: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.uppercase(), fontWeight = FontWeight.Black, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        containerColor = Color(0xFFF9FAFB)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "PRÓXIMAMENTE", 
                    fontWeight = FontWeight.Black, 
                    fontSize = 24.sp, 
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
                Text(
                    "Esta sección de $title está en desarrollo", 
                    fontSize = 14.sp, 
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Home", onNavigate)

@Composable
fun RetosScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Retos", onNavigate)

@Composable
fun PlayScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Play", onNavigate)

@Composable
fun ComunidadesScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Comunidades", onNavigate)

@Composable
fun PerfilUsuarioScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Perfil", onNavigate)

@Composable
fun TorneosScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Torneos", onNavigate)

@Composable
fun ClimaScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Clima", onNavigate)

@Composable
fun StravaScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Strava", onNavigate)

@Composable
fun HistorialScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Historial", onNavigate)
