package com.uniandes.sport.ui.screens.tabs.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.patterns.event.EventUIAdapter
import com.uniandes.sport.viewmodels.play.PlayViewModelInterface

@Composable
fun PlayScreen(
    viewModel: PlayViewModelInterface,
    logViewModel: com.uniandes.sport.viewmodels.log.LogViewModelInterface = androidx.lifecycle.viewmodel.compose.viewModel<com.uniandes.sport.viewmodels.log.FirebaseLogViewModel>(),
    onNavigate: (String) -> Unit
) {
    val events by viewModel.events.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedSport by viewModel.selectedSport.collectAsState()
    
    var selectedMode by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog && selectedSport != null && selectedMode != null) {
        CreateMatchDialog(
            sport = selectedSport!!,
            modality = selectedMode!!,
            onDismiss = { showCreateDialog = false },
            onCreate = { title, location, description, date, skillLevel ->
                viewModel.createEvent(title, description, location, selectedSport!!, selectedMode!!, date, skillLevel,
                    onSuccess = { 
                        // Analytics Engine: BQ4 (Registration / Funnel Conversion tracking)
                        logViewModel.log(
                            screen = "PlayScreen",
                            action = "EVENT_REGISTERED",
                            params = mapOf(
                                "source" to "organic", // Or dynamic if pushed from Notification
                                "challenge_type" to selectedMode!!,
                                "sport_category" to selectedSport!!
                            )
                        )
                        showCreateDialog = false 
                    },
                    onError = { /* Optionally show error msg */ }
                )
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp), // padding bottom for sticky bar
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "ACTIVE NOW",
                        value = "24 PLAYERS",
                        icon = Icons.Default.Bolt,
                        iconTint = Color(0xFFF5B041)
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "OPEN MATCHES",
                        value = "5 NEARBY",
                        icon = Icons.Default.EmojiEvents,
                        iconTint = Color(0xFF45B39D)
                    )
                }
            }

            // Section: Choose Your Sport
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (selectedSport != null) "1. SPORT ✓" else "1. CHOOSE YOUR SPORT",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SportButton(Modifier.weight(1f), "Fútbol", Icons.Default.SportsSoccer, Color(0xFF2ECC71), selectedSport == "fútbol") { viewModel.setSportFilter(if (selectedSport == "fútbol") null else "fútbol"); selectedMode = null }
                        SportButton(Modifier.weight(1f), "Basketball", Icons.Default.SportsBasketball, Color(0xFFE67E22), selectedSport == "basketball") { viewModel.setSportFilter(if (selectedSport == "basketball") null else "basketball"); selectedMode = null }
                        SportButton(Modifier.weight(1f), "Tennis", Icons.Default.SportsTennis, Color(0xFFF1C40F), selectedSport == "tennis") { viewModel.setSportFilter(if (selectedSport == "tennis") null else "tennis"); selectedMode = null }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SportButton(Modifier.weight(1f), "Calistenia", Icons.Default.FitnessCenter, Color(0xFF9B59B6), selectedSport == "calistenia") { viewModel.setSportFilter(if (selectedSport == "calistenia") null else "calistenia"); selectedMode = null }
                        SportButton(Modifier.weight(1f), "Running", Icons.Default.DirectionsRun, Color(0xFFE74C3C), selectedSport == "running") { viewModel.setSportFilter(if (selectedSport == "running") null else "running"); selectedMode = null }
                        Box(Modifier.weight(1f))
                    }
                }
            }

            if (selectedSport == null) {
                // Section: Open Matches
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OPEN MATCHES",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${events.size} available",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (events.isEmpty()) {
                    item {
                        Text(
                            "No matches found. Create one!",
                            modifier = Modifier.padding(16.dp),
                            color = Color.Gray
                        )
                    }
                } else {
                    items(events) { event ->
                        val uiModel = EventUIAdapter.toUIModel(event)
                        MatchCard(
                            uiModel = uiModel,
                            onMatchClick = {
                                // Analytics Engine: BQ6 (Available Capacity of interacted sports)
                                logViewModel.log(
                                    screen = "PlayScreen",
                                    action = "MATCH_VIEWED",
                                    params = mapOf(
                                        "sport_category" to event.sport,
                                        "available_capacity" to (event.maxParticipants - event.participants.size).toString(),
                                        "max_capacity" to event.maxParticipants.toString()
                                    )
                                )
                                // TODO: Join / details navigation
                            }
                        )
                    }
                }
            } else {
                // Section: Mode Selection
                item {
                    Text(
                        text = if (selectedMode != null) "2. MODE ✓" else "2. CHOOSE MODE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ModeButton(Modifier.weight(1f), "Casual", "Just for fun", Icons.Default.Handshake, selectedMode == "casual") { selectedMode = "casual" }
                            ModeButton(Modifier.weight(1f), "Amateur", "Competitive but relaxed", Icons.Default.Groups, selectedMode == "amateur") { selectedMode = "amateur" }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ModeButton(Modifier.weight(1f), "Torneo", "Competitive match", Icons.Default.EmojiEvents, selectedMode == "torneo") { selectedMode = "torneo" }
                            ModeButton(Modifier.weight(1f), "Training", "Practice & improve", Icons.Default.TrackChanges, selectedMode == "training") { selectedMode = "training" }
                        }
                    }
                }
            }
        }
        
        // Sticky Bottom Bar
        if (selectedMode != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.setSportFilter(selectedSport) // ensures the search executes
                            // To actually search, we would reset modes or navigate. For now just clear mode to show matches.
                            selectedMode = null
                        },
                        modifier = Modifier.weight(2f).height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Search", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    
                    OutlinedButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Create", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color
) {
    Surface(
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun SportButton(
    modifier: Modifier = Modifier,
    name: String,
    icon: ImageVector,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    
    Surface(
        modifier = modifier
            .height(110.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = if (!selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        tonalElevation = if (selected) 4.dp else 0.dp,
        shadowElevation = if (selected) 4.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.2f) else color),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = name, tint = Color.White)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = contentColor)
        }
    }
}

@Composable
fun ModeButton(
    modifier: Modifier = Modifier,
    name: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val iconContainerColor = if (selected) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer
    val iconTint = if (selected) Color.White else MaterialTheme.colorScheme.primary
    
    Surface(
        modifier = modifier
            .height(86.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = if (!selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        tonalElevation = if (selected) 4.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = name, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = contentColor)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = if (selected) contentColor.copy(alpha=0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MatchCard(uiModel: com.uniandes.sport.patterns.event.EventUIModel, onMatchClick: () -> Unit = {}) {
    val event = uiModel.rawEvent
    
    val (icon, color) = when (event.sport.lowercase()) {
        "fútbol", "futbol", "soccer" -> Icons.Default.SportsSoccer to Color(0xFF2ECC71)
        "basketball", "baloncesto" -> Icons.Default.SportsBasketball to Color(0xFFE67E22)
        "tennis", "tenis" -> Icons.Default.SportsTennis to Color(0xFFF1C40F)
        "calistenia", "calisthenics" -> Icons.Default.FitnessCenter to Color(0xFF9B59B6)
        "running", "correr" -> Icons.Default.DirectionsRun to Color(0xFFE74C3C)
        else -> Icons.Default.Sports to Color.Gray
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMatchClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = event.sport, tint = Color.White)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${uiModel.formattedDate} • ${uiModel.participantsFraction}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Icon(Icons.Default.ChevronRight, contentDescription = "View Details", tint = MaterialTheme.colorScheme.outline)
        }
    }
}
