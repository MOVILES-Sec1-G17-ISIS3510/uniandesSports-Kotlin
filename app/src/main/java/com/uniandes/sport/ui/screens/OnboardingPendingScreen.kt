package com.uniandes.sport.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.uniandes.sport.data.local.PendingOnboardingStore
import com.uniandes.sport.Routes
import com.uniandes.sport.ui.components.OfflineConnectivityBanner
import com.uniandes.sport.ui.components.ThemeModeToggle
import com.uniandes.sport.ui.components.rememberIsOnline
import com.uniandes.sport.ui.theme.ThemeMode
import kotlinx.coroutines.delay

@Composable
fun OnboardingPendingScreen(
    navController: NavController,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    val pending = remember { mutableStateOf(PendingOnboardingStore.get(context)) }
    val isOnline = rememberIsOnline()
    var wasPending by remember { mutableStateOf(pending.value != null) }

    LaunchedEffect(isOnline) {
        while (pending.value != null) {
            delay(1500)
            pending.value = PendingOnboardingStore.get(context)
            if (pending.value == null) {
                navController.navigate(Routes.MAIN_TABS) {
                    popUpTo(Routes.ONBOARDING_PENDING_SCREEN) { inclusive = true }
                }
                break
            }
        }
        wasPending = pending.value != null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        ThemeModeToggle(
            themeMode = themeMode,
            onThemeChange = onThemeChange,
            modifier = Modifier.align(Alignment.TopEnd)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OfflineConnectivityBanner(
                offlineMessage = "No tienes conexión. Tus datos quedaron guardados y la cuenta se creará cuando vuelva internet."
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "En espera de conexión",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tu onboarding quedó guardado. Cuando vuelva la conexión, se creará la cuenta y recibirás una notificación.",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { pending.value = PendingOnboardingStore.get(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOnline) MaterialTheme.colorScheme.primary else Color(0xFFFBBF24),
                            contentColor = if (isOnline) MaterialTheme.colorScheme.onPrimary else Color.Black
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isOnline) "Revisar estado" else "Sin conexión")
                    }

                    if (wasPending && isOnline) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Hay conexión. Estamos terminando de sincronizar tu cuenta.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
