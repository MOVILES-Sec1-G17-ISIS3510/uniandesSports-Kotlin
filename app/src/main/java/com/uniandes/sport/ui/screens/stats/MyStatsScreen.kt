package com.uniandes.sport.ui.screens.stats

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.SportsVolleyball
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.data.entities.BadgeEntity
import com.uniandes.sport.models.ChallengeStats
import com.uniandes.sport.models.EventSummary
import com.uniandes.sport.models.RunDataPoint
import com.uniandes.sport.models.SportBreakdownItem
import com.uniandes.sport.ui.screens.stats.components.SyncStatusBar
import com.uniandes.sport.viewmodels.stats.MyStatsViewModelInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Badge catalog ────────────────────────────────────────────────────────────
// All achievable badges. The grid always shows all 12; locked ones are grayed.

private data class BadgeDef(
    val idPrefix: String,   // e.g. "event_first" — appended with "_$userId" to match Room
    val name: String,
    val requirement: String,
    val icon: ImageVector,
    val rarity: String      // COMMON | RARE | EPIC | LEGENDARY
)

private val BADGE_CATALOG = listOf(
    // Events
    BadgeDef("event_first",    "First Match",     "Join 1 event",       Icons.Default.Groups,       "COMMON"),
    BadgeDef("event_team",     "Team Player",     "Join 5 events",      Icons.Default.Groups,       "RARE"),
    BadgeDef("event_veteran",  "Sports Veteran",  "Join 20 events",     Icons.Default.Groups,       "EPIC"),
    // Posts
    BadgeDef("post_first",     "First Post",      "Make 1 post",        Icons.Default.PostAdd,      "COMMON"),
    BadgeDef("post_voice",     "Community Voice", "Make 10 posts",      Icons.Default.PostAdd,      "RARE"),
    BadgeDef("post_influencer","Influencer",      "Make 50 posts",      Icons.Default.PostAdd,      "EPIC"),
    // Running
    BadgeDef("run_first",      "First Run",       "Run 1 km",           Icons.Default.DirectionsRun,"COMMON"),
    BadgeDef("run_10k",        "10K Club",        "Run 10 km total",    Icons.Default.DirectionsRun,"RARE"),
    BadgeDef("run_marathon",   "Marathon Hero",   "Run 42 km total",    Icons.Default.DirectionsRun,"EPIC"),
    // Level
    BadgeDef("level_rising",   "Rising Star",     "Reach Level 3",      Icons.Default.Star,         "RARE"),
    BadgeDef("level_champion", "Champion",        "Reach Level 5",      Icons.Default.EmojiEvents,  "EPIC"),
    BadgeDef("level_legend",   "Legend",          "Reach Level 10",     Icons.Default.EmojiEvents,  "LEGENDARY")
)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyStatsScreen(
    viewModel: MyStatsViewModelInterface,
    onNavigate: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val stats           by viewModel.stats.collectAsState()
    val badges          by viewModel.badges.collectAsState()
    val syncStatus      by viewModel.syncStatus.collectAsState()
    val isLoading       by viewModel.isLoading.collectAsState()
    val recentRuns      by viewModel.recentRuns.collectAsState()
    val challengeStats  by viewModel.challengeStats.collectAsState()
    val joinedEvents    by viewModel.joinedEvents.collectAsState()
    val sportBreakdown  by viewModel.sportBreakdown.collectAsState()

    Log.d("📊 MYSTATS:", "🖼️ Render: events=${stats.totalEvents} posts=${stats.totalPosts} km=${stats.totalKm} badges=${badges.size}")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Activity", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ── Sync status ───────────────────────────────────────────────────
            item {
                SyncStatusBar(
                    isOnline     = true,
                    syncStatus   = syncStatus,
                    lastSyncTime = if (stats.lastSyncAt > 0) stats.lastSyncAt else null,
                    onSync       = { viewModel.refreshStats(forceSync = true) },
                    isLoading    = isLoading
                )
            }

            // ── Level card ────────────────────────────────────────────────────
            item { LevelCard(level = stats.level, points = stats.points, streakDays = stats.streakDays) }

            // ── Activity summary ──────────────────────────────────────────────
            item { SectionTitle(icon = TrendingUp, title = "Activity Summary") }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(Modifier.weight(1f), Icons.Default.Groups,       MaterialTheme.colorScheme.primary, "Events", "${stats.totalEvents}")
                    StatCard(Modifier.weight(1f), Icons.Default.PostAdd,      Color(0xFF4CAF50),                 "Posts",  "${stats.totalPosts}")
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(Modifier.weight(1f), Icons.Default.DirectionsRun, Color(0xFFFF9800),                "Km Run", String.format("%.1f", stats.totalKm))
                    StatCard(Modifier.weight(1f), Icons.Default.EmojiEvents,   Color(0xFFFFC107),                "Points", "${stats.points}")
                }
            }

            item { LevelProgressCard(level = stats.level, points = stats.points) }

            // ── Running history ───────────────────────────────────────────────
            if (recentRuns.isNotEmpty()) {
                item { SectionTitle(icon = Icons.Default.DirectionsRun, title = "Running History") }
                item { RunningHistoryCard(runs = recentRuns) }
            }

            // ── My events ─────────────────────────────────────────────────────
            if (joinedEvents.isNotEmpty()) {
                item { SectionTitle(icon = Icons.Default.Groups, title = "My Events", subtitle = "${joinedEvents.size} joined") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(joinedEvents) { event -> EventCard(event = event) }
                    }
                }
            }

            // ── Challenges ────────────────────────────────────────────────────
            if (challengeStats.total > 0) {
                item { SectionTitle(icon = Icons.Default.EmojiEvents, title = "Challenges") }
                item { ChallengeStatsCard(stats = challengeStats) }
            }

            // ── Sport breakdown ───────────────────────────────────────────────
            if (sportBreakdown.isNotEmpty()) {
                item { SectionTitle(icon = Icons.Default.Sports, title = "Sports Breakdown") }
                item { SportBreakdownCard(breakdown = sportBreakdown) }
            }

            // ── Badges ────────────────────────────────────────────────────────
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD600), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Badges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        text  = "${badges.size}/${BADGE_CATALOG.size} unlocked",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { BadgeGrid(earnedBadges = badges, userId = stats.userId) }

            // ── New-user tip ──────────────────────────────────────────────────
            if (!stats.hasRealData && !isLoading && syncStatus != "SYNCING") {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Text(
                            text     = "Join events in Play, post in Communities, and run to start earning stats and badges.",
                            modifier = Modifier.padding(16.dp),
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ─── Private composables ──────────────────────────────────────────────────────

// ── Section title with icon ────────────────────────────────────────────────

private val TrendingUp = Icons.Default.TrendingUp

@Composable
private fun SectionTitle(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier         = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Level card ─────────────────────────────────────────────────────────────

@Composable
private fun LevelCard(level: Int, points: Int, streakDays: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("$level", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Level $level", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("$points points", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
            if (streakDays > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LocalFireDepartment, "Streak", tint = Color(0xFFFF6D00), modifier = Modifier.size(24.dp))
                    Text("$streakDays", fontWeight = FontWeight.Bold, color = Color(0xFFFF6D00), fontSize = 16.sp)
                    Text("days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                }
            }
        }
    }
}

// ── Stat card ──────────────────────────────────────────────────────────────

@Composable
private fun StatCard(modifier: Modifier = Modifier, icon: ImageVector, iconTint: Color, label: String, value: String) {
    Card(
        modifier  = modifier,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, label, tint = iconTint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 24.sp)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Level progress ─────────────────────────────────────────────────────────

@Composable
private fun LevelProgressCard(level: Int, points: Int) {
    val pointsInLevel = points % 100
    val progress      = pointsInLevel / 100f
    val toNext        = 100 - pointsInLevel

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Level $level", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("$toNext pts to Level ${level + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress   = { progress },
                modifier   = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color      = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

// ── Running history card + bar chart ──────────────────────────────────────

@Composable
private fun RunningHistoryCard(runs: List<RunDataPoint>) {
    val totalKm = runs.sumOf { it.distanceKm.toDouble() }.toFloat()
    val bestRun = runs.maxByOrNull { it.distanceKm }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("${runs.size} sessions", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(String.format("%.1f km shown", totalKm), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (bestRun != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Best run", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(String.format("%.1f km", bestRun.distanceKm), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                        if (bestRun.pace.isNotBlank()) Text("${bestRun.pace} /km", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            RunBarChart(runs = runs)
        }
    }
}

@Composable
private fun RunBarChart(runs: List<RunDataPoint>, chartHeightDp: Int = 100) {
    if (runs.isEmpty()) return

    // Clamp max to 0.96 so the top spacer always has weight > 0 (weight=0 crashes Compose)
    val maxKm      = runs.maxOfOrNull { it.distanceKm }.takeIf { (it ?: 0f) > 0f } ?: 1f
    val barColor   = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier              = Modifier.fillMaxWidth().height((chartHeightDp + 32).dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        runs.forEach { run ->
            val fraction   = (run.distanceKm / maxKm).coerceIn(0.04f, 0.96f)
            val spacerFrac = (1f - fraction).coerceAtLeast(0.04f)

            Column(
                modifier            = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(spacerFrac))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(fraction)
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(barColor)
                )
                Spacer(Modifier.height(4.dp))
                Text(String.format("%.1f", run.distanceKm), fontSize = 8.sp, color = labelColor, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.fillMaxWidth())
                Text(formatShortDate(run.timestamp), fontSize = 7.sp, color = labelColor.copy(alpha = 0.6f), textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ── Event cards ────────────────────────────────────────────────────────────

@Composable
private fun EventCard(event: EventSummary) {
    val sportIv     = sportIcon(event.sport)
    val statusColor = when (event.status.lowercase(Locale.getDefault())) {
        "active", "open", "abierto"              -> Color(0xFF4CAF50)
        "completed", "cerrado", "closed", "done" -> MaterialTheme.colorScheme.onSurfaceVariant
        else                                     -> Color(0xFFFF9800)
    }

    Card(
        modifier  = Modifier.width(150.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier            = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Sport icon in colored circle
            Box(
                modifier         = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(sportIv, contentDescription = event.sport, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Text(
                text      = event.title.ifBlank { "Event" },
                style     = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis,
                modifier  = Modifier.fillMaxWidth()
            )
            Text(
                text  = event.sport.ifBlank { "Sport" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Status chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text  = event.status.replaceFirstChar { it.uppercaseChar() }.ifBlank { "—" },
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (event.scheduledAt != null) {
                Text(formatShortDate(event.scheduledAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Challenge stats card ───────────────────────────────────────────────────

@Composable
private fun ChallengeStatsCard(stats: ChallengeStats) {
    val completedFrac = if (stats.total > 0) stats.completed.toFloat() / stats.total else 0f
    val activeFrac    = if (stats.total > 0) stats.inProgress.toFloat() / stats.total else 0f

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Three stat numbers
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ChallengeStat(value = "${stats.total}",     label = "Joined",    color = MaterialTheme.colorScheme.primary)
                ChallengeStat(value = "${stats.completed}", label = "Completed", color = Color(0xFF4CAF50))
                ChallengeStat(value = "${stats.inProgress}",label = "Active",    color = Color(0xFFFF9800))
            }

            // Segmented progress bar
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Completion rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${(completedFrac * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                }
                Spacer(Modifier.height(4.dp))
                // Segmented bar: completed (green) | active (orange) | remainder (surface)
                Row(
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                ) {
                    if (completedFrac > 0f) Box(Modifier.weight(completedFrac).fillMaxHeight().background(Color(0xFF4CAF50)))
                    if (activeFrac    > 0f) Box(Modifier.weight(activeFrac).fillMaxHeight().background(Color(0xFFFF9800)))
                    val remaining = (1f - completedFrac - activeFrac).coerceAtLeast(0.01f)
                    Box(Modifier.weight(remaining).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant))
                }
            }
        }
    }
}

@Composable
private fun ChallengeStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Black, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Sport breakdown card ───────────────────────────────────────────────────

@Composable
private fun SportBreakdownCard(breakdown: List<SportBreakdownItem>) {
    val maxCount = breakdown.maxOfOrNull { it.count }?.takeIf { it > 0 } ?: 1

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            breakdown.take(6).forEach { item ->
                val fraction = item.count.toFloat() / maxCount
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Sport icon
                    Icon(
                        imageVector  = sportIcon(item.sport),
                        contentDescription = item.sport,
                        tint         = MaterialTheme.colorScheme.primary,
                        modifier     = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text     = item.sport,
                        modifier = Modifier.width(90.dp),
                        style    = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Horizontal bar (track + fill)
                    Box(
                        modifier = Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                                .clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("${item.count}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}

// ── Badge grid ─────────────────────────────────────────────────────────────

@Composable
private fun BadgeGrid(earnedBadges: List<BadgeEntity>, userId: String) {
    val earnedIds = earnedBadges.map { it.badgeId }.toSet()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BADGE_CATALOG.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { def ->
                    val earned = earnedIds.contains("${def.idPrefix}_$userId")
                    BadgeCell(def = def, earned = earned, modifier = Modifier.weight(1f))
                }
                // Pad incomplete final row
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BadgeCell(def: BadgeDef, earned: Boolean, modifier: Modifier = Modifier) {
    val (bgColor, accentColor) = rarityColors(def.rarity, earned)

    Card(
        modifier  = modifier,
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(if (earned) 3.dp else 0.dp)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Icon with lock overlay when locked
            Box(contentAlignment = Alignment.BottomEnd) {
                Icon(
                    imageVector  = def.icon,
                    contentDescription = def.name,
                    tint         = if (earned) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    modifier     = Modifier.size(30.dp)
                )
                if (!earned) {
                    Icon(
                        imageVector  = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint         = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier     = Modifier.size(12.dp)
                    )
                }
            }
            // Name
            Text(
                text       = def.name,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = if (earned) FontWeight.Bold else FontWeight.Normal,
                textAlign  = TextAlign.Center,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                fontSize   = 10.sp,
                color      = if (earned) MaterialTheme.colorScheme.onSurface
                             else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            // Rarity (earned) or requirement (locked)
            Text(
                text      = if (earned) def.rarity else def.requirement,
                style     = MaterialTheme.typography.labelSmall,
                fontSize  = 9.sp,
                textAlign = TextAlign.Center,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis,
                color     = if (earned) accentColor.copy(alpha = 0.9f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }
    }
}

// ─── Utilities ────────────────────────────────────────────────────────────────

/** Background color + accent color for each rarity tier. */
private fun rarityColors(rarity: String, earned: Boolean): Pair<Color, Color> {
    if (!earned) return Pair(Color(0xFFF5F5F5), Color.Gray)
    return when (rarity) {
        "LEGENDARY" -> Pair(Color(0xFFFFF8E1), Color(0xFFFFAB00))
        "EPIC"      -> Pair(Color(0xFFEDE7F6), Color(0xFF7C4DFF))
        "RARE"      -> Pair(Color(0xFFE3F2FD), Color(0xFF1E88E5))
        else        -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))  // COMMON = green
    }
}

/** Returns a Material Icon for a given sport name. */
private fun sportIcon(sport: String): ImageVector = when (sport.lowercase(Locale.getDefault()).trim()) {
    "fútbol", "futbol", "soccer", "football"        -> Icons.Default.SportsSoccer
    "basketball", "baloncesto"                       -> Icons.Default.SportsBasketball
    "tennis", "tenis"                                -> Icons.Default.SportsTennis
    "running", "correr", "carrera", "run"            -> Icons.Default.DirectionsRun
    "swimming", "natación", "natacion"               -> Icons.Default.Pool
    "volleyball", "voleibol", "volley"               -> Icons.Default.SportsVolleyball
    "cycling", "ciclismo", "bike"                    -> Icons.Default.DirectionsBike
    "gym", "gimnasio", "fitness", "crossfit"         -> Icons.Default.FitnessCenter
    else                                             -> Icons.Default.Sports
}

/** Formats a Unix timestamp as "dd/MM" for chart and card labels. */
private fun formatShortDate(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(timestamp))
}
