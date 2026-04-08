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
import com.uniandes.sport.models.Event
import com.uniandes.sport.viewmodels.play.PlayViewModelInterface

import com.uniandes.sport.ui.components.FabMenuItem
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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
    val myTracksByEventId by viewModel.myTracksByEventId.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedSports by viewModel.selectedSports.collectAsState()
    val joinedEventIds by viewModel.joinedEventIds.collectAsState()
    
    val firestoreVM: com.uniandes.sport.viewmodels.retos.FirestoreRetosViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val aiViewModel: com.uniandes.sport.viewmodels.retos.AiReviewViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return com.uniandes.sport.viewmodels.retos.AiReviewViewModel(
                    com.uniandes.sport.ai.OpenAiAnalyzerStrategy(),
                    firestoreVM,
                    viewModel // Pasamos el PlayViewModel actual
                ) as T
            }
        }
    )
    
    var activeModal by remember { mutableStateOf<PlayModalType?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf<String?>(null) }
    var isPullRefreshing by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }
    
    var selectedEventUIModel by remember { mutableStateOf<com.uniandes.sport.patterns.event.EventUIModel?>(null) }
    var trackEvent by remember { mutableStateOf<Event?>(null) }
    var aiTrackTextToAnalyze by remember { mutableStateOf<String?>(null) }
    var aiTrackEventId by remember { mutableStateOf<String?>(null) }
    var aiTrackOldAnalysis by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var editingEvent by remember { mutableStateOf<Event?>(null) }
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
    val joinedFinishedEvents = remember(finishedEvents, joinedEventIds) {
        finishedEvents.filter { joinedEventIds.contains(it.id) }
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

    LaunchedEffect(openEventId) {
        if (!openEventId.isNullOrBlank() && selectedSports.isNotEmpty()) {
            viewModel.clearSportFilters()
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
        viewModel.fetchMyTracksForEvents(ids)
    }

    if (selectedEventUIModel != null) {
        EventDetailModal(
            uiModel = selectedEventUIModel!!,
            viewModel = viewModel,
            onEditClick = { editingEvent = selectedEventUIModel?.rawEvent },
            onReviewClick = { trackEvent = selectedEventUIModel?.rawEvent },
            onDismiss = {
                selectedEventUIModel = null
                viewModel.refreshEvents()
            }
        )
    }

    val trackEventLocal = trackEvent
    if (trackEventLocal != null) {
        val existingTrack = myTracksByEventId[trackEventLocal.id]
        TrackDialog(
            event = trackEventLocal,
            existingTrack = existingTrack,
            viewModel = viewModel,
            onDismiss = { trackEvent = null },
            onSubmit = { text, rating, participated, source, onDone ->
                viewModel.submitTrack(
                    eventId = trackEventLocal.id,
                    text = text,
                    rating = rating,
                    participated = participated,
                    source = source,
                    onSuccess = {
                        android.widget.Toast.makeText(context, "Track saved", android.widget.Toast.LENGTH_SHORT).show()
                        if (participated && text.isNotBlank()) {
                            aiTrackEventId = trackEventLocal.id
                            aiTrackTextToAnalyze = text
                            aiTrackOldAnalysis = existingTrack?.aiAnalysis ?: emptyMap()
                        } else if (!participated && existingTrack?.aiAnalysis?.isNotEmpty() == true) {
                            // Si marcó que NO asistió pero antes tenía progreso, reseteamos el progreso en los retos
                            aiViewModel.resetProgressForEvent(trackEventLocal.id, existingTrack.aiAnalysis)
                        }
                        onDone(true)
                    },
                    onError = { e ->
                        android.widget.Toast.makeText(context, "Could not save track: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        onDone(false)
                    }
                )
            }
        )
    }

    aiTrackTextToAnalyze?.let { text ->
        val eventId = aiTrackEventId ?: ""
        com.uniandes.sport.ui.screens.AiReviewDialog(
            trackText = text,
            eventId = eventId,
            viewModel = aiViewModel,
            oldAnalysis = aiTrackOldAnalysis,
            onDismiss = { 
                aiTrackTextToAnalyze = null
                aiTrackEventId = null
                aiTrackOldAnalysis = emptyMap()
            }
        )
    }

    if (showCreateDialog && selectedMode != null) {
        val creationSport = selectedSports.firstOrNull() ?: "soccer" 
        CreateEventDialog(
            sport = creationSport,
            modality = selectedMode!!,
            onDismiss = { showCreateDialog = false },
            onFinish = { finalSport, title, location, description, date, endDate, skillLevel, maxParticipants, shouldJoin, dialogOnSuccess, dialogOnError ->
                viewModel.createEvent(
                    title = title,
                    description = description,
                    location = location,
                    sport = finalSport,
                    modality = selectedMode!!,
                    scheduledAt = date,
                    finishedAt = endDate,
                    skillLevel = skillLevel,
                    maxParticipants = maxParticipants,
                    shouldJoin = shouldJoin,
                    onSuccess = { 
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
                        android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }

    val editingEventLocal = editingEvent
    if (editingEventLocal != null) {
        CreateEventDialog(
            sport = editingEventLocal.sport,
            modality = editingEventLocal.modality,
            initialEvent = editingEventLocal,
            onDismiss = { editingEvent = null },
            onFinish = { finalSport, title, location, description, date, endDate, skillLevel, maxParticipants, _, dialogOnSuccess, dialogOnError ->
                viewModel.updateEvent(
                    eventId = editingEventLocal.id,
                    title = title,
                    description = description,
                    location = location,
                    sport = finalSport,
                    scheduledAt = date,
                    finishedAt = endDate,
                    skillLevel = skillLevel,
                    maxParticipants = maxParticipants,
                    onSuccess = { 
                        android.widget.Toast.makeText(context, "Event updated!", android.widget.Toast.LENGTH_SHORT).show()
                        dialogOnSuccess()
                        editingEvent = null
                    },
                    onError = { e ->
                        dialogOnError(e)
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
            item {
                Text(
                    text = "EXPLORE BY SPORT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary // Navy Blue (#012567)
                )
                Spacer(modifier = Modifier.height(12.dp))
                SportFilterRow(
                    selectedSports = selectedSports,
                    onSportSelected = { viewModel.toggleSportFilter(it) }
                )
            }

            if (inProgressEvents.isNotEmpty()) {
                item {
                    Text(
                        text = "LIVE NOW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(inProgressEvents, key = { it.id }) { event ->
                            val uiModel = EventUIAdapter.toUIModel(event)
                            CompactEventCard(
                                modifier = Modifier.width(280.dp),
                                uiModel = uiModel,
                                badgeText = "IN PROGRESS",
                                onClick = { onEventSelected(event) }
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "YOUR DASHBOARD",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard(
                        title = "My Schedule",
                        subtitle = if (joinedEvents.isEmpty()) "No upcoming matches" else "${joinedEvents.size} confirmed events",
                        icon = Icons.Default.CalendarToday,
                        badgeCount = if (joinedEvents.isNotEmpty()) joinedEvents.size else null,
                        color = Color(0xFF2F8C89), // Brand Teal
                        onClick = { activeModal = PlayModalType.MY_SCHEDULE }
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionCard(
                            modifier = Modifier.weight(1f),
                            title = "Find Match",
                            subtitle = "${otherEvents.size} open matches",
                            icon = Icons.Default.Search,
                            color = MaterialTheme.colorScheme.secondary,
                            onClick = { activeModal = PlayModalType.SEARCH }
                        )
                        val pendingTracks = joinedFinishedEvents.count { !myTracksByEventId.containsKey(it.id) }
                        ActionCard(
                            modifier = Modifier.weight(1f),
                            title = "History",
                            subtitle = if (pendingTracks > 0) "$pendingTracks pending tracks" else "View past play",
                            icon = Icons.Default.History,
                            badgeCount = if (pendingTracks > 0) pendingTracks else null,
                            color = if (pendingTracks > 0) MaterialTheme.colorScheme.error else Color(0xFF012567),
                            onClick = { activeModal = PlayModalType.HISTORY }
                        )
                    }
                }
            }
        }
        
        // Modals management
        when (activeModal) {
            PlayModalType.MY_SCHEDULE -> {
                CategoryModal(
                    title = "My Schedule",
                    onDismiss = { 
                        activeModal = null 
                        viewModel.clearSportFilters()
                    }
                ) {
                    SportFilterRow(
                        selectedSports = selectedSports,
                        onSportSelected = { viewModel.toggleSportFilter(it) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (joinedEvents.isEmpty()) {
                        EmptyState(Icons.Default.EventBusy, "Nothing here yet", "Join an event to see it in your schedule!")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            joinedEvents.forEach { event ->
                                CompactEventCard(
                                    uiModel = EventUIAdapter.toUIModel(event),
                                    onClick = { 
                                        activeModal = null
                                        onEventSelected(event) 
                                    }
                                )
                            }
                        }
                    }
                }
            }
            PlayModalType.SEARCH -> {
                CategoryModal(
                    title = "Find Matches",
                    onDismiss = { 
                        activeModal = null
                        viewModel.clearSportFilters()
                    }
                ) {
                    SportFilterRow(
                        selectedSports = selectedSports,
                        onSportSelected = { viewModel.toggleSportFilter(it) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (otherEvents.isEmpty()) {
                        EmptyState(Icons.Default.SearchOff, "No matches found", "Try changing the sport filter above.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            otherEvents.forEach { event ->
                                CompactEventCard(
                                    uiModel = EventUIAdapter.toUIModel(event),
                                    onClick = { 
                                        activeModal = null
                                        onEventSelected(event) 
                                    }
                                )
                            }
                        }
                    }
                }
            }
            PlayModalType.HISTORY -> {
                CategoryModal(
                    title = "Match History",
                    onDismiss = { 
                        activeModal = null
                        viewModel.clearSportFilters()
                    }
                ) {
                    SportFilterRow(
                        selectedSports = selectedSports,
                        onSportSelected = { viewModel.toggleSportFilter(it) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (joinedFinishedEvents.isEmpty()) {
                        EmptyState(Icons.Default.History, "No past events", "Completed matches you join will appear here.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            joinedFinishedEvents.forEach { event ->
                                FullWidthEventCard(
                                    uiModel = EventUIAdapter.toUIModel(event),
                                    badgeText = "FINISHED",
                                    reviewLabel = if (myTracksByEventId.containsKey(event.id)) "Edit track" else "Track session",
                                    onReviewClick = { 
                                        activeModal = null
                                        trackEvent = event 
                                    },
                                    onClick = { 
                                        activeModal = null
                                        onEventSelected(event) 
                                    }
                                )
                            }
                        }
                    }
                }
            }
            null -> {}
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
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            com.uniandes.sport.ui.components.SportIconBox(sport = event.sport, size = 48.dp)
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
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
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
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
                style = MaterialTheme.typography.displayMedium, // Now Bebas Neue
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
            com.uniandes.sport.ui.components.SportIconBox(sport = event.sport, size = 48.dp)
            
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
private fun TrackDialog(
    event: com.uniandes.sport.models.Event,
    existingTrack: com.uniandes.sport.models.Track?,
    viewModel: PlayViewModelInterface,
    onDismiss: () -> Unit,
    onSubmit: (text: String, rating: Int, participated: Boolean, source: String, onDone: (Boolean) -> Unit) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var trackText by remember(existingTrack?.text) { mutableStateOf(existingTrack?.text.orEmpty()) }
    var rating by remember(existingTrack?.rating) { mutableIntStateOf(existingTrack?.rating ?: 0) }
    var participated by remember(existingTrack?.participated) { mutableStateOf(existingTrack?.participated ?: true) }
    var submitting by remember { mutableStateOf(false) }
    var inputSource by remember { mutableStateOf(existingTrack?.source ?: "text") }
    
    val scrollState = rememberScrollState()

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) {
                trackText = spoken
                inputSource = "microphone"
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Session Track",
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Participation (The Gatekeeper)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (participated) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = if (participated) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Did you participate?", 
                                style = MaterialTheme.typography.titleSmall, 
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (participated) "I attended this session" else "I missed this session",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = participated,
                            onCheckedChange = { participated = it },
                            thumbContent = if (participated) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                }
                            } else null
                        )
                    }
                }

                if (participated) {
                    // Section 2: Rating
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Performance Rating", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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

                    // Section 3: Activity Log
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
                                Text("Activity Log", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe your activity")
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
                                value = trackText,
                                onValueChange = {
                                    trackText = it
                                    inputSource = "text"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 4,
                                maxLines = 7,
                                placeholder = { Text("What did you accomplish in this session?") }
                            )

                            if (inputSource == "microphone") {
                                AssistChip(onClick = {}, label = { Text("Voice input") })
                            }
                        }
                    }
                } else {
                    // Missed session message
                    Text(
                        "You've marked this session as missed. Your status will be saved, but no progress will be tracked for your challenges.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = (!participated || (trackText.isNotBlank() && rating in 1..5)) && !submitting,
                onClick = {
                    submitting = true
                    onSubmit(trackText, rating, participated, inputSource) { success ->
                        submitting = false
                        if (success) onDismiss()
                    }
                }
            ) {
                if (submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(if (participated) "Save Activity" else "Save Status")
            }
        },
        dismissButton = {
            TextButton(enabled = !submitting, onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

enum class PlayModalType { MY_SCHEDULE, SEARCH, HISTORY }

@Composable
private fun ActionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeCount: Int? = null,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                }
                
                if (badgeCount != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = badgeCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryModal(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Modal Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close")
                    }
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                    )
                }
                
                // Content
                Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            description, 
            style = MaterialTheme.typography.bodyMedium, 
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun CompactEventCard(
    modifier: Modifier = Modifier,
    uiModel: com.uniandes.sport.patterns.event.EventUIModel,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    val event = uiModel.rawEvent
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            com.uniandes.sport.ui.components.SportIconBox(sport = event.sport, size = 42.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    text = "${uiModel.formattedDate} • ${uiModel.participantsFraction}", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (badgeText != null) {
                    Text(
                        text = badgeText, 
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun FullWidthEventCard(
    uiModel: com.uniandes.sport.patterns.event.EventUIModel,
    badgeText: String? = null,
    reviewLabel: String = "Write review",
    onReviewClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val event = uiModel.rawEvent
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.uniandes.sport.ui.components.SportIconBox(sport = event.sport, size = 48.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${uiModel.formattedDate} • ${uiModel.participantsFraction}", 
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (badgeText != null || onReviewClick != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (badgeText != null) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = badgeText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    if (onReviewClick != null) {
                        TextButton(onClick = onReviewClick) {
                            Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(reviewLabel, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun SportFilterRow(
    selectedSports: Set<String>,
    onSportSelected: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        val categories = listOf("soccer", "basketball", "tennis", "calisthenics", "running", "other")
        
        items(categories) { name ->
            val isSelected = selectedSports.contains(name.lowercase())
            FilterChip(
                selected = isSelected,
                onClick = { onSportSelected(name.lowercase()) },
                label = { Text(name.replaceFirstChar { it.uppercase() }, fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium) },
                leadingIcon = { 
                    com.uniandes.sport.ui.components.SportIconBox(
                        sport = name, 
                        size = 24.dp
                    ) 
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = Color.Transparent,
                    selectedBorderColor = MaterialTheme.colorScheme.secondary
                )
            )
        }
    }
}
