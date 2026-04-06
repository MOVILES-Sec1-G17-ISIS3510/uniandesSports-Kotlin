package com.uniandes.sport.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
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
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(
                            "FEATURE PREVIEW",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    "COMING SOON", 
                    fontWeight = FontWeight.Black, 
                    fontSize = 24.sp, 
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
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
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Home", onNavigate)

// Removed RetosScreen as it now has its own implementation file

// Removed PlayScreen as it now has its own implementation file

@Composable
fun ComunidadesScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Communities", onNavigate)

@Composable
fun PerfilUsuarioScreen(onNavigate: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val authViewModel: com.uniandes.sport.viewmodels.auth.FirebaseAuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var isLoggingOut by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    SimplePlaceholderScreen("Profile", onNavigate) {
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
fun TorneosScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Tournaments", onNavigate)

@Composable
fun ClimaScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Weather", onNavigate)

@Composable
fun StravaScreen(onNavigate: (String) -> Unit) = SimplePlaceholderScreen("Strava", onNavigate)

