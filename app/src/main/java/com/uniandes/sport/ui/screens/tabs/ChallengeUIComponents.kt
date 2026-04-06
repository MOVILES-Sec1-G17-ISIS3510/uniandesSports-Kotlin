package com.uniandes.sport.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.models.Reto

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
fun ActiveChallengeCard(reto: Reto, currentUserId: String, onClick: () -> Unit) {
    val progressRaw = reto.progressByUser[currentUserId] ?: 0.0
    val progressPercent = (progressRaw * 100).toInt()

    Card(
        onClick = onClick,
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SportIconBox(reto.sport, size = 40.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(reto.title, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Progress: $progressPercent%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progressRaw.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = Color(0xFF4CAF50),
                trackColor = Color(0xFFE0E0E0)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailItemSmall(Icons.Default.Groups, "${reto.participantsCount}")
                DetailItemSmall(Icons.Default.AccessTime, "Active")
            }
        }
    }
}

@Composable
fun ExploreChallengeCard(reto: Reto, onJoin: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SportIconBox(reto.sport, size = 48.dp)
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(reto.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChallengeBadge(reto.type.uppercase(), Color(0xFFE3F2FD), Color(0xFF1976D2))
                    ChallengeBadge(reto.difficulty.uppercase(), Color(0xFFF3E5F5), Color(0xFF7B1FA2))
                }
                Spacer(modifier = Modifier.height(8.dp))
                DetailItemSmall(Icons.Default.TrackChanges, reto.goalLabel)
            }
            
            Button(
                onClick = onJoin,
                contentPadding = PaddingValues(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("JOIN", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SportIconBox(sport: String, size: Dp) {
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(size/4)).background(Color(0xFFE0F2F1)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when(sport.lowercase()) {
                "running" -> Icons.Default.DirectionsRun
                "soccer" -> Icons.Default.SportsSoccer
                "calisthenics" -> Icons.Default.FitnessCenter
                "tennis" -> Icons.Default.SportsTennis
                else -> Icons.Default.FlashOn
            },
            contentDescription = null,
            tint = Color(0xFF00796B),
            modifier = Modifier.size(size * 0.6f)
        )
    }
}

@Composable
fun ChallengeBadge(text: String, containerColor: Color, contentColor: Color) {
    Surface(color = containerColor, shape = RoundedCornerShape(4.dp)) {
        Text(text = text, color = contentColor, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
fun DetailItemSmall(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 11.sp, color = Color.Gray)
    }
}
