package com.uniandes.sport.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.viewmodels.retos.RetosViewModelInterface
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialScreen(
    viewModel: RetosViewModelInterface = androidx.lifecycle.viewmodel.compose.viewModel(modelClass = com.uniandes.sport.viewmodels.retos.FirestoreRetosViewModel::class.java),
    onNavigate: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val retos by viewModel.retos.collectAsState()
    val uid = Firebase.auth.currentUser?.uid ?: ""
    
    // Filter for challenges I participated in and are finished or completed
    val finishedChallenges = retos.filter { reto ->
        val isParticipant = reto.participants.contains(uid)
        val isExpired = (reto.endDate?.toDate()?.time ?: 0) < Date().time
        val isCompleted = (reto.progressByUser[uid] ?: 0.0) >= 1.0
        isParticipant && (isExpired || isCompleted)
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
        containerColor = Color(0xFFF8F9FA)
    ) { padding ->
        if (finishedChallenges.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No completed challenges yet", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(finishedChallenges) { reto ->
                    HistoryCard(reto, uid)
                }
            }
        }
    }
}

@Composable
fun HistoryCard(reto: com.uniandes.sport.models.Reto, uid: String) {
    val progress = (reto.progressByUser[uid] ?: 0.0) * 100
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SportIconBox(reto.sport, size = 40.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(reto.title, fontWeight = FontWeight.Bold)
                Text("Result: ${progress.toInt()}%", fontSize = 12.sp, color = if (progress >= 100) Color(0xFF4CAF50) else Color.Gray)
            }
            if (progress >= 100) {
                ChallengeBadge("COMPLETED", Color(0xFFE8F5E9), Color(0xFF2E7D32))
            } else {
                ChallengeBadge("EXPIRED", Color(0xFFFFEBEE), Color(0xFFD32F2F))
            }
        }
    }
}
