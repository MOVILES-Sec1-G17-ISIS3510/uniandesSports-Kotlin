package com.uniandes.sport.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.utils.observeConnectivityAsFlow
import kotlinx.coroutines.delay

/**
 * EVC (Eventual Connectivity) — Componente reutilizable de banner de conectividad.
 *
 * Muestra un banner animado cuando el dispositivo pierde la red y lo oculta
 * automáticamente (con un breve mensaje de "Back online!") al reconectarse.
 *
 * Regla del rubric: Cada vista protegida debe tener navegación y features offline,
 * no sólo un mensaje genérico para toda la app.
 *
 * @param modifier Modifier a aplicar al banner.
 * @param offlineMessage Mensaje personalizado por vista para modo offline.
 */
@Composable
fun OfflineConnectivityBanner(
    modifier: Modifier = Modifier,
    offlineMessage: String = "You're offline. Showing cached data."
) {
    val context = LocalContext.current
    val isOnline by context.observeConnectivityAsFlow()
        .collectAsState(initial = isOnlineNow(context))

    // Estado para mostrar el mensaje "back online" brevemente
    var showBackOnline by remember { mutableStateOf(false) }
    var wasOffline by remember { mutableStateOf(false) }

    LaunchedEffect(isOnline) {
        if (!isOnline) {
            wasOffline = true
        } else if (wasOffline && isOnline) {
            showBackOnline = true
            delay(3000)
            showBackOnline = false
            wasOffline = false
        }
    }

    Column(modifier = modifier) {
        // Banner de OFFLINE
        AnimatedVisibility(
            visible = !isOnline,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFB91C1C)) // red-700
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Offline",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = "No internet connection",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = offlineMessage,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Banner de RECONEXIÓN (aparece brevemente al volver a estar online)
        AnimatedVisibility(
            visible = showBackOnline,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF15803D)) // green-700
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Back online",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Back online! Data syncing...",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * Composable que expone el estado de conectividad reactivo para usarlo
 * en cualquier parte de la pantalla (p.ej. deshabilitar botones).
 */
@Composable
fun rememberIsOnline(): Boolean {
    val context = LocalContext.current
    return context.observeConnectivityAsFlow()
        .collectAsState(initial = isOnlineNow(context))
        .value
}

/**
 * Comprobación síncrona del estado de red actual (para el valor inicial del Flow).
 */
private fun isOnlineNow(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
