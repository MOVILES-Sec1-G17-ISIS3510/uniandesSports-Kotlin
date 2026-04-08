package com.uniandes.sport.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.viewmodels.retos.RetosViewModelInterface
import com.uniandes.sport.viewmodels.play.PlayViewModelInterface
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.util.Date
import com.uniandes.sport.ui.components.SportIconBox
import com.uniandes.sport.ui.components.ChallengeBadge
import com.uniandes.sport.ui.screens.tabs.running.RunSummaryDialog
import com.uniandes.sport.viewmodels.running.FirestoreRunningViewModel
import com.uniandes.sport.models.RunSession
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Psychology

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialScreen(
    viewModel: RetosViewModelInterface = androidx.lifecycle.viewmodel.compose.viewModel(modelClass = com.uniandes.sport.viewmodels.retos.FirestoreRetosViewModel::class.java),
    playViewModel: PlayViewModelInterface = androidx.lifecycle.viewmodel.compose.viewModel(modelClass = com.uniandes.sport.viewmodels.play.FirestorePlayViewModel::class.java),
    runningViewModel: FirestoreRunningViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigate: (String) -> Unit,
    onNavigateBack: () -> Unit = { onNavigate("back") }
) {
    val retos by viewModel.retos.collectAsState()
    val finishedEvents by playViewModel.finishedEvents.collectAsState()
    val joinedEventIds by playViewModel.joinedEventIds.collectAsState()
    val myTracksByEventId by playViewModel.myTracksByEventId.collectAsState()
    val pastRuns by runningViewModel.pastRuns.collectAsState()
    
    var selectedRunReport by remember { mutableStateOf<RunSession?>(null) }
    
    val uid = Firebase.auth.currentUser?.uid ?: ""
    
    // Filter for challenges I participated in and are finished or completed
    val finishedChallenges = retos.filter { reto ->
        val isParticipant = reto.participants.contains(uid)
        val isExpired = (reto.endDate?.toDate()?.time ?: 0) < Date().time
        val isCompleted = (reto.progressByUser[uid] ?: 0.0) >= 100.0
        isParticipant && (isExpired || isCompleted)
    }

    val finishedOpenMatches = finishedEvents
        .filter { joinedEventIds.contains(it.id) }
        .sortedByDescending { it.scheduledAt }

    LaunchedEffect(finishedOpenMatches) {
        if (finishedOpenMatches.isNotEmpty()) {
            playViewModel.fetchMyTracksForEvents(finishedOpenMatches.map { it.id })
        }
    }

    LaunchedEffect(Unit) {
        runningViewModel.fetchPastRuns()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("CHALLENGE HISTORY", fontWeight = FontWeight.Black, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (finishedChallenges.isEmpty() && finishedOpenMatches.isEmpty() && pastRuns.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No completed challenges yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Running Sessions Section
                if (pastRuns.isNotEmpty()) {
                    item {
                        Text(
                            text = "RUNNING TRACKS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    items(pastRuns) { run ->
                        RunHistoryCard(run) {
                            selectedRunReport = run
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                if (finishedOpenMatches.isNotEmpty()) {
                    item {
                        Text(
                            text = "Historical match registry",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    items(finishedOpenMatches, key = { it.id }) { event ->
                        OpenMatchHistoryCard(
                            event = event,
                            trackText = myTracksByEventId[event.id]?.text
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                if (finishedChallenges.isNotEmpty()) {
                    item {
                        Text(
                            text = "Historical challenge registry",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                items(finishedChallenges) { reto ->
                    HistoryCard(reto, uid)
                }
            }
        }
    }

    // Report Summary Dialog for Running
    selectedRunReport?.let { run ->
        RunSummaryDialog(session = run, onDismiss = { selectedRunReport = null })
    }
}

@Composable
private fun OpenMatchHistoryCard(event: com.uniandes.sport.models.Event, trackText: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SportIconBox(event.sport, size = 40.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(event.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    val dateLabel = event.scheduledAt?.toDate()?.toString()?.take(16) ?: "Unknown date"
                    Text(dateLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ChallengeBadge("FINISHED", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.RateReview, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    val message = if (trackText.isNullOrBlank()) {
                        "No activity track saved"
                    } else {
                        trackText
                    }
                    Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun HistoryCard(reto: com.uniandes.sport.models.Reto, uid: String) {
    val progress = reto.progressByUser[uid] ?: 0.0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SportIconBox(reto.sport, size = 40.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(reto.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Result: ${progress.toInt()}%", fontSize = 12.sp, color = if (progress >= 100) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (progress >= 100) {
                ChallengeBadge("COMPLETED", Color(0xFFE8F5E9).copy(alpha = 0.8f), Color(0xFF2E7D32))
            } else {
                ChallengeBadge("EXPIRED", Color(0xFFFFEBEE).copy(alpha = 0.8f), Color(0xFFD32F2F))
            }
        }
    }
}

@Composable
fun RunHistoryCard(run: RunSession, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon section
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DirectionsRun,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                val date = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(run.timestamp))
                
                Text(
                    text = date.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                
                Text(
                    text = "${String.format("%.2f", run.distanceKm)} km Running",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    HistoryStat(androidx.compose.material.icons.Icons.Default.Schedule, run.pace)
                    HistoryStat(androidx.compose.material.icons.Icons.Default.Terrain, "${run.elevationGain.toInt()}m")
                    if (run.aiFeedback.isNotEmpty()) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.Psychology, 
                            contentDescription = "Has AI Feedback", 
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            
            Icon(
                androidx.compose.material.icons.Icons.Default.ChevronRight, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun HistoryStat(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
