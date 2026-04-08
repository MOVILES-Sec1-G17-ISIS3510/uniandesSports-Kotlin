package com.uniandes.sport.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniandes.sport.models.Event
import com.uniandes.sport.models.Reto
import com.uniandes.sport.ui.theme.ArchivoFamily
import com.uniandes.sport.viewmodels.auth.FirebaseAuthViewModel
import com.uniandes.sport.viewmodels.retos.FirestoreRetosViewModel
import com.uniandes.sport.viewmodels.play.FirestorePlayViewModel
import com.uniandes.sport.viewmodels.booking.BookClassViewModel
import com.uniandes.sport.ui.components.getSportAccentColor
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

data class SurpriseContent(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val buttonLabel: String,
    val targetRoute: String
)

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    authViewModel: FirebaseAuthViewModel = viewModel(),
    retosViewModel: FirestoreRetosViewModel = viewModel(),
    playViewModel: FirestorePlayViewModel = viewModel(),
    bookingViewModel: BookClassViewModel = viewModel()
) {
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    var userName by remember { mutableStateOf("User") }
    
    val allEvents by playViewModel.events.collectAsState()
    val finishedEvents by playViewModel.finishedEvents.collectAsState()
    val joinedIds by playViewModel.joinedEventIds.collectAsState()
    val activeRetos by retosViewModel.activeChallenges.collectAsState()
    val allRetos by retosViewModel.retos.collectAsState()
    val userBookings by bookingViewModel.userBookings.collectAsState()
    
    // Surprise State
    var showSurprise by remember { mutableStateOf(false) }
    var surpriseData by remember { mutableStateOf<SurpriseContent?>(null) }

    // Logic: Separate joined vs available events
    val upcomingMatches = remember(allEvents, joinedIds) {
        allEvents.filter { joinedIds.contains(it.id) }
            .sortedBy { it.scheduledAt?.seconds ?: Long.MAX_VALUE }
    }
    
    val availableEvents = remember(allEvents, joinedIds) {
        allEvents.filter { !joinedIds.contains(it.id) }
            .sortedBy { it.scheduledAt?.seconds ?: Long.MAX_VALUE }
    }

    // Sessions count
    val sessionsCount = remember(upcomingMatches) {
        val now = Calendar.getInstance()
        val startOfMonth = now.apply { 
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
        }.timeInMillis
        
        upcomingMatches.count { match ->
            (match.scheduledAt?.toDate()?.time ?: 0L) >= startOfMonth
        }
    }

    // Holistic Streak Calculation
    val streakDays = remember(finishedEvents, upcomingMatches, joinedIds, userBookings, activeRetos) {
        val activityDates = mutableSetOf<String>()
        fun addDate(date: Date?) {
            date?.let {
                val c = Calendar.getInstance()
                c.time = it
                activityDates.add("${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}")
            }
        }
        (finishedEvents.filter { joinedIds.contains(it.id) } + upcomingMatches).forEach { addDate(it.scheduledAt?.toDate()) }
        userBookings.forEach { addDate(it.createdAt.toDate()) }
        activeRetos.forEach { addDate(it.startDate?.toDate()) }
        if (activityDates.isEmpty()) return@remember 0
        var streak = 0
        val today = Calendar.getInstance()
        var checkDate = today
        while (true) {
            val key = "${checkDate.get(Calendar.YEAR)}-${checkDate.get(Calendar.DAY_OF_YEAR)}"
            if (activityDates.contains(key)) {
                streak++
                checkDate.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                if (streak == 0) {
                    checkDate.add(Calendar.DAY_OF_YEAR, -1)
                    val yesterdayKey = "${checkDate.get(Calendar.YEAR)}-${checkDate.get(Calendar.DAY_OF_YEAR)}"
                    if (activityDates.contains(yesterdayKey)) {
                        streak++
                        checkDate.add(Calendar.DAY_OF_YEAR, -1)
                        continue
                    }
                }
                break
            }
        }
        streak
    }

    LaunchedEffect(Unit) {
        authViewModel.getUser(
            onSuccess = { user -> userName = user.fullName.split(" ").firstOrNull() ?: "User" },
            onFailure = { /* Fail silent */ }
        )
        retosViewModel.fetchRetos()
        playViewModel.fetchEvents()
        bookingViewModel.fetchUserBookings(currentUserId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 100.dp, start = 20.dp, end = 20.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header etc. same as before
            item {
                Column {
                    Text(
                        text = "WELCOME, ${userName.uppercase()}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = ArchivoFamily,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Text(
                        text = "YOUR SPORTS DASHBOARD",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Stats
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard(label = "Streak", value = "$streakDays Days", icon = Icons.Default.Whatshot, iconColor = Color(0xFFE67E22), modifier = Modifier.weight(1f))
                    StatCard(label = "Activity", value = "$sessionsCount ${if (sessionsCount == 1) "Session" else "Sessions"}", icon = Icons.Default.TrendingUp, iconColor = Color(0xFF2ECC71), modifier = Modifier.weight(1f))
                }
            }

            // Actions
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeActionChip(Icons.Default.Cloud, "24°") { /* Weather logic */ }
                        HomeActionChip(Icons.Default.DirectionsRun, "Strava") { onNavigate("strava") }
                        HomeActionChip(Icons.Default.History, "History") { onNavigate("history") }
                    }
                }
            }

            // Quick Activity
            item {
                SectionHeader(title = "Quick Activity", subtitle = "Suggested sessions you might like")
                if (availableEvents.isEmpty()) {
                    EmptyStateCard(title = "No events nearby", description = "Why not organize one?", icon = Icons.Default.EventBusy, actionLabel = "Create Activity", onAction = { onNavigate("play") })
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        availableEvents.take(2).forEach { event -> ActivityCard(event = event, onClick = { /* Detail */ }) }
                    }
                }
            }

            // Active Challenges
            item {
                SectionHeader(title = "Active Challenges", onViewAll = { onNavigate("challenges") })
                if (activeRetos.isEmpty()) {
                    EmptyStateWideCard(title = "No active challenges", description = "Start your journey today and compete with others.", icon = Icons.Default.Flag, actionLabel = "Browse Challenges", onClick = { onNavigate("challenges") })
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        activeRetos.take(2).forEach { reto ->
                            val userProgress = (reto.progressByUser[currentUserId] ?: 0.0).toFloat()
                            HomeChallengeCard(title = reto.title, daysRemaining = calculateDaysRemaining(reto.endDate), progress = userProgress, participants = reto.participantsCount.toInt())
                        }
                    }
                }
            }

            // Recommended
            item {
                SectionHeader(title = "Recommended for You")
                if (availableEvents.size <= 2) {
                     EmptyStateCard(title = "Searching for you", description = "We'll show you more sports soon.", icon = Icons.Default.AutoAwesome)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(availableEvents.drop(2).take(4)) { event -> RecommendedItemCard(event = event) }
                    }
                }
            }

            // Upcoming
            item {
                SectionHeader(title = "Upcoming Matches")
                if (upcomingMatches.isEmpty()) {
                    EmptyStateWideCard(title = "Your field is empty", description = "Join a match or schedule one with your friends.", icon = Icons.Default.SportsSoccer, actionLabel = "Schedule Match", onClick = { onNavigate("play") })
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        upcomingMatches.forEach { match -> UpcomingMatchItem(event = match) }
                    }
                }
            }
        }

        // Multi-Action FAB
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)) {
            MultiActionFAB(onActionClick = { action ->
                when(action) {
                    "schedule" -> onNavigate("play")
                    "connect" -> onNavigate("strava")
                    "surprise" -> {
                        // SURPRISE ME LOGIC
                        val randomMatch = availableEvents.filter { it.membersCount < it.maxParticipants }.randomOrNull()
                        val randomChallenge = allRetos.filter { reto -> !activeRetos.any { it.id == reto.id } }.randomOrNull()
                        
                        val tips = listOf(
                             "Pro tip: Stretching for 5 mins after a session reduces recovery time by 30%!",
                             "Fun fact: Playing sports with friends releases 2x more endorphins than solo training.",
                             "Athlete mode: Your streak is looking strong! Keep going to earn new badges.",
                             "Community spirit: There are over 10 active communities waiting for you in the Social tab!"
                        )

                        surpriseData = when {
                            randomMatch != null -> SurpriseContent(
                                title = "Spontaneous Match?",
                                description = "There's a ${randomMatch.sport} game at ${randomMatch.location} that needs someone like you!",
                                icon = Icons.Default.SportsSoccer,
                                buttonLabel = "LET'S PLAY",
                                targetRoute = "play"
                            )
                            randomChallenge != null -> SurpriseContent(
                                title = "A New Journey!",
                                description = "You haven't tried the '${randomChallenge.title}' challenge yet. Ready to level up?",
                                icon = Icons.Default.AutoAwesome,
                                buttonLabel = "VIEW CHALLENGE",
                                targetRoute = "challenges"
                            )
                            else -> SurpriseContent(
                                title = "Today's Tip",
                                description = tips.random(),
                                icon = Icons.Default.Lightbulb,
                                buttonLabel = "GOT IT!",
                                targetRoute = "home"
                            )
                        }
                        showSurprise = true
                    }
                }
            })
        }
    }

    if (showSurprise && surpriseData != null) {
        SurpriseDialog(
            title = surpriseData!!.title,
            description = surpriseData!!.description,
            icon = surpriseData!!.icon,
            buttonLabel = surpriseData!!.buttonLabel,
            onConfirm = {
                showSurprise = false
                if (surpriseData!!.targetRoute != "home") {
                    onNavigate(surpriseData!!.targetRoute)
                }
            },
            onDismiss = { showSurprise = false }
        )
    }
}

