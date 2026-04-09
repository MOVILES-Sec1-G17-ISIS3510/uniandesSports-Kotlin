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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniandes.sport.models.Event
import com.uniandes.sport.models.Reto
import com.uniandes.sport.models.RunSession
import com.uniandes.sport.patterns.event.EventUIAdapter
import com.uniandes.sport.patterns.event.OpenMatchRanker
import com.uniandes.sport.ui.theme.ArchivoFamily
import com.uniandes.sport.ui.components.SmartMatchCard
import com.uniandes.sport.ui.components.rememberPhoneCalendarEventsState
import com.uniandes.sport.ui.components.rememberCurrentLocationState
import com.uniandes.sport.viewmodels.auth.FirebaseAuthViewModel
import com.uniandes.sport.viewmodels.retos.FirestoreRetosViewModel
import com.uniandes.sport.viewmodels.play.FirestorePlayViewModel
import com.uniandes.sport.viewmodels.booking.BookClassViewModel
import com.uniandes.sport.viewmodels.running.FirestoreRunningViewModel
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    authViewModel: FirebaseAuthViewModel = viewModel(),
    retosViewModel: FirestoreRetosViewModel = viewModel(),
    playViewModel: FirestorePlayViewModel = viewModel(),
    bookingViewModel: BookClassViewModel = viewModel(),
    stepViewModel: com.uniandes.sport.viewmodels.sensors.StepCounterViewModel = viewModel(),
    runningViewModel: FirestoreRunningViewModel = viewModel(),
    weatherViewModel: com.uniandes.sport.viewmodels.weather.WeatherViewModel = viewModel(),
    logViewModel: com.uniandes.sport.viewmodels.log.LogViewModelInterface = viewModel<com.uniandes.sport.viewmodels.log.FirebaseLogViewModel>()
) {
    var currentUserId by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.uid ?: "") }
    
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUserId = auth.currentUser?.uid ?: ""
        }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
        onDispose {
            FirebaseAuth.getInstance().removeAuthStateListener(listener)
        }
    }
    var userName by remember { mutableStateOf("User") }
    
    val allEvents by playViewModel.events.collectAsState()
    val finishedEvents by playViewModel.finishedEvents.collectAsState()
    val joinedIds by playViewModel.joinedEventIds.collectAsState()
    val activeRetos by retosViewModel.activeChallenges.collectAsState()
    val allRetos by retosViewModel.retos.collectAsState()
    val userBookings by bookingViewModel.userBookings.collectAsState()
    val pastRuns by runningViewModel.pastRuns.collectAsState()
    
    val lastRun = pastRuns.firstOrNull()
    val lastCoachFeedback = lastRun?.aiFeedback ?: "Start your first run to get personalized tips from your AI Coach!"
    
    // ViewModels Loading States
    val playLoading by playViewModel.isLoading.collectAsState()
    val retosLoading by retosViewModel.isLoading.collectAsState()
    val masterLoading = playLoading || retosLoading

    // Surprise State
    var showSurprise by remember { mutableStateOf(false) }
    var surpriseData by remember { mutableStateOf<SurpriseContent?>(null) }

    // Step Counter State
    val currentSteps by stepViewModel.currentSteps.collectAsState()
    val dailyGoal = stepViewModel.dailyGoal
    val context = androidx.compose.ui.platform.LocalContext.current

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                stepViewModel.startTracking()
            }
        }
    )

    // Pull Refresh State
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            // Accurate refresh actions matching system standards
            playViewModel.refreshEvents()
            retosViewModel.fetchRetos()
            bookingViewModel.fetchUserBookings(currentUserId)
            runningViewModel.fetchPastRuns()
        }
    )

    // Sync isRefreshing with real ViewModel states (Full Accuracy)
    LaunchedEffect(masterLoading) {
        if (!masterLoading && isRefreshing) {
            isRefreshing = false
        }
    }

    // Logic: Separate joined vs available events
    val upcomingMatches = remember(allEvents, joinedIds) {
        allEvents.filter { joinedIds.contains(it.id) }
            .sortedBy { it.scheduledAt?.seconds ?: Long.MAX_VALUE }
    }
    
    val availableEvents = remember(allEvents, joinedIds) {
        allEvents.filter { !joinedIds.contains(it.id) }
            .sortedBy { it.scheduledAt?.seconds ?: Long.MAX_VALUE }
    }
    val joinedFinishedEvents = remember(finishedEvents, joinedIds) {
        finishedEvents.filter { joinedIds.contains(it.id) }
    }
    val historyEvents = remember(upcomingMatches, joinedFinishedEvents) {
        (upcomingMatches + joinedFinishedEvents).distinctBy { it.id }
    }
    val currentLocation by rememberCurrentLocationState()
    val phoneCalendarEvents by rememberPhoneCalendarEventsState()
    val preferredSports = remember(authViewModel.mainSport) {
        OpenMatchRanker.parsePreferredSports(authViewModel.mainSport)
    }
    val rankedAvailableEvents = remember(availableEvents, upcomingMatches, historyEvents, preferredSports, currentLocation, phoneCalendarEvents) {
        OpenMatchRanker.rank(
            openEvents = availableEvents,
            joinedEvents = upcomingMatches,
            historyEvents = historyEvents,
            preferredSports = preferredSports,
            phoneCalendarEvents = phoneCalendarEvents,
            currentLocation = currentLocation
        )
    }
    val featuredMatch = rankedAvailableEvents.firstOrNull()

    // Sessions count
    val sessionsCount = remember(upcomingMatches, pastRuns) {
        val now = Calendar.getInstance()
        val startOfMonth = now.apply { 
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis
        
        val matchSessions = upcomingMatches.count { match ->
            (match.scheduledAt?.toDate()?.time ?: 0L) >= startOfMonth
        }
        val runSessions = pastRuns.count { run ->
            run.timestamp >= startOfMonth
        }
        matchSessions + runSessions
    }

    // Holistic Streak Calculation
    val streakDays = remember(finishedEvents, upcomingMatches, joinedIds, userBookings, activeRetos, pastRuns) {
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
        pastRuns.forEach { addDate(Date(it.timestamp)) }
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

    LaunchedEffect(currentUserId) {
        if (currentUserId.isBlank()) return@LaunchedEffect
        
        authViewModel.getUser(
            onSuccess = { user -> userName = user.fullName.split(" ").firstOrNull() ?: "User" },
            onFailure = { /* Fail silent */ }
        )
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val status = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION)
            if (status == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                stepViewModel.startTracking()
            } else {
                permissionLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
            }
        } else {
            stepViewModel.startTracking()
        }
        
        runningViewModel.fetchPastRuns()
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isSmallScreen = screenWidth < 410
    
    val horizontalPadding = if (isSmallScreen) 16.dp else 20.dp
    val headerFontSize = if (isSmallScreen) 20.sp else 22.sp
    val sectionSpacing = if (isSmallScreen) 20.dp else 24.dp

    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 100.dp, start = horizontalPadding, end = horizontalPadding, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing)
        ) {
            item {
                Column {
                    Text(
                        text = "${getDynamicGreeting()}, ${userName.uppercase()}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = ArchivoFamily,
                            fontSize = headerFontSize,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            // Coach Insight Widget
            item {
                CoachInsightCard(feedback = lastCoachFeedback)
            }

            // Stats - Adaptive Grid
            item {
                val streakColor = if (MaterialTheme.colorScheme.background.toArgb() == Color(0xFF020617).toArgb()) Color(0xFFFB923C) else Color(0xFFE67E22)
                val activityColor = if (MaterialTheme.colorScheme.background.toArgb() == Color(0xFF020617).toArgb()) Color(0xFF4ADE80) else Color(0xFF2ECC71)

                if (isSmallScreen) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatCard(label = "Streak", value = "$streakDays Days", icon = Icons.Default.Whatshot, iconColor = streakColor, modifier = Modifier.fillMaxWidth())
                        StatCard(label = "Activity", value = "$sessionsCount ${if (sessionsCount == 1) "Session" else "Sessions"}", icon = Icons.Default.TrendingUp, iconColor = activityColor, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatCard(label = "Streak", value = "$streakDays Days", icon = Icons.Default.Whatshot, iconColor = streakColor, modifier = Modifier.weight(1f))
                        StatCard(label = "Activity", value = "$sessionsCount ${if (sessionsCount == 1) "Session" else "Sessions"}", icon = Icons.Default.TrendingUp, iconColor = activityColor, modifier = Modifier.weight(1f))
                    }
                }
            }

            // Actions - Centered & Responsive
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(horizontal = if (isSmallScreen) 4.dp else 0.dp)
                ) {
                    item { 
                        val weatherState by weatherViewModel.weatherState.collectAsState()
                        val temp = when (val s = weatherState) {
                            is com.uniandes.sport.viewmodels.weather.WeatherState.Success -> "${s.data.currentWeather.temperature.toInt()}°"
                            else -> "--°"
                        }
                        HomeActionChip(Icons.Default.Cloud, temp) { onNavigate("weather") } 
                    }
                    item { HomeActionChip(Icons.Default.DirectionsRun, "Strava") { onNavigate("strava") } }
                    item { HomeActionChip(Icons.Default.History, "History") { onNavigate("history") } }
                }
            }

            // Daily Step Challenge

            // Daily Step Challenge
            item {
                DailyStepChallenge(steps = currentSteps, goal = dailyGoal)
            }

            // Start Live Run Button
            item {
                Button(
                    onClick = { 
                        logViewModel.log(
                            screen = "HomeScreen",
                            action = "MATCH_VIEWED",
                            params = mapOf("sport_category" to "running")
                        )
                        onNavigate("live_run") 
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DirectionsRun, contentDescription = null, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("START RUN", fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            // Sections
            item {
                SectionHeader(title = "Quick Activity", subtitle = "Suggested sessions you might like")
                if (featuredMatch != null) {
                    SmartMatchCard(
                        recommendation = featuredMatch!!,
                        onClick = { onNavigate("play") }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (rankedAvailableEvents.isEmpty()) {
                    EmptyStateWideCard(title = "No events nearby", description = "Try searching in the Play tab.", icon = Icons.Default.Search, actionLabel = "Explore Play", onClick = { onNavigate("play") })
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        rankedAvailableEvents.take(2).forEach { rankedEvent -> 
                            ActivityCard(
                                event = rankedEvent.event, 
                                onClick = { 
                                    logViewModel.log(
                                        screen = "HomeScreen",
                                        action = "MATCH_VIEWED",
                                        params = mapOf("sport_category" to rankedEvent.event.sport)
                                    )
                                    // Detail navigation logic normally goes here
                                }
                            ) 
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "Active Challenges", onViewAll = { onNavigate("challenges") })
                if (activeRetos.isEmpty()) {
                    EmptyStateWideCard(title = "No active challenges", description = "Join a challenge and start earning points.", icon = Icons.Default.EmojiEvents, actionLabel = "Browse Challenges", onClick = { onNavigate("challenges") })
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        activeRetos.take(2).forEach { reto ->
                            val userProgress = (reto.progressByUser[currentUserId] ?: 0.0).toFloat()
                            HomeChallengeCard(title = reto.title, daysRemaining = calculateDaysRemaining(reto.endDate), progress = userProgress, participants = reto.participantsCount.toInt())
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "Recent Activity", onViewAll = { onNavigate("history") })
                lastRun?.let { run ->
                    RecentRunWidget(run = run, onClick = { onNavigate("history") })
                } ?: run {
                    EmptyStateWideCard(
                        title = "No recent runs",
                        description = "Start your running journey today!",
                        icon = Icons.Default.DirectionsRun,
                        actionLabel = "Start Run",
                        onClick = { onNavigate("live_run") }
                    )
                }
            }

            item {
                SectionHeader(title = "Recommended for You")
                if (rankedAvailableEvents.size <= 2) {
                     EmptyStateCard(title = "Looking for matches", description = "We'll show you more sports soon.", icon = Icons.Default.AutoAwesome)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(rankedAvailableEvents.drop(2).take(4)) { rankedEvent -> 
                            RecommendedItemCard(
                                event = rankedEvent.event,
                                onClick = {
                                    logViewModel.log(
                                        screen = "HomeScreen",
                                        action = "MATCH_VIEWED",
                                        params = mapOf("sport_category" to rankedEvent.event.sport)
                                    )
                                }
                            ) 
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "Upcoming Matches")
                if (upcomingMatches.isEmpty()) {
                    EmptyStateWideCard(title = "Your field is empty", description = "Join a match or schedule one with your friends.", icon = Icons.Default.SportsSoccer, actionLabel = "Find Match", onClick = { onNavigate("play") })
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        upcomingMatches.forEach { match -> 
                            UpcomingMatchItem(
                                event = match,
                                onClick = {
                                    logViewModel.log(
                                        screen = "HomeScreen",
                                        action = "MATCH_VIEWED",
                                        params = mapOf("sport_category" to match.sport)
                                    )
                                }
                            ) 
                        }
                    }
                }
            }
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            contentColor = MaterialTheme.colorScheme.primary,
            backgroundColor = MaterialTheme.colorScheme.surface
        )

        // Multi-Action FAB
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            MultiActionFAB(onActionClick = { action ->
                when(action) {
                    "schedule" -> onNavigate("play")
                    "connect" -> onNavigate("strava")
                    "surprise" -> {
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
    val timeStr = remember(event.scheduledAt, event.finishedAt) {
        EventUIAdapter.formatSchedule(event)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(sportColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
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
                    Text(event.title, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" $timeStr", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" ${event.location}", fontSize = 13.sp, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
fun RecommendedItemCard(event: Event, onClick: () -> Unit = {}) {
    val sportColor = getSportAccentColor(event.sport)
    val dateStr = remember(event.scheduledAt, event.finishedAt) {
        EventUIAdapter.formatSchedule(event)
    }
    Surface(
        modifier = Modifier.width(200.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(color = sportColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                Text(event.sport.uppercase(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = sportColor)
            }
            Spacer(Modifier.height(12.dp))
            Text(event.title, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
            Text(event.location, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" $dateStr", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${event.maxParticipants - event.membersCount} spots", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (MaterialTheme.colorScheme.background.toArgb() == Color(0xFF020617).toArgb()) Color(0xFF4ADE80) else Color(0xFF10B981))
            }
        }
    }
}

@Composable
fun UpcomingMatchItem(event: Event, onClick: () -> Unit = {}) {
    val dateStr = remember(event.scheduledAt, event.finishedAt) {
        EventUIAdapter.formatSchedule(event)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha=0.5f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CalendarToday, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(event.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(dateStr, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(event.location, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private fun calculateDaysRemaining(endDate: com.google.firebase.Timestamp?): Int {
    if (endDate == null) return 0
    val diff = endDate.seconds - (System.currentTimeMillis() / 1000)
    return (diff / (24 * 3600)).toInt().coerceAtLeast(0)
}

private fun androidx.compose.ui.graphics.Color.toArgb(): Int {
    return (alpha * 255.0f + 0.5f).toInt() shl 24 or
           (red * 255.0f + 0.5f).toInt() shl 16 or
           (green * 255.0f + 0.5f).toInt() shl 8 or
           (blue * 255.0f + 0.5f).toInt()
}
