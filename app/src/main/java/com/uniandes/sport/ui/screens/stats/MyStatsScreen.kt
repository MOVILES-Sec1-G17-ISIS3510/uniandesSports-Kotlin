package com.uniandes.sport.ui.screens.stats

import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.data.entities.BadgeEntity
import com.uniandes.sport.models.ActiveChallengeData
import com.uniandes.sport.models.RunDataPoint
import com.uniandes.sport.models.SportBreakdownItem
import com.uniandes.sport.ui.screens.stats.components.SyncStatusBar
import com.uniandes.sport.viewmodels.stats.MyStatsViewModelInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyStatsScreen(
    viewModel: MyStatsViewModelInterface,
    onNavigate: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val stats          by viewModel.stats.collectAsState()
    val badges         by viewModel.badges.collectAsState()
    val syncStatus     by viewModel.syncStatus.collectAsState()
    val isLoading      by viewModel.isLoading.collectAsState()
    val recentRuns     by viewModel.recentRuns.collectAsState()
    val activeChallenges by viewModel.activeChallenges.collectAsState()
    val sportBreakdown by viewModel.sportBreakdown.collectAsState()

    Log.d("📊 MYSTATS:", "🖼️ Render: events=${stats.totalEvents} posts=${stats.totalPosts} km=${stats.totalKm} badges=${badges.size} runs=${recentRuns.size} challenges=${activeChallenges.size}")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Activity", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
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

            // ── Sync status bar ───────────────────────────────────────────────
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
            item {
                LevelCard(
                    level      = stats.level,
                    points     = stats.points,
                    streakDays = stats.streakDays
                )
            }

            // ── Activity summary title ────────────────────────────────────────
            item {
                Text(
                    text     = "Activity Summary",
                    style    = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // ── Stat cards grid ───────────────────────────────────────────────
            item {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        modifier  = Modifier.weight(1f),
                        icon      = Icons.Default.Groups,
                        iconTint  = MaterialTheme.colorScheme.primary,
                        label     = "Events",
                        value     = "${stats.totalEvents}"
                    )
                    StatCard(
                        modifier  = Modifier.weight(1f),
                        icon      = Icons.Default.PostAdd,
                        iconTint  = Color(0xFF4CAF50),
                        label     = "Posts",
                        value     = "${stats.totalPosts}"
                    )
                }
            }

            item {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        modifier  = Modifier.weight(1f),
                        icon      = Icons.Default.DirectionsRun,
                        iconTint  = Color(0xFFFF9800),
                        label     = "Km Run",
                        value     = String.format("%.1f", stats.totalKm)
                    )
                    StatCard(
                        modifier  = Modifier.weight(1f),
                        icon      = Icons.Default.EmojiEvents,
                        iconTint  = Color(0xFFFFC107),
                        label     = "Points",
                        value     = "${stats.points}"
                    )
                }
            }

            // ── Level progress ────────────────────────────────────────────────
            item {
                LevelProgressCard(level = stats.level, points = stats.points)
            }

            // ── Running history chart ─────────────────────────────────────────
            if (recentRuns.isNotEmpty()) {
                item {
                    SectionTitle(emoji = "🏃", title = "Running History")
                }
                item {
                    RunningHistoryCard(runs = recentRuns)
                }
            }

            // ── Active challenges ─────────────────────────────────────────────
            if (activeChallenges.isNotEmpty()) {
                item {
                    SectionTitle(emoji = "🎯", title = "Active Challenges")
                }
                items(activeChallenges) { challenge ->
                    ChallengeProgressCard(challenge = challenge)
                }
            }

            // ── Sport breakdown ───────────────────────────────────────────────
            if (sportBreakdown.isNotEmpty()) {
                item {
                    SectionTitle(emoji = "⚽", title = "Sports Activity")
                }
                item {
                    SportBreakdownCard(breakdown = sportBreakdown)
                }
            }

            // ── Badges ────────────────────────────────────────────────────────
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = "🏅 Badges",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.weight(1f)
                    )
                    Text(
                        text  = "${badges.size} unlocked",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (badges.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors   = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment   = Alignment.CenterHorizontally
                        ) {
                            Text("🎯", fontSize = 32.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text      = if (isLoading) "Loading badges…"
                                            else "Join events, post in communities\nand run to unlock badges!",
                                style     = MaterialTheme.typography.bodyMedium,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(badges) { badge -> BadgeChip(badge = badge) }
                    }
                }
            }

            // ── New-user tip ──────────────────────────────────────────────────
            if (!stats.hasRealData && !isLoading && syncStatus != "SYNCING") {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors   = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text     = "💡 Join events in Play, post in Communities, and run to see your stats here.",
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

/** A simple "Emoji Title" row used before each major section. */
@Composable
private fun SectionTitle(emoji: String, title: String) {
    Text(
        text       = "$emoji $title",
        style      = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

// ── Level card ─────────────────────────────────────────────────────────────

@Composable
private fun LevelCard(level: Int, points: Int, streakDays: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier          = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment  = Alignment.Center
            ) {
                Text(
                    text       = "$level",
                    color      = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize   = 22.sp
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = "Level $level",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text  = "$points points",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            if (streakDays > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = "Streak",
                        tint               = Color(0xFFFF6D00),
                        modifier           = Modifier.size(24.dp)
                    )
                    Text(
                        text       = "$streakDays",
                        fontWeight = FontWeight.Bold,
                        color      = Color(0xFFFF6D00),
                        fontSize   = 16.sp
                    )
                    Text(
                        text  = "days",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ── Stat card ──────────────────────────────────────────────────────────────

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String
) {
    Card(
        modifier  = modifier,
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector  = icon,
                contentDescription = label,
                tint         = iconTint,
                modifier     = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text       = value,
                fontWeight = FontWeight.Black,
                fontSize   = 24.sp,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Level progress card ────────────────────────────────────────────────────

@Composable
private fun LevelProgressCard(level: Int, points: Int) {
    val pointsInLevel = points % 100
    val progress      = pointsInLevel / 100f
    val pointsToNext  = 100 - pointsInLevel

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text       = "Level $level",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text  = "$pointsToNext pts to Level ${level + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress   = { progress },
                modifier   = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color      = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

// ── Running history card + bar chart ──────────────────────────────────────

@Composable
private fun RunningHistoryCard(runs: List<RunDataPoint>) {
    val totalKm   = runs.sumOf { it.distanceKm.toDouble() }.toFloat()
    val bestRun   = runs.maxByOrNull { it.distanceKm }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Summary row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text       = "${runs.size} sessions",
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text  = String.format("%.1f km shown", totalKm),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (bestRun != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text       = "🥇 Best",
                            style      = MaterialTheme.typography.labelSmall,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text       = String.format("%.1f km", bestRun.distanceKm),
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color      = Color(0xFFFF9800)
                        )
                        if (bestRun.pace.isNotBlank()) {
                            Text(
                                text  = "${bestRun.pace} /km",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Bar chart
            RunBarChart(runs = runs)
        }
    }
}

/**
 * Pure-Compose bar chart for run distances.
 *
 * Layout trick: each Column fills the Row's fixed height.
 * Inside, a weighted Spacer + weighted Box push the bar to the bottom;
 * labels sit below in fixed-height Texts.
 */
@Composable
private fun RunBarChart(
    runs: List<RunDataPoint>,
    chartHeightDp: Int = 100
) {
    if (runs.isEmpty()) return

    val maxKm        = runs.maxOfOrNull { it.distanceKm }.takeIf { (it ?: 0f) > 0f } ?: 1f
    val barColor     = MaterialTheme.colorScheme.primary
    val labelColor   = MaterialTheme.colorScheme.onSurfaceVariant

    // Row height = bars area + labels area
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .height((chartHeightDp + 32).dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        runs.forEach { run ->
            // Clamp to [0.04, 0.96] so BOTH the bar weight AND the spacer weight
            // are always > 0.  Modifier.weight(0f) throws IllegalArgumentException.
            val fraction    = (run.distanceKm / maxKm).coerceIn(0.04f, 0.96f)
            val spacerFrac  = (1f - fraction).coerceAtLeast(0.04f)

            Column(
                modifier            = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Pushes the bar to the bottom of the chart area
                Spacer(Modifier.weight(spacerFrac))
                // The bar itself
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(fraction)
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(barColor)
                )
                // Labels below the bar
                Spacer(Modifier.height(4.dp))
                Text(
                    text      = String.format("%.1f", run.distanceKm),
                    fontSize  = 8.sp,
                    color     = labelColor,
                    textAlign = TextAlign.Center,
                    maxLines  = 1,
                    modifier  = Modifier.fillMaxWidth()
                )
                Text(
                    text      = formatRunDate(run.timestamp),
                    fontSize  = 7.sp,
                    color     = labelColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    maxLines  = 1,
                    modifier  = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ── Challenge progress card ────────────────────────────────────────────────

@Composable
private fun ChallengeProgressCard(challenge: ActiveChallengeData) {
    val progress      = (challenge.userProgress / 100.0).coerceIn(0.0, 1.0).toFloat()
    val progressColor = when {
        progress >= 1f   -> Color(0xFF4CAF50)
        progress >= 0.5f -> MaterialTheme.colorScheme.primary
        else             -> Color(0xFFFF9800)
    }
    val emoji = sportEmoji(challenge.sport)

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = emoji, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = challenge.title,
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1
                    )
                    if (challenge.goalLabel.isNotBlank()) {
                        Text(
                            text  = "Goal: ${challenge.goalLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text       = "${challenge.userProgress.toInt()}%",
                    fontWeight = FontWeight.Bold,
                    color      = progressColor,
                    fontSize   = 18.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress   = { progress },
                modifier   = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color      = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            if (challenge.endDate != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "Ends ${formatRunDate(challenge.endDate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Sport breakdown card ───────────────────────────────────────────────────

@Composable
private fun SportBreakdownCard(breakdown: List<SportBreakdownItem>) {
    val maxCount = breakdown.maxOfOrNull { it.count }?.takeIf { it > 0 } ?: 1

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier              = Modifier.padding(14.dp),
            verticalArrangement   = Arrangement.spacedBy(10.dp)
        ) {
            breakdown.take(6).forEach { item ->
                val fraction = item.count.toFloat() / maxCount
                val emoji    = sportEmoji(item.sport)
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text     = "$emoji ${item.sport}",
                        modifier = Modifier.width(110.dp),
                        style    = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        color    = MaterialTheme.colorScheme.onSurface
                    )
                    // Track background
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        // Fill
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                                )
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text       = "${item.count}",
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.width(20.dp),
                        textAlign  = TextAlign.End
                    )
                }
            }
        }
    }
}

// ── Badge chip ─────────────────────────────────────────────────────────────

@Composable
private fun BadgeChip(badge: BadgeEntity) {
    val bgColor = when (badge.rarity) {
        "LEGENDARY" -> Color(0xFFFFF9C4)
        "EPIC"      -> Color(0xFFEDE7F6)
        "RARE"      -> Color(0xFFE3F2FD)
        else        -> MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = when (badge.rarity) {
        "LEGENDARY" -> Color(0xFFFFD600)
        "EPIC"      -> Color(0xFF7C4DFF)
        "RARE"      -> Color(0xFF2196F3)
        else        -> MaterialTheme.colorScheme.outline
    }

    Card(
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier            = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .width(90.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = badge.icon, fontSize = 24.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text       = badge.name,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign  = TextAlign.Center,
                maxLines   = 2
            )
            Text(
                text       = badge.rarity,
                style      = MaterialTheme.typography.labelSmall,
                fontSize   = 9.sp,
                color      = borderColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─── Utilities ────────────────────────────────────────────────────────────────

/** Returns an emoji for a given sport name (case-insensitive). */
private fun sportEmoji(sport: String): String = when {
    sport.isBlank() -> "🏅"
    else -> when (sport.lowercase(Locale.getDefault()).trim()) {
        "fútbol", "futbol", "soccer", "football" -> "⚽"
        "basketball", "baloncesto"               -> "🏀"
        "tennis", "tenis"                        -> "🎾"
        "running", "correr", "carrera", "run"    -> "🏃"
        "swimming", "natación", "natacion"       -> "🏊"
        "volleyball", "voleibol", "volley"       -> "🏐"
        "cycling", "ciclismo", "bike"            -> "🚴"
        "gym", "gimnasio", "fitness", "crossfit" -> "💪"
        "baseball", "béisbol", "beisbol"         -> "⚾"
        "golf"                                   -> "⛳"
        "rugby"                                  -> "🏉"
        "handball", "balonmano"                  -> "🤾"
        "martial arts", "artes marciales", "boxing", "boxeo" -> "🥊"
        else                                     -> "🏅"
    }
}

/** Formats a Unix timestamp as "dd/MM" for chart labels. */
private fun formatRunDate(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(timestamp))
}
