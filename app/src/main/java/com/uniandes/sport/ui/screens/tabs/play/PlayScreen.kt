package com.uniandes.sport.ui.screens.tabs.play

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import java.util.Locale
import com.uniandes.sport.patterns.event.EventUIAdapter
import com.uniandes.sport.patterns.event.EventUIModel
import com.uniandes.sport.viewmodels.play.PlayViewModelInterface

import com.uniandes.sport.ui.components.FabMenuItem
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.draw.rotate

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(
    viewModel: PlayViewModelInterface,
    openEventId: String? = null,
    onOpenEventConsumed: () -> Unit = {},
    logViewModel: com.uniandes.sport.viewmodels.log.LogViewModelInterface = androidx.lifecycle.viewmodel.compose.viewModel<com.uniandes.sport.viewmodels.log.FirebaseLogViewModel>(),
    onNavigate: (String) -> Unit
) {
    val events by viewModel.events.collectAsState()
    val inProgressEvents by viewModel.inProgressEvents.collectAsState()
    val finishedEvents by viewModel.finishedEvents.collectAsState()
    val myReviewsByEventId by viewModel.myReviewsByEventId.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedSport by viewModel.selectedSport.collectAsState()
    val joinedEventIds by viewModel.joinedEventIds.collectAsState()
    
    var selectedMode by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var isPullRefreshing by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }
    var showAllJoinedEvents by remember { mutableStateOf(false) }
    var showAllFinishedEvents by remember { mutableStateOf(false) }
    var selectedEventUIModel by remember { mutableStateOf<com.uniandes.sport.patterns.event.EventUIModel?>(null) }
    var reviewEvent by remember { mutableStateOf<com.uniandes.sport.models.Event?>(null) }
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
    val visibleJoinedEvents = remember(joinedEvents, showAllJoinedEvents) {
        if (showAllJoinedEvents) joinedEvents else joinedEvents.take(2)
    }
    val visibleFinishedEvents = remember(finishedEvents, showAllFinishedEvents) {
        if (showAllFinishedEvents) finishedEvents else finishedEvents.take(2)
    }

    val onEventSelected: (com.uniandes.sport.models.Event) -> Unit = { event ->
        logViewModel.log(
            screen = "PlayScreen",
            action = "EVENT_VIEWED",
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

    // If a deep-link asks to open a specific Event, clear sport filter to avoid hiding it.
    LaunchedEffect(openEventId) {
        if (!openEventId.isNullOrBlank() && selectedSport != null) {
            viewModel.setSportFilter(null)
        }
    }

    LaunchedEffect(openEventId, events) {
        val pendingId = openEventId ?: return@LaunchedEffect
        if (events.isEmpty()) return@LaunchedEffect

        val pendingEvent = events.firstOrNull { it.id == pendingId }
        if (pendingEvent != null) {
            selectedEventUIModel = EventUIAdapter.toUIModel(pendingEvent)
            onOpenEventConsumed()
        }
    }

    LaunchedEffect(inProgressEvents, finishedEvents) {
        val ids = (inProgressEvents + finishedEvents).map { it.id }.distinct()
        viewModel.fetchMyReviewsForEvents(ids)
    }

    if (selectedEventUIModel != null) {
        EventDetailModal(
            uiModel = selectedEventUIModel!!,
            viewModel = viewModel,
            onDismiss = {
                selectedEventUIModel = null
                viewModel.refreshEvents()
            }
        )
    }

    if (reviewEvent != null) {
        val existingReview = myReviewsByEventId[reviewEvent!!.id]
        ReviewDialog(
            event = reviewEvent!!,
            existingReview = existingReview,
            viewModel = viewModel,
            onDismiss = { reviewEvent = null },
            onSubmit = { text, rating, attendanceByUserId, source, onDone ->
                viewModel.submitReview(
                    eventId = reviewEvent!!.id,
                    reviewText = text,
                    rating = rating,
                    attendanceByUserId = attendanceByUserId,
                    source = source,
                    onSuccess = {
                        android.widget.Toast.makeText(context, "Review saved", android.widget.Toast.LENGTH_SHORT).show()
                        onDone(true)
                    },
                    onError = { e ->
                        android.widget.Toast.makeText(context, "Could not save review: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        onDone(false)
                    }
                )
            }
        )
    }

    if (showCreateDialog && selectedMode != null) {
        val creationSport = selectedSport ?: "soccer" // Default to soccer if no filter active
        CreateEventDialog(
            sport = creationSport,
            modality = selectedMode!!,
            onDismiss = { showCreateDialog = false },
            onCreate = { finalSport, title, location, description, date, skillLevel, maxParticipants, shouldJoin, dialogOnSuccess, dialogOnError ->
                viewModel.createEvent(
                    title = title,
                    description = description,
                    location = location,
                    sport = finalSport,
                    modality = selectedMode!!,
                    scheduledAt = date,
                    skillLevel = skillLevel,
                    maxParticipants = maxParticipants,
                    shouldJoin = shouldJoin,
                    onSuccess = { 
                        // Analytics Engine: BQ4 (Registration / Funnel Conversion tracking)
                        logViewModel.log(
                            screen = "PlayScreen",
                            action = "EVENT_REGISTERED",
                            params = mapOf(
                                "source" to "organic",
                                "challenge_type" to selectedMode!!,
                                "sport_category" to finalSport
                            )
                        )
                        dialogOnSuccess()
                    },
                    onError = { e ->
                        dialogOnError(e)
                        android.widget.Toast.makeText(
                            context, 
                            "Error creating event: ${e.message}", 
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // New Compact Sport Filter
            item {
                Text(
                    text = "EXPLORE BY SPORT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    val categories = listOf(
                        "soccer" to Icons.Default.SportsSoccer,
                        "basketball" to Icons.Default.SportsBasketball,
                        "tennis" to Icons.Default.SportsTennis,
                        "calisthenics" to Icons.Default.FitnessCenter,
                        "running" to Icons.Default.DirectionsRun
                    )
                    
                    items(categories) { (name, icon) ->
                        val isSelected = selectedSport == name
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setSportFilter(if (isSelected) null else name) },
                            label = { Text(name.replaceFirstChar { it.uppercase() }) },
                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            if (inProgressEvents.isNotEmpty()) {
                item {
                    Text(
                        text = "IN PROGRESS NOW",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Events started less than 1 hour ago",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(inProgressEvents, key = { it.id }) { event ->
                    val uiModel = EventUIAdapter.toUIModel(event)
                    EventCard(
                        uiModel = uiModel,
                        badgeText = "IN PROGRESS",
                        reviewLabel = if (myReviewsByEventId.containsKey(event.id)) "Edit review" else "Write review",
                        onReviewClick = { reviewEvent = event },
                        onEventClick = { onEventSelected(event) }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "ACTIVE NOW",
                        value = joinedEvents.size.toString(),
                        subtitle = if (joinedEvents.isEmpty()) "You have no joined events" else "Joined open events",
                        metric = "${joinedEvents.size} of ${events.size} total",
                        icon = Icons.Default.Bolt,
                        iconTint = Color(0xFFF5B041)
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "OPEN EVENTS",
                        value = events.size.toString(),
                        subtitle = if (otherEvents.isEmpty()) "No additional events now" else "Ready to join nearby",
                        metric = "${otherEvents.size} available to join",
                        icon = Icons.Default.EmojiEvents,
                        iconTint = Color(0xFF45B39D)
                    )
                }
            }


            if (selectedSport == null) {
                if (joinedEvents.isNotEmpty()) {
                    item {
                        Text(
                            text = "MY OPEN EVENTS",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "You are already in these events",
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

                                JoinedEventListCard(
                                    uiModel = EventUIAdapter.toUIModel(event),
                                    countdownText = "STARTS IN ${formatRemainingTime(event, nowMillis)}",
                                    urgency = urgency,
                                    onClick = { onEventSelected(event) }
                                )
                            }

                            if (joinedEvents.size > 2) {
                                TextButton(
                                    onClick = { showAllJoinedEvents = !showAllJoinedEvents },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    val label = if (showAllJoinedEvents) {
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

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OPEN EVENTS",
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
                            if (joinedEvents.isNotEmpty()) "No additional open events right now." else "No recent events found. Create one!",
                            modifier = Modifier.padding(16.dp),
                            color = Color.Gray
                        )
                    }
                } else {
                    items(otherEvents, key = { it.id }) { event ->
                        val uiModel = EventUIAdapter.toUIModel(event)
                        EventCard(
                            uiModel = uiModel,
                            onEventClick = { onEventSelected(event) }
                        )
                    }
                }

                if (finishedEvents.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "FINISHED OPEN Events",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Most recent completed Events",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(visibleFinishedEvents, key = { it.id }) { event ->
                        val uiModel = EventUIAdapter.toUIModel(event)
                        EventCard(
                            uiModel = uiModel,
                            highlighted = false,
                            badgeText = "FINISHED",
                            reviewLabel = if (myReviewsByEventId.containsKey(event.id)) "Edit review" else "Write review",
                            onReviewClick = { reviewEvent = event },
                            onEventClick = { onEventSelected(event) }
                        )
                    }

                    if (finishedEvents.size > 2) {
                        item {
                            TextButton(
                                onClick = { showAllFinishedEvents = !showAllFinishedEvents },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val label = if (showAllFinishedEvents) {
                                    "Show less"
                                } else {
                                    "Show all (${finishedEvents.size})"
                                }
                                Text(label, fontWeight = FontWeight.Bold)
                            }
                        }
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

        // FAB Menu Overlay
        if (isFabExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable { isFabExpanded = false },
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(
                    modifier = Modifier.padding(end = 20.dp, bottom = 100.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FabMenuItem(
                        text = "Training",
                        icon = Icons.Default.TrackChanges,
                        onClick = { 
                            selectedMode = "training"
                            showCreateDialog = true
                            isFabExpanded = false
                        }
                    )
                    FabMenuItem(
                        text = "Tournament",
                        icon = Icons.Default.EmojiEvents,
                        onClick = { 
                            selectedMode = "torneo"
                            showCreateDialog = true
                            isFabExpanded = false
                        }
                    )
                    FabMenuItem(
                        text = "Amateur Match",
                        icon = Icons.Default.Groups,
                        onClick = { 
                            selectedMode = "amateur"
                            showCreateDialog = true
                            isFabExpanded = false
                        }
                    )
                    FabMenuItem(
                        text = "Casual Match",
                        icon = Icons.Default.Handshake,
                        onClick = { 
                            selectedMode = "casual"
                            showCreateDialog = true
                            isFabExpanded = false
                        }
                    )
                }
            }
        }

        // Standardized Expanding FAB
        FloatingActionButton(
            onClick = { isFabExpanded = !isFabExpanded },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            val rotation by animateFloatAsState(targetValue = if (isFabExpanded) 135f else 0f, label = "fabRotation")
            Icon(
                Icons.Default.Add, 
                contentDescription = "Create Event",
                modifier = Modifier.rotate(rotation)
            )
        }
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
private fun JoinedEventListCard(
    uiModel: com.uniandes.sport.patterns.event.EventUIModel,
    countdownText: String,
    urgency: CountdownUrgency,
    reviewLabel: String = "Write review",
    onReviewClick: (() -> Unit)? = null,
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

            if (onReviewClick != null) {
                TextButton(
                    onClick = onReviewClick,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Icon(Icons.Default.RateReview, contentDescription = "Review", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(reviewLabel, fontWeight = FontWeight.Bold)
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
fun EventCard(
    uiModel: com.uniandes.sport.patterns.event.EventUIModel,
    highlighted: Boolean = false,
    badgeText: String? = null,
    reviewLabel: String = "Write review",
    onReviewClick: (() -> Unit)? = null,
    onEventClick: () -> Unit = {}
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
            .clickable { onEventClick() },
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

                if (onReviewClick != null) {
                    TextButton(
                        onClick = onReviewClick,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Icon(Icons.Default.RateReview, contentDescription = "Review", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(reviewLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Icon(Icons.Default.ChevronRight, contentDescription = "View Details", tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ReviewDialog(
    event: com.uniandes.sport.models.Event,
    existingReview: com.uniandes.sport.models.OpenMatchReview?,
    viewModel: PlayViewModelInterface,
    onDismiss: () -> Unit,
    onSubmit: (text: String, rating: Int, attendanceByUserId: Map<String, Boolean>, source: String, onDone: (Boolean) -> Unit) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var reviewText by remember(existingReview?.text) { mutableStateOf(existingReview?.text.orEmpty()) }
    var rating by remember(existingReview?.rating) { mutableIntStateOf(existingReview?.rating ?: 0) }
    var submitting by remember { mutableStateOf(false) }
    var inputSource by remember { mutableStateOf(existingReview?.source ?: "text") }
    var members by remember { mutableStateOf<List<com.uniandes.sport.models.MatchMember>>(emptyList()) }
    var loadingMembers by remember { mutableStateOf(true) }
    val attendanceByUserId = remember { mutableStateMapOf<String, Boolean>() }
    val scrollState = rememberScrollState()

    LaunchedEffect(event.id, existingReview?.attendanceByUserId) {
        loadingMembers = true
        viewModel.fetchEventMembersOnce(
            eventId = event.id,
            onSuccess = { list ->
                members = list
                attendanceByUserId.clear()
                list.forEach { member ->
                    attendanceByUserId[member.userId] = existingReview?.attendanceByUserId?.get(member.userId) ?: false
                }
                loadingMembers = false
            },
            onError = {
                members = emptyList()
                loadingMembers = false
            }
        )
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) {
                reviewText = spoken
                inputSource = "microphone"
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Event review",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Rate the Event", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            (1..5).forEach { star ->
                                FilledIconButton(
                                    onClick = { rating = star },
                                    modifier = Modifier.size(34.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = if (star <= rating) Color(0xFFFFE082) else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Star $star",
                                        tint = if (star <= rating) Color(0xFFF9A825) else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (rating == 0) "Select" else "$rating/5",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Your review", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your review")
                                    }
                                    try {
                                        speechLauncher.launch(intent)
                                    } catch (_: Exception) {
                                        android.widget.Toast.makeText(context, "Speech recognition not available", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Mic", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Voice")
                            }
                        }

                        OutlinedTextField(
                            value = reviewText,
                            onValueChange = {
                                reviewText = it
                                inputSource = "text"
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 7,
                            placeholder = { Text("Tell us what happened in this Event") }
                        )

                        if (inputSource == "microphone") {
                            AssistChip(onClick = {}, label = { Text("Voice input") })
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Attendance", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            if (loadingMembers) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        }

                        if (!loadingMembers && members.isEmpty()) {
                            Text("No members found for this Event", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        members.forEach { member ->
                            val checked = attendanceByUserId[member.userId] == true
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    attendanceByUserId[member.userId] = !checked
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { value -> attendanceByUserId[member.userId] = value }
                                    )
                                    Text(
                                        text = if (member.userId == viewModel.currentUserId) "You" else member.displayName.ifBlank { member.userId.take(8) },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (member.userId == viewModel.currentUserId) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = reviewText.isNotBlank() && rating in 1..5 && !submitting,
                onClick = {
                    submitting = true
                    onSubmit(reviewText, rating, attendanceByUserId.toMap(), inputSource) { success ->
                        submitting = false
                        if (success) onDismiss()
                    }
                }
            ) {
                if (submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!submitting) onDismiss() }) {
                Text("Cancel")
            }
        }
    )
}


