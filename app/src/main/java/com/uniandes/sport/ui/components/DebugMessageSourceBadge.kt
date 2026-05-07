package com.uniandes.sport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.models.MessageSource

/**
 * Debug badge that shows message source (LRU, Room, Firebase)
 * Only visible in DEBUG builds or when explicitly enabled
 */
@Composable
fun MessageSourceBadge(
    source: MessageSource,
    modifier: Modifier = Modifier,
    showBadge: Boolean = true
) {
    if (!showBadge) return

    val (backgroundColor, label) = when (source) {
        MessageSource.LRU_CACHE -> {
            // Teal - super fast in-memory cache
            Color(0xFF1DB679) to "LRU"
        }
        MessageSource.ROOM_CACHE -> {
            // Blue - local database
            Color(0xFF0E7490) to "Room"
        }
        MessageSource.FIREBASE -> {
            // Purple - remote real-time
            Color(0xFF6D28D9) to "Firebase"
        }
        MessageSource.UNKNOWN -> {
            // Gray - unknown source
            Color(0xFF6B7280) to "Unknown"
        }
    }

    Box(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
    }
}

/**
 * Debug info: Show message source as emoji only (compact version)
 */
@Composable
fun MessageSourceEmoji(source: MessageSource) {
    val label = when (source) {
        MessageSource.LRU_CACHE -> "LRU"
        MessageSource.ROOM_CACHE -> "Room"
        MessageSource.FIREBASE -> "Firebase"
        MessageSource.UNKNOWN -> "Unknown"
    }
    Text(
        text = label,
        fontSize = 12.sp
    )
}
