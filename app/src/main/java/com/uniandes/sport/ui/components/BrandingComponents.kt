package com.uniandes.sport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.ui.theme.PrimaryUniandes
import com.uniandes.sport.ui.theme.SecondaryUniandes

/**
 * Brand-compliant initials avatar.
 * Mint Green background (#D9F0EE) and Navy Blue text (#012567).
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
            .background(SecondaryUniandes),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = PrimaryUniandes,
            fontSize = (size.value * 0.4).sp,
            fontWeight = FontWeight.Bold
        )
    }
}
