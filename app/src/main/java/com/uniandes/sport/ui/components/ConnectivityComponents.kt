package com.uniandes.sport.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.utils.observeConnectivityAsFlow

/**
 * EVC (Eventual Connectivity) — Componente reutilizable de banner de conectividad.
 *
 * Muestra un banner cuando el dispositivo pierde la red
 * idéntico al del tab Social (Communities).
 */
@Composable
fun OfflineConnectivityBanner(
    modifier: Modifier = Modifier,
    offlineMessage: String = "Showing cached items only",
    messageFontSize: androidx.compose.ui.unit.TextUnit = 14.sp
) {
    val context = LocalContext.current
    val isOnline by context.observeConnectivityAsFlow()
        .collectAsState(initial = isOnlineNow(context))

    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = !isOnline,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = "No connection",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = offlineMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = messageFontSize
                        )
                    }
                }
            }
        }

        // Silent background sync - no "back online" banner shown for non-invasive UX
    }
}

@Composable
fun rememberIsOnline(): Boolean {
    val context = LocalContext.current
    return context.observeConnectivityAsFlow()
        .collectAsState(initial = isOnlineNow(context))
        .value
}

private fun isOnlineNow(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
