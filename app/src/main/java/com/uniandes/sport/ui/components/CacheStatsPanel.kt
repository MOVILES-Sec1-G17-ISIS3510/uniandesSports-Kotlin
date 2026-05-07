package com.uniandes.sport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Debug overlay panel showing cache statistics:
 * - Total messages cached
 * - Cache hit ratio
 * - Evictions count
 * - Current utilization
 */
@Composable
fun CacheStatsPanel(
    totalMessages: State<Int>,
    hitCount: State<Int>,
    missCount: State<Int>,
    evictionCount: State<Int>,
    isVisible: State<Boolean>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible.value) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(
                color = Color(0xFF1F2937),
                shape = RoundedCornerShape(8.dp)
            ),
        color = Color(0xFF1F2937),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LRU Cache Stats",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.then(
                        Modifier.padding(0.dp)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.then(Modifier)
                    )
                }
            }

            val totalHits = hitCount.value
            val totalMisses = missCount.value
            val total = totalHits + totalMisses
            val hitRatio = if (total > 0) {
                (totalHits.toFloat() / total * 100).toInt()
            } else {
                0
            }

            // Stats rows
            StatsRow("Total Cached", "${totalMessages.value} msgs", "")
            StatsRow("Hit Ratio", "$hitRatio% (${totalHits}H/${totalMisses}M)", "")
            StatsRow("Evictions", "${evictionCount.value} LRU", "")

            if (hitRatio >= 80) {
                Text(
                    text = "Excellent cache performance!",
                    fontSize = 11.sp,
                    color = Color(0x4CAF50),
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else if (hitRatio >= 50) {
                Text(
                    text = "Good hit ratio, monitor for improvements",
                    fontSize = 11.sp,
                    color = Color(0xFFC107),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StatsRow(label: String, value: String, emoji: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = emoji,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 6.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xB0BEC5),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
