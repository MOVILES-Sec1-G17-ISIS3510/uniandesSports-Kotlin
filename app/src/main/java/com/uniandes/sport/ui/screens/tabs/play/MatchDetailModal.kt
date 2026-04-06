package com.uniandes.sport.ui.screens.tabs.play

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uniandes.sport.patterns.event.EventUIModel
import com.uniandes.sport.viewmodels.play.PlayViewModelInterface

@Composable
fun MatchDetailModal(
    uiModel: EventUIModel,
    viewModel: PlayViewModelInterface,
    onDismiss: () -> Unit
) {
    val event = uiModel.rawEvent
    val currentUserId = viewModel.currentUserId
    val members by viewModel.members.collectAsState()
    val isAlreadyJoined = currentUserId != null && members.any { it.userId == currentUserId }
    val isFull = event.membersCount >= event.maxParticipants
    
    LaunchedEffect(members) {
        android.util.Log.d("MatchModal", "Members updated: ${members.size} members found. isAlreadyJoined: $isAlreadyJoined")
    }
    
    var isLoading by remember { mutableStateOf(false) }
    var showConfirmLeave by remember { mutableStateOf(false) }
    var showConfirmCancel by remember { mutableStateOf(false) }

    
    LaunchedEffect(event.id) {
        viewModel.fetchMembers(event.id)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(36.dp).clickable { onDismiss() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Sport Badge & Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, color) = getSportIconAndColor(event.sport)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = color.copy(alpha = 0.2f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = event.title.uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = event.modality.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Details Grid
                DetailRow(Icons.Default.CalendarToday, "When", uiModel.formattedDate)
                Spacer(modifier = Modifier.height(16.dp))
                DetailRow(Icons.Default.LocationOn, "Where", event.location)
                Spacer(modifier = Modifier.height(16.dp))
                DetailRow(Icons.Default.Groups, "Participants", "${members.size} / ${event.maxParticipants}")
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Description
                Text("ABOUT THIS MATCH", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = event.description.ifEmpty { "No description provided." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Members List
                Text("MEMBERS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    members.forEach { member ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Surface(shape = CircleShape, color = if (member.role == "organizer") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(32.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(if (member.role == "organizer") Icons.Default.Stars else Icons.Default.Person, contentDescription = null, tint = if (member.role == "organizer") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (member.userId == currentUserId) "You" else member.displayName.take(15) + (if (member.displayName.length > 15) "..." else ""),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (member.role == "organizer") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("Admin", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                            }
                            
                            // Kick button for organizers
                            if (currentUserId == event.createdBy && member.userId != currentUserId) {
                                IconButton(onClick = {
                                    viewModel.kickMember(event.id, member.userId)
                                }) {
                                    Icon(Icons.Default.PersonRemove, contentDescription = "Kick", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
                
                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isAlreadyJoined) {
                        // LEAVE MATCH button
                        Button(
                            onClick = { showConfirmLeave = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE74C3C), // Strong Red
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("LEAVE MATCH", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        // CANCEL MATCH button (Owners only)
                        if (currentUserId == event.createdBy) {
                            OutlinedButton(
                                onClick = { showConfirmCancel = true },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                enabled = !isLoading,
                                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFE74C3C).copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFE74C3C)
                                )
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CANCEL MATCH FOR EVERYONE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                            }
                        }
                    } else {
                        // JOIN MATCH button
                        Button(
                            onClick = {
                                if (currentUserId != null) {
                                    isLoading = true
                                    viewModel.joinEvent(event.id, currentUserId, 
                                        onSuccess = { isLoading = false; onDismiss() },
                                        onError = { isLoading = false }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isFull && !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                if (isFull) {
                                    Text("MATCH FULL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("JOIN MATCH", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmLeave) {
        PremiumActionDialog(
            title = "Leave this match?",
            description = "You'll be removed from the participants list. You can join again later if there's space.",
            confirmLabel = "YES, LEAVE",
            isDestructive = true,
            onConfirm = {
                if (currentUserId != null) {
                    isLoading = true
                    viewModel.leaveEvent(event.id, currentUserId,
                        onSuccess = { 
                            isLoading = false
                            showConfirmLeave = false
                            onDismiss() 
                        },
                        onError = { 
                            isLoading = false
                            showConfirmLeave = false
                        }
                    )
                }
            },
            onDismiss = { showConfirmLeave = false }
        )
    }

    if (showConfirmCancel) {
        PremiumActionDialog(
            title = "CANCEL MATCH?",
            description = "This will notify all participants and remove the match forever. This action cannot be undone.",
            confirmLabel = "YES, CANCEL IT",
            isDestructive = true,
            onConfirm = {
                isLoading = true
                viewModel.cancelEvent(event.id,
                    onSuccess = { 
                        isLoading = false
                        showConfirmCancel = false
                        onDismiss() 
                    },
                    onError = { 
                        isLoading = false
                        showConfirmCancel = false
                    }
                )
            },
            onDismiss = { showConfirmCancel = false }
        )
    }
}

@Composable
fun PremiumActionDialog(
    title: String,
    description: String,
    confirmLabel: String,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = if (isDestructive) Color(0xFFE74C3C) else MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) Color(0xFFE74C3C) else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("KEEP GOING", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}


@Composable
fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun getSportIconAndColor(sport: String): Pair<ImageVector, Color> {
    return when (sport.lowercase()) {
        "fútbol", "futbol", "soccer" -> Icons.Default.SportsSoccer to Color(0xFF2ECC71)
        "basketball", "baloncesto" -> Icons.Default.SportsBasketball to Color(0xFFE67E22)
        "tennis", "tenis" -> Icons.Default.SportsTennis to Color(0xFFF1C40F)
        "calistenia", "calisthenics" -> Icons.Default.FitnessCenter to Color(0xFF9B59B6)
        "running", "correr" -> Icons.Default.DirectionsRun to Color(0xFFE74C3C)
        else -> Icons.Default.Sports to Color.Gray
    }
}
