package com.uniandes.sport.ui.screens.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("TODO: HOME SCREEN")
    }
}

@Composable
fun RetosScreen(onNavigate: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("TODO: RETOS SCREEN")
    }
}

@Composable
fun PlayScreen(onNavigate: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("TODO: PLAY SCREEN")
    }
}

@Composable
fun ComunidadesScreen(onNavigate: (String) -> Unit) {
    // For now we inject the DummyViewModel to design the UI. 
    // Later this will be passed down from MainActivity as an Interface.
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.uniandes.sport.viewmodels.communities.DummyCommunitiesViewModel>()
    com.uniandes.sport.ui.screens.tabs.communities.CommunitiesMainScreen(
        viewModel = viewModel,
        onNavigate = onNavigate
    )
}

@Composable
fun ProfesoresScreen(onNavigate: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("TODO: PROFESORES SCREEN")
    }
}

@Composable
fun PerfilUsuarioScreen(onNavigate: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("TODO: PERFIL SCREEN")
    }
}

@Composable
fun TorneosScreen(onNavigate: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("TODO: TORNEOS SCREEN")
    }
}

@Composable
fun ClimaScreen(onNavigate: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("TODO: CLIMA SCREEN")
    }
}

@Composable
fun StravaScreen(onNavigate: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("TODO: STRAVA SCREEN")
    }
}

@Composable
fun HistorialScreen(onNavigate: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("TODO: HISTORIAL SCREEN")
    }
}