@Composable
fun ActivityCard(event: Event, onClick: () -> Unit) {
    val sportColor = getSportAccentColor(event.sport)
    val timeStr = remember(event.scheduledAt) {
        val date = event.scheduledAt?.toDate() ?: Date()
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    }
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(sportColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(imageVector = when(event.sport.lowercase()) {
                    "running", "correr" -> Icons.Default.DirectionsRun
                    "soccer", "fútbol", "futbol" -> Icons.Default.SportsSoccer
                    "tennis", "tenis" -> Icons.Default.SportsTennis
                    else -> Icons.Default.FitnessCenter
                }, null, tint = sportColor, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(event.title, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" $timeStr", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" ${event.location}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun RecommendedItemCard(event: Event) {
    val sportColor = getSportAccentColor(event.sport)
    val dateStr = remember(event.scheduledAt) {
        val date = event.scheduledAt?.toDate() ?: Date()
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(date)
    }
    Card(modifier = Modifier.width(200.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(color = sportColor.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                Text(event.sport.uppercase(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = sportColor)
            }
            Spacer(Modifier.height(12.dp))
            Text(event.title, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1)
            Text(event.location, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" $dateStr", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${event.maxParticipants - event.membersCount} spots", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
            }
        }
    }
}

@Composable
fun UpcomingMatchItem(event: Event) {
    val dateStr = remember(event.scheduledAt) {
        val date = event.scheduledAt?.toDate() ?: Date()
        SimpleDateFormat("EEE, hh:mm a", Locale.getDefault()).format(date)
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE7F5F5)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CalendarToday, null, tint = Color(0xFF43817A))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(event.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(dateStr, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(event.location, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
        }
    }
}

private fun calculateDaysRemaining(endDate: com.google.firebase.Timestamp?): Int {
    if (endDate == null) return 0
    val diff = endDate.seconds - (System.currentTimeMillis() / 1000)
    return (diff / (24 * 3600)).toInt().coerceAtLeast(0)
}
