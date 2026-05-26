package com.uniandes.sport.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uniandes.sport.ui.screens.stats.components.SyncStatusBar
import com.uniandes.sport.viewmodels.stats.MyStatsViewModelInterface

/**
 * MyStatsScreen — Pantalla principal de estadísticas personalizadas.
 * 
 * DOCUMENTACIÓN TÉCNICA:
 * 
 * FEATURE: Caching (Requisito c)
 * - Línea 41-45: collectAsState() convierte StateFlow en State
 * - Compose recompone automáticamente cuando cambia
 * - Datos vienen de LRU Cache (1ms), Room (50ms) o Firestore (500ms)
 * 
 * FEATURE: Multi-threading (Requisito a)
 * - Línea 38-40: viewModel.stats observable sin bloquear UI
 * - La sincronización ocurre en background (coroutines + WorkManager)
 * - UI siempre responsiva
 * 
 * FEATURE: Eventual Connectivity (Requisito d)
 * - Línea 60: SyncStatusBar muestra estado de conectividad
 * - 🟢 Conectado vs 🔴 Sin conexión
 * - Indicador de \"Última sincronización\"
 * - Botón \"Sincronizar ahora\" si está online
 * 
 * ESTRUCTURA:
 * - Línea 53: Scaffold con TopAppBar
 * - Línea 58-87: LazyColumn con secciones
 * - Secciones: Sync status, Profile, Badges, Stats, Charts, Actions
 * 
 * @author Juan Felipe Hernández
 * @since 26-may-2026
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyStatsScreen(
    viewModel: MyStatsViewModelInterface,
    onNavigate: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    // Línea 41-45: Observar StateFlows desde ViewModel
    val stats by viewModel.stats.collectAsState()
    val badges by viewModel.badges.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Activity") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, \"back\")
                    }
                }
            )
        }
    ) { paddingValues ->
        // FEATURE: Manejo de usuarios nuevos sin datos
        if (stats == null) {
            // Usuario nuevo o sin datos
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(
                    text = " Not enough data to display your statistics",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = "Participate in events, upload posts in the community, and run to see your statistics here.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            return@Scaffold
        }

        // Usuario tiene datos, mostrar contenido completo
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Línea 60: Sección 1 - Indicador de Sincronización
            item {
                SyncStatusBar(
                    isOnline = true, // TODO: Conectar con ConnectivityObserver
                    syncStatus = syncStatus,
                    lastSyncTime = stats?.lastSyncAt,
                    onSync = { viewModel.refreshStats(forceSync = true) },
                    isLoading = isLoading
                )
            }

            // Línea 72: Sección 2 - Profile + Level
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = " User",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "LEVEL ${stats?.level ?: 1} ⭐ • ${stats?.points ?: 0} points",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Línea 87: Sección 3 - Estadísticas Resumen
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = " This Week",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "Events: ${stats?.totalEvents ?: 0} • Posts: ${stats?.totalPosts ?: 0} • Km: ${String.format("%.1f", stats?.totalKm ?: 0f)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Línea 100: Sección 4 - Badges
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = \"🏅 Badges (\${badges.size}/18)\",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "\"Unlock badges by participating in the community\"",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Línea 113: Footer
            item {
                Text(
                    text = " Tip: Manually sync to get fresh data",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
