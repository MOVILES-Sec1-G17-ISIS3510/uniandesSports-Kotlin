package com.uniandes.sport.ui.screens.tabs.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import kotlinx.coroutines.delay
import com.uniandes.sport.patterns.event.EventUIAdapter
import com.uniandes.sport.patterns.event.EventUIModel
import com.uniandes.sport.viewmodels.play.PlayViewModelInterface

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PlayScreen(
    viewModel: PlayViewModelInterface,
    openMatchEventId: String? = null,
    onOpenMatchConsumed: () -> Unit = {},
    logViewModel: com.uniandes.sport.viewmodels.log.LogViewModelInterface = androidx.lifecycle.viewmodel.compose.viewModel<com.uniandes.sport.viewmodels.log.FirebaseLogViewModel>(),
    onNavigate: (String) -> Unit
) {
    val events by viewModel.events.collectAsState()
    val finishedEvents by viewModel.finishedEvents.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedSport by viewModel.selectedSport.collectAsState()
    val joinedEventIds by viewModel.joinedEventIds.collectAsState()
    
    var selectedMode by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var isPullRefreshing by remember { mutableStateOf(false) }
    var showAllJoinedMatches by remember { mutableStateOf(false) }
    var showAllFinishedMatches by remember { mutableStateOf(false) }
    var selectedEventUIModel by remember { mutableStateOf<com.uniandes.sport.patterns.event.EventUIModel?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }

    val joinedEvents = remember(events, joinedEventIds) {
        events.filter { joinedEventIds.contains(it.id) }.sortedBy { it.scheduledAt }
    }
    val otherEvents = remember(events, joinedEventIds) {
        events.filterNot { joinedEventIds.contains(it.id) }.sortedBy { it.scheduledAt }
    }
    val visibleJoinedEvents = remember(joinedEvents, showAllJoinedMatches) {
        if (showAllJoinedMatches) joinedEvents else joinedEvents.take(2)
    }
    val visibleFinishedEvents = remember(finishedEvents, showAllFinishedMatches) {
        if (showAllFinishedMatches) finishedEvents else finishedEvents.take(2)
    }

    val onMatchSelected: (com.uniandes.sport.models.Event) -> Unit = { event ->
        logViewModel.log(
            screen = "PlayScreen",
            action = "MATCH_VIEWED",
            params = mapOf(
                "sport_category" to event.sport,
                "available_capacity" to (event.maxParticipants - event.membersCount).toString(),
                "max_capacity" to event.maxParticipants.toString()
            )
        )
        selectedEventUIModel = EventUIAdapter.toUIModel(event)
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isPullRefreshing,
        onRefresh = {
            isPullRefreshing = true
            viewModel.refreshEvents()
        }
    )

    LaunchedEffect(isLoading, isPullRefreshing) {
        if (isPullRefreshing && !isLoading) {
            isPullRefreshing = false
        }
    }

    // If a deep-link asks to open a specific match, clear sport filter to avoid hiding it.
    LaunchedEffect(openMatchEventId) {
        if (!openMatchEventId.isNullOrBlank() && selectedSport != null) {
            viewModel.setSportFilter(null)
        }
    }

    LaunchedEffect(openMatchEventId, events) {
        val pendingId = openMatchEventId ?: return@LaunchedEffect
        if (events.isEmpty()) return@LaunchedEffect

        val pendingEvent = events.firstOrNull { it.id == pendingId }
        if (pendingEvent != null) {
            selectedEventUIModel = EventUIAdapter.toUIModel(pendingEvent)
            onOpenMatchConsumed()
        }
    }

    if (selectedEventUIModel != null) {
        MatchDetailModal(
            uiModel = selectedEventUIModel!!,
            viewModel = viewModel,
            onDismiss = {
                selectedEventUIModel = null
                viewModel.refreshEvents()
            }
        )
    }

    if (showCreateDialog && selectedSport != null && selectedMode != null) {
        CreateMatchDialog(
            sport = selectedSport!!,
            modality = selectedMode!!,
            onDismiss = { showCreateDialog = false },
            onCreate = { title, location, description, date, skillLevel, maxParticipants, dialogOnSuccess, dialogOnError ->
                viewModel.createEvent(
                    title = title,
                    description = description,
                    location = location,
                    sport = selectedSport!!,
                    modality = selectedMode!!,
                    scheduledAt = date,
                    skillLevel = skillLevel,
                    maxParticipants = maxParticipants,
                    onSuccess = { 
                        // Analytics Engine: BQ4 (Registration / Funnel Conversion tracking)
                        logViewModel.log(
                            screen = "PlayScreen",
                            action = "EVENT_REGISTERED",
                            params = mapOf(
                                "source" to "organic",
                                "challenge_type" to selectedMode!!,
                                "sport_category" to selectedSport!!
                            )
                        )
                        dialogOnSuccess()
                        showCreateDialog = false 
                    },
                    onError = { e ->
                        dialogOnError(e)
                        android.widget.Toast.makeText(
                            context, 
                            "Error creating match: ${e.message}", 
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pullRefresh(pullRefreshState)
    ) {
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
                        value = joinedEvents.size.toString(),
                        subtitle = if (joinedEvents.isEmpty()) "You have no joined matches" else "Joined open matches",
                        metric = "${joinedEvents.size} of ${events.size} total",
                        icon = Icons.Default.Bolt,
                        iconTint = Color(0xFFF5B041)
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "OPEN MATCHES",
                        value = events.size.toString(),
                        subtitle = if (otherEvents.isEmpty()) "No additional matches now" else "Ready to join nearby",
                        metric = "${otherEvents.size} available to join",
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
                if (joinedEvents.isNotEmpty()) {
                    item {
                        Text(
                            text = "MY OPEN MATCHES",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "You are already in these matches",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            visibleJoinedEvents.forEach { event ->
                                val remainingMillis = remainingTimeMillis(event, nowMillis)
                                val urgency = countdownUrgency(remainingMillis)

                                JoinedMatchListCard(
                                    uiModel = EventUIAdapter.toUIModel(event),
                                    countdownText = "STARTS IN ${formatRemainingTime(event, nowMillis)}",
                                    urgency = urgency,
                                    onClick = { onMatchSelected(event) }
                                )
                            }

                            if (joinedEvents.size > 2) {
                                TextButton(
                                    onClick = { showAllJoinedMatches = !showAllJoinedMatches },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    val label = if (showAllJoinedMatches) {
                                        "Show less"
                                    } else {
                                        "Show all (${joinedEvents.size})"
                                    }
                                    Text(label, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

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
                                text = "${otherEvents.size} available",
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
                } else if (otherEvents.isEmpty()) {
                    item {
                        Text(
                            if (joinedEvents.isNotEmpty()) "No additional open matches right now." else "Not recent matches found. Create one!",
                            modifier = Modifier.padding(16.dp),
                            color = Color.Gray
                        )
                    }
                } else {
                    items(otherEvents, key = { it.id }) { event ->
                        val uiModel = EventUIAdapter.toUIModel(event)
                        MatchCard(
                            uiModel = uiModel,
                            onMatchClick = { onMatchSelected(event) }
                        )
                    }
                }

                if (finishedEvents.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "FINISHED OPEN MATCHES",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Most recent completed matches",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(visibleFinishedEvents, key = { it.id }) { event ->
                        val uiModel = EventUIAdapter.toUIModel(event)
                        MatchCard(
                            uiModel = uiModel,
                            highlighted = false,
                            badgeText = "FINISHED",
                            onMatchClick = { onMatchSelected(event) }
                        )
                    }

                    if (finishedEvents.size > 2) {
                        item {
                            TextButton(
                                onClick = { showAllFinishedMatches = !showAllFinishedMatches },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val label = if (showAllFinishedMatches) {
                                    "Show less"
                                } else {
                                    "Show all (${finishedEvents.size})"
                                }
                                Text(label, fontWeight = FontWeight.Bold)
                            }
                        }
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

        PullRefreshIndicator(
            refreshing = isPullRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

private fun formatRemainingTime(event: com.uniandes.sport.models.Event, nowMillis: Long): String {
    val targetMillis = event.scheduledAt?.toDate()?.time ?: return "TBD"
    val remaining = (targetMillis - nowMillis).coerceAtLeast(0L)

    val totalMinutes = remaining / 60000
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    val minutes = totalMinutes % 60

    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

private fun remainingTimeMillis(event: com.uniandes.sport.models.Event, nowMillis: Long): Long {
    val targetMillis = event.scheduledAt?.toDate()?.time ?: return Long.MAX_VALUE
    return (targetMillis - nowMillis).coerceAtLeast(0L)
}

private enum class CountdownUrgency { NORMAL, SOON, URGENT }

private fun countdownUrgency(remainingMillis: Long): CountdownUrgency {
    return when {
        remainingMillis <= 60L * 60L * 1000L -> CountdownUrgency.URGENT
        remainingMillis <= 3L * 60L * 60L * 1000L -> CountdownUrgency.SOON
        else -> CountdownUrgency.NORMAL
    }
}

@Composable
private fun JoinedMatchListCard(
    uiModel: com.uniandes.sport.patterns.event.EventUIModel,
    countdownText: String,
    urgency: CountdownUrgency,
    onClick: () -> Unit
) {
    val event = uiModel.rawEvent
    val (urgencyBackground, urgencyText, borderColor) = when (urgency) {
        CountdownUrgency.URGENT -> Triple(Color(0xFFFFE3E0), Color(0xFFB71C1C), Color(0xFFE53935))
        CountdownUrgency.SOON -> Triple(Color(0xFFFFF4E5), Color(0xFF8A4B00), Color(0xFFFF9800))
        CountdownUrgency.NORMAL -> Triple(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        tonalElevation = 4.dp,
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Icon(Icons.Default.ChevronRight, contentDescription = "View", tint = MaterialTheme.colorScheme.outline)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${uiModel.formattedDate} • ${uiModel.participantsFraction}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = urgencyBackground
            ) {
                Text(
                    text = countdownText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = urgencyText
                )
            }
        }
    }
}

@Composable
fun SummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    metric: String,
    icon: ImageVector,
    iconTint: Color
) {
    Surface(
        modifier = modifier.height(132.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                }
            }

            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = iconTint.copy(alpha = 0.12f)
            ) {
                Text(
                    text = metric,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = iconTint,
                    fontWeight = FontWeight.Bold
                )
            }
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
fun MatchCard(
    uiModel: com.uniandes.sport.patterns.event.EventUIModel,
    highlighted: Boolean = false,
    badgeText: String? = null,
    onMatchClick: () -> Unit = {}
) {
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
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
        border = if (highlighted) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null,
        tonalElevation = if (highlighted) 4.dp else 1.dp,
        shadowElevation = if (highlighted) 3.dp else 1.dp
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
                if (!badgeText.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Icon(Icons.Default.ChevronRight, contentDescription = "View Details", tint = MaterialTheme.colorScheme.outline)
        }
    }
}
