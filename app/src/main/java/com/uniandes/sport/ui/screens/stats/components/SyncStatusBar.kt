package com.uniandes.sport.ui.screens.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SyncStatusBar — Indicador de estado de sincronización y conectividad.
 * 
 * DOCUMENTACIÓN:
 * - Línea 28-35: Card con color según isOnline
 * - FEATURE: Eventual Connectivity (Requisito d)
 * - Muestra: estado de conectividad
 * - Muestra: Última sincronización + timestamp
 * - Botón: \"Sincronizar ahora\" (si está online y no sincronizando)
 * 
 * FLUJO:
 * isOnline=true + syncStatus=IDLE → Verde + botón habilitado
 * isOnline=false → Rojo + aviso de datos locales
 * syncStatus=SYNCING → Spinner + no clickeable
 * 
 * @author Juan Felipe Hernández
 * @since 26-may-2026
 */
@Composable
fun SyncStatusBar(
    isOnline: Boolean,
    syncStatus: String,
    lastSyncTime: Long?,
    onSync: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOnline) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Línea 40-45: Indicador conectividad
                Text(
                    text = if (isOnline) "🟢 Connected" else "🔴 No connection",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                
                // Línea 47-53: Última sincronización
                if (lastSyncTime != null) {
                    Text(
                        text = "Last sync: ${formatRelativeTime(lastSyncTime)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Línea 55-67: Spinner si sincronizando, botón si idle
            if (syncStatus == "SYNCING") {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Button(
                    onClick = onSync,
                    enabled = isOnline && !isLoading
                ) {
                    Text("Sync")
                }
            }
        }
    }
}

/**
 * formatRelativeTime — Convertir timestamp a texto relativo.
 * 
 * DOCUMENTACIÓN:
 * - Línea 70-85: Calcular diferencia temporal
 * - \"hace 2 minutos\", \"hace 1 hora\", \"ayer\", etc.
 * 
 * @author Juan Felipe Hernández
 */
private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60 * 1000 -> "seconds ago"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m ago"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h ago"
        diff < 2 * 24 * 60 * 60 * 1000 -> "yesterday"
        else -> {
            val format = SimpleDateFormat("dd MMM", Locale.ENGLISH)
            format.format(Date(timestamp))
        }
    }
}
