package com.uniandes.sport.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uniandes.sport.models.Reto
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeDetailModal(
    reto: Reto?,
    currentUserId: String,
    onDismiss: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit
) {
    if (reto == null) return

    var showConfirmLeave by remember { mutableStateOf(false) }
    val isJoined = reto.participants.contains(currentUserId)
    val context = androidx.compose.ui.platform.LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }

    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f) // Estilo BottomSheet
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(MaterialTheme.colorScheme.surface),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // HEADER CON GRADIENTE
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF00796B), Color(0xFF004D40))
                            )
                        )
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(24.dp)
                    ) {
                        SportIconBox(reto.sport, size = 56.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = reto.title,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black)
                        )
                    }
                }

                // CONTENIDO
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        InfoCard(
                            label = "GOAL",
                            value = reto.goalLabel,
                            icon = Icons.Default.TrackChanges,
                            modifier = Modifier.weight(1f)
                        )
                        InfoCard(
                            label = "DIFFICULTY",
                            value = reto.difficulty.uppercase(),
                            icon = Icons.Default.SignalCellularAlt,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("CHALLENGE INFO", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = Color.Gray))
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    DetailRow(Icons.Default.CalendarToday, "Start Date", reto.startDate?.toDate()?.let { dateFormat.format(it) } ?: "TBD")
                    DetailRow(Icons.Default.Event, "End Date", reto.endDate?.toDate()?.let { dateFormat.format(it) } ?: "TBD")
                    DetailRow(Icons.Default.Groups, "Type", reto.type.uppercase())
                    DetailRow(Icons.Default.People, "Participants", "${reto.participants.size} athletes joined")
                }

                // ACTION BUTTON AREA (fixed at bottom)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    if (isJoined) {
                        Button(
                            onClick = { showConfirmLeave = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C))
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("LEAVE CHALLENGE", fontWeight = FontWeight.Black)
                        }

                    } else {
                        Button(
                            onClick = { 
                                onJoin()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("JOIN CHALLENGE", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }

    if (showConfirmLeave) {
        AlertDialog(
            onDismissRequest = { showConfirmLeave = false },
            title = { 
                Text(
                    "LEAVE CHALLENGE?", 
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFE74C3C)
                ) 
            },
            text = { 
                Text(
                    "You're doing great so far! Are you sure you want to leave? All your progress will be lost and cannot be recovered.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        onLeave()
                        showConfirmLeave = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("YES, LEAVE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmLeave = false }) {
                    Text("KEEP GOING", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }

}

@Composable
fun InfoCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7F9))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = Color(0xFF00796B))
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF004D40))
        }
    }
}

@Composable
fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFE0F2F1)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = Color(0xFF00796B))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
