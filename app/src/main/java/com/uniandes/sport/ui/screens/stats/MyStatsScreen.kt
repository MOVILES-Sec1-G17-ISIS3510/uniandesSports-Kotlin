package com.uniandes.sport.ui.screens.stats

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.uniandes.sport.ui.screens.stats.components.SyncStatusBar
import com.uniandes.sport.viewmodels.stats.MyStatsViewModelInterface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyStatsScreen(
    viewModel: MyStatsViewModelInterface,
    onNavigate: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val stats by viewModel.stats.collectAsState()
    val badges by viewModel.badges.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Log.d("📊 MYSTATS:", "🖼️ Render: events=${stats.totalEvents} posts=${stats.totalPosts} km=${stats.totalKm} badges=${badges.size}")

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

            // ── Indicador de sincronización ──────────────────────────────────
            item {
                SyncStatusBar(
                    isOnline = true,
                    syncStatus = syncStatus,
                    lastSyncTime = if (stats.lastSyncAt > 0) stats.lastSyncAt else null,
                    onSync = { viewModel.refreshStats(forceSync = true) },
                    isLoading = isLoading
                )
            }

            // ── Tarjeta de perfil / level ─────────────────────────────────────
            item {
                LevelCard(
                    level = stats.level,
                    points = stats.points,
                    streakDays = stats.streakDays
                )
            }

            // ── Grid de estadísticas ──────────────────────────────────────────
            item {
                Text(
                    text = "Activity Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Groups,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label = "Events",
                        value = "${stats.totalEvents}"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.PostAdd,
                        iconTint = Color(0xFF4CAF50),
                        label = "Posts",
                        value = "${stats.totalPosts}"
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.DirectionsRun,
                        iconTint = Color(0xFFFF9800),
                        label = "Km Run",
                        value = String.format("%.1f", stats.totalKm)
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.EmojiEvents,
                        iconTint = Color(0xFFFFC107),
                        label = "Points",
                        value = "${stats.points}"
                    )
                }
            }

            // ── Progreso al siguiente level ───────────────────────────────────
            item {
                LevelProgressCard(level = stats.level, points = stats.points)
            }

            // ── Badges ────────────────────────────────────────────────────────
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🏅 Badges",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    // BUG CORREGIDO: antes era "\${badges.size}" (escapado = literal),
                    // ahora es "${badges.size}" (interpolado = número real)
                    Text(
                        text = "${badges.size} unlocked",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (badges.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🎯", fontSize = 32.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (isLoading) "Loading badges…"
                                       else "Join events, post in communities\nand run to unlock badges!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(badges) { badge ->
                            BadgeChip(badge = badge)
                        }
                    }
                }
            }

            // ── Si el usuario es nuevo y no hay datos reales ─────────────────
            if (!stats.hasRealData && !isLoading && syncStatus != "SYNCING") {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = "💡 Join events in Play, post in Communities, and run to see your stats here.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ─── Componentes privados ──────────────────────────────────────────────────────

@Composable
private fun LevelCard(level: Int, points: Int, streakDays: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Círculo de level
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$level",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Level $level",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "$points points",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            if (streakDays > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = "Streak",
                        tint = Color(0xFFFF6D00),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "$streakDays",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6D00),
                        fontSize = 16.sp
                    )
                    Text(
                        text = "days",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LevelProgressCard(level: Int, points: Int) {
    val pointsInLevel = points % 100
    val progress = pointsInLevel / 100f
    val pointsToNext = 100 - pointsInLevel

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Level $level",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$pointsToNext pts to Level ${level + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

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
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .width(90.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = badge.icon, fontSize = 24.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = badge.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Text(
                text = badge.rarity,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = borderColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
