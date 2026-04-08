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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialScreen(
    viewModel: RetosViewModelInterface = androidx.lifecycle.viewmodel.compose.viewModel(modelClass = com.uniandes.sport.viewmodels.retos.FirestoreRetosViewModel::class.java),
    playViewModel: PlayViewModelInterface = androidx.lifecycle.viewmodel.compose.viewModel(modelClass = com.uniandes.sport.viewmodels.play.FirestorePlayViewModel::class.java),
    onNavigate: (String) -> Unit,
    onNavigateBack: () -> Unit = { onNavigate("back") }
) {
    val retos by viewModel.retos.collectAsState()
    val finishedEvents by playViewModel.finishedEvents.collectAsState()
    val joinedEventIds by playViewModel.joinedEventIds.collectAsState()
    val myReviewsByEventId by playViewModel.myReviewsByEventId.collectAsState()
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
        playViewModel.fetchMyReviewsForEvents(finishedOpenMatches.map { it.id })
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
        if (finishedChallenges.isEmpty() && finishedOpenMatches.isEmpty()) {
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
                if (finishedOpenMatches.isNotEmpty()) {
                    item {
                        Text(
                            text = "OPEN MATCHES HISTORY",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    items(finishedOpenMatches, key = { it.id }) { event ->
                        OpenMatchHistoryCard(
                            event = event,
                            review = myReviewsByEventId[event.id]?.text
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                if (finishedChallenges.isNotEmpty()) {
                    item {
                        Text(
                            text = "CHALLENGE HISTORY",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                items(finishedChallenges) { reto ->
                    HistoryCard(reto, uid)
                }
            }
        }
    }
}

@Composable
private fun OpenMatchHistoryCard(event: com.uniandes.sport.models.Event, review: String?) {
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
                    val message = if (review.isNullOrBlank()) {
                        "No review saved yet"
                    } else {
                        review
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
