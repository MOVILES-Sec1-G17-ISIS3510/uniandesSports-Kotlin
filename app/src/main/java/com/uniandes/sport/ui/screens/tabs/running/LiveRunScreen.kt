package com.uniandes.sport.ui.screens.tabs.running

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniandes.sport.ui.theme.ArchivoFamily
import com.uniandes.sport.viewmodels.sensors.RunningSessionViewModel
import java.util.Locale

@Composable
fun LiveRunScreen(
    onNavigateBack: () -> Unit,
    runViewModel: RunningSessionViewModel = viewModel()
) {
    val distance by runViewModel.distanceKm.collectAsState()
    val pace by runViewModel.currentPace.collectAsState()
    val elevation by runViewModel.elevationGain.collectAsState()
    val cadence by runViewModel.cadence.collectAsState()
    val isRunning by runViewModel.isRunning.collectAsState()
    val summary by runViewModel.lastSessionSummary.collectAsState()
    val isAnalyzing by runViewModel.isAnalyzing.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val fineLocation = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            if (fineLocation && !isRunning) {
                runViewModel.startRunSession()
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!isRunning) {
            val status = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
            if (status == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                runViewModel.startRunSession()
            } else {
                permissionLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "LIVE RUN",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Black,
                fontFamily = ArchivoFamily,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.padding(top = 24.dp, bottom = 32.dp)
        )

        // Metrics Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricCard(
                title = "PACE",
                value = pace,
                unit = "min/km",
                icon = Icons.Default.Speed,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "DISTANCE",
                value = String.format(Locale.US, "%.2f", distance),
                unit = "km",
                icon = Icons.Default.Map,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricCard(
                title = "ELEVATION",
                value = String.format(Locale.US, "%.0f", elevation),
                unit = "m",
                icon = Icons.Default.Terrain,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "CADENCE",
                value = cadence.toString(),
                unit = "spm",
                icon = Icons.Default.DirectionsRun,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Stop Button
        FloatingActionButton(
            onClick = { 
                runViewModel.stopRunSession()
                // Ya no navegamos atrás inmediatamente, esperamos a que el usuario vea el resumen
            },
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = CircleShape,
            modifier = Modifier.size(80.dp).padding(bottom = 16.dp)
        ) {
            Icon(Icons.Default.Stop, contentDescription = "Stop Run", modifier = Modifier.size(36.dp))
        }
        Text("TAP TO STOP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    // Report Dialog
    summary?.let { session ->
        RunSummaryDialog(
            session = session,
            onDismiss = {
                runViewModel.clearSummary()
                onNavigateBack()
            }
        )
    }
}

@Composable
fun MetricCard(title: String, value: String, unit: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(value, fontSize = 32.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Text("$title ($unit)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary, letterSpacing = 1.sp)
        }
    }
}
