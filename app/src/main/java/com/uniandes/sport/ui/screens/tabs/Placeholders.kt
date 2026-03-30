package com.uniandes.sport.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplePlaceholderScreen(
    title: String, 
    onNavigate: (String) -> Unit,
    extraContent: @Composable ColumnScope.() -> Unit = {}
) {
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
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )
                extraContent()
            }
        }
    }
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Home", onNavigate)

// Removed RetosScreen as it now has its own implementation file

// Removed PlayScreen as it now has its own implementation file

@Composable
fun ComunidadesScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Comunidades", onNavigate)

@Composable
fun PerfilUsuarioScreen(onNavigate: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val authViewModel: com.uniandes.sport.viewmodels.auth.FirebaseAuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var isLoggingOut by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    SimplePlaceholderScreen("Perfil", onNavigate) {
        Button(
            onClick = {
                isLoggingOut = true
                authViewModel.logout(
                    onSuccess = {
                        val intent = android.content.Intent(context, com.uniandes.sport.MainActivity::class.java).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        context.startActivity(intent)
                    },
                    onFailure = { e ->
                        isLoggingOut = false
                        android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.padding(top = 24.dp).fillMaxWidth(0.6f).height(50.dp)
        ) {
            if (isLoggingOut) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("LOGOUT", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TorneosScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Torneos", onNavigate)

@Composable
fun ClimaScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Clima", onNavigate)

@Composable
fun StravaScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Strava", onNavigate)

@Composable
fun HistorialScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Historial", onNavigate)
