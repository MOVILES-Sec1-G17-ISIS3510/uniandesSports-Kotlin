package com.uniandes.sport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.patterns.event.RankedOpenMatch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SmartMatchCard(
    recommendation: RankedOpenMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showScoreBreakdown by remember { mutableStateOf(false) }
    val event = recommendation.event
    val timeLabel = event.scheduledAt?.toDate()?.let {
        SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(it)
    } ?: "No time"

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(getSportAccentColor(event.sport).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        SportIconBox(event.sport, 28.dp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "BEST MATCH FOR YOU",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    modifier = Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = { showScoreBreakdown = true }
                    ),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = String.format("%.1f", recommendation.score),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(timeLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(14.dp))
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(event.location, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recommendation.reasons.take(3).forEach { reason ->
                    AssistChip(
                        onClick = onClick,
                        label = { Text(reason) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFFD9F0EE),
                            labelColor = Color(0xFF012567)
                        )
                    )
                }
            }
        }
    }

    if (showScoreBreakdown) {
        AlertDialog(
            onDismissRequest = { showScoreBreakdown = false },
            title = {
                Text("Score breakdown")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recommendation.contributions.forEach { contribution ->
                        val prefix = if (contribution.points >= 0) "+" else ""
                        Text(
                            text = "$prefix${String.format("%.1f", contribution.points)}  ${contribution.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (contribution.points >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Total: ${String.format("%.1f", recommendation.score)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black
                    )

                    if (recommendation.dayCalendarEvents.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Phone calendar events (same day)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        recommendation.dayCalendarEvents.forEach { calendarEvent ->
                            val conflict = recommendation.conflictingCalendarEvents.any {
                                it.startMillis == calendarEvent.startMillis && it.endMillis == calendarEvent.endMillis && it.title == calendarEvent.title
                            }
                            val indicator = if (conflict) "[CONFLICT]" else "[OK]"
                            Text(
                                text = "$indicator ${formatCalendarEvent(calendarEvent.startMillis, calendarEvent.endMillis, calendarEvent.isAllDay)} - ${calendarEvent.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (conflict) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScoreBreakdown = false }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun formatCalendarEvent(startMillis: Long, endMillis: Long, isAllDay: Boolean): String {
    if (isAllDay) return "All day"
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    return "${formatter.format(Date(startMillis))} - ${formatter.format(Date(endMillis))}"
}
