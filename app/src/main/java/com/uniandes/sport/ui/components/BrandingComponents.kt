package com.uniandes.sport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Standardized sport accent colors matching the Create Event Modal.
 * Colors used in CreateEventDialog.kt:
 * soccer: #2ECC71
 * basketball: #E67E22
 * tennis: #F1C40F
 * calisthenics: #9B59B6
 * running: #E74C3C
 */
@Composable
fun getSportAccentColor(sport: String): Color {
    return when(sport.lowercase()) {
        "soccer", "fútbol", "futbol", "football" -> Color(0xFF2ECC71)
        "basketball", "baloncesto" -> Color(0xFFE67E22)
        "tennis", "tenis" -> Color(0xFFF1C40F)
        "calisthenics", "calistenia", "calistennics" -> Color(0xFF9B59B6)
        "running", "correr" -> Color(0xFFE74C3C)
        "swimming", "natación" -> Color(0xFF3498DB)
        else -> Color(0xFF95A5A6) // Default Gray from the modal
    }
}

/**
 * Brand-compliant initials avatar.
 */
@Composable
fun InitialsAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val initials = name.split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = (size.value * 0.4).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Standardized Sport Icon Box matching Create Event Modal Style (Circular).
 */
@Composable
fun SportIconBox(sport: String, size: Dp, modifier: Modifier = Modifier) {
    val sportColor = getSportAccentColor(sport)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(sportColor.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when(sport.lowercase()) {
                "running", "correr" -> Icons.Default.DirectionsRun
                "soccer", "fútbol", "futbol", "football" -> Icons.Default.SportsSoccer
                "calisthenics", "calistenia", "calistennics" -> Icons.Default.FitnessCenter
                "tennis", "tenis" -> Icons.Default.SportsTennis
                "basketball", "baloncesto" -> Icons.Default.SportsBasketball
                "swimming", "natación" -> Icons.Default.Waves
                else -> Icons.Default.FlashOn
            },
            contentDescription = null,
            tint = sportColor,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

/**
 * Reusable badge for status, difficulty, or types.
 */
@Composable
fun ChallengeBadge(text: String, containerColor: Color, contentColor: Color) {
    Surface(color = containerColor, shape = RoundedCornerShape(4.dp)) {
        Text(
            text = text, 
            color = contentColor, 
            fontSize = 10.sp, 
            fontWeight = FontWeight.Black, 
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
