package com.uniandes.sport.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.models.Profesor
import com.uniandes.sport.ui.components.CoachAvatar
import com.uniandes.sport.viewmodels.profesores.ProfesoresViewModelInterface
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachComparisonScreen(
    coachIds: String,
    profesoresViewModel: ProfesoresViewModelInterface,
    onNavigateBack: () -> Unit,
    onBookClass: (String) -> Unit
) {
    val allCoaches by profesoresViewModel.profesores.collectAsState()
    val selectedIds = remember(coachIds) { coachIds.split(",").filter { it.isNotBlank() } }
    val comparedCoaches = remember(allCoaches, selectedIds) {
        allCoaches.filter { it.id in selectedIds }
    }

    LaunchedEffect(Unit) {
        if (allCoaches.isEmpty()) {
            profesoresViewModel.fetchProfesores()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Coach Comparison", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(
                            "SIDE-BY-SIDE MATRIX",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (comparedCoaches.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Compare,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No coaches selected for comparison.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            return@Scaffold
        }

        // Metrics calculations for highlighting the best attributes
        val lowestPrice = remember(comparedCoaches) {
            comparedCoaches.map { parsePrice(it.precio) }.minOrNull() ?: 99999
        }
        val highestRating = remember(comparedCoaches) {
            comparedCoaches.map { it.rating }.maxOrNull() ?: 0.0
        }
        val mostExperience = remember(comparedCoaches) {
            comparedCoaches.map { parseExperience(it.experiencia) }.maxOrNull() ?: 0
        }
        val bestRank = remember(comparedCoaches) {
            comparedCoaches.map { it.rankInSport }.filter { it > 0 }.minOrNull() ?: 99999
        }

        val scrollStateVertical = rememberScrollState()
        val scrollStateHorizontal = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollStateVertical)
        ) {
            // Header Info Card showing comparison status
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Compare,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Comparing ${comparedCoaches.size} coaches side-by-side. The best value in each category is highlighted.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Matrix Layout
            // Left sticky labels + Right scrollable content
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
            ) {
                // Fixed Left Columns for Labels
                Column(
                    modifier = Modifier.width(110.dp)
                ) {
                    // Row Headers with aligned fixed heights
                    CellHeader(height = 160.dp, label = "Coach")
                    CellLabel(height = 56.dp, label = "Sport")
                    CellLabel(height = 56.dp, label = "Price / hr")
                    CellLabel(height = 56.dp, label = "Rating")
                    CellLabel(height = 56.dp, label = "Experience")
                    CellLabel(height = 96.dp, label = "Specialty")
                    CellLabel(height = 56.dp, label = "Sport Rank")
                    CellLabel(height = 56.dp, label = "Sessions")
                    CellLabel(height = 56.dp, label = "Wins")
                    CellLabel(height = 56.dp, label = "Verified")
                }

                // Scrollable Coach Columns
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scrollStateHorizontal)
                ) {
                    comparedCoaches.forEach { coach ->
                        Column(
                            modifier = Modifier
                                .width(150.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                        ) {
                            // Column headers: Coach photo + name + action
                            Box(
                                modifier = Modifier
                                    .height(160.dp)
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(contentAlignment = Alignment.BottomEnd) {
                                        CoachAvatar(profesor = coach, size = 56.dp)
                                        if (coach.verified) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Verified",
                                                tint = Color(0xFF3B82F6),
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = coach.nombre,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { onBookClass(coach.id) },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                    ) {
                                        Text("Book", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Sport
                            CellContent(height = 56.dp, text = coach.deporte)

                            // Price per hour (lowest is best)
                            val isBestPrice = parsePrice(coach.precio) == lowestPrice && lowestPrice != 99999
                            CellContent(
                                height = 56.dp,
                                text = coach.precio,
                                isHighlighted = isBestPrice,
                                highlightColor = Color(0xFFE8F5E9), // Light green tint
                                textColor = if (isBestPrice) Color(0xFF2E7D32) else Color.Unspecified,
                                badgeText = if (isBestPrice) "Best Value" else null
                            )

                            // Rating (highest is best)
                            val isBestRating = coach.rating == highestRating && highestRating > 0.0
                            CellContent(
                                height = 56.dp,
                                text = if (coach.totalReviews > 0) {
                                    "${String.format(Locale.US, "%.1f", coach.rating)} (${coach.totalReviews})"
                                } else {
                                    "New"
                                },
                                isHighlighted = isBestRating,
                                highlightColor = Color(0xFFFFFDE7), // Light yellow tint
                                textColor = if (isBestRating) Color(0xFFF57F17) else Color.Unspecified,
                                icon = {
                                    Icon(
                                        Icons.Default.Star,
                                        null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )

                            // Experience (highest is best)
                            val expYears = parseExperience(coach.experiencia)
                            val isBestExp = expYears == mostExperience && mostExperience > 0
                            CellContent(
                                height = 56.dp,
                                text = coach.experiencia,
                                isHighlighted = isBestExp,
                                highlightColor = Color(0xFFECEFF1), // Subtle gray highlight
                                textColor = if (isBestExp) MaterialTheme.colorScheme.primary else Color.Unspecified
                            )

                            // Specialty
                            CellContent(
                                height = 96.dp,
                                text = coach.especialidad,
                                maxLines = 4
                            )

                            // Rank in sport (lowest number is best, e.g. #1)
                            val isBestRank = coach.rankInSport == bestRank && bestRank != 99999
                            CellContent(
                                height = 56.dp,
                                text = "#${coach.rankInSport}",
                                isHighlighted = isBestRank,
                                highlightColor = Color(0xFFEDE7F6), // Light purple highlight
                                textColor = if (isBestRank) Color(0xFF673AB7) else Color.Unspecified
                            )

                            // Sessions
                            CellContent(height = 56.dp, text = coach.sessionsDelivered.toString())

                            // Wins
                            CellContent(height = 56.dp, text = coach.tournamentWins.toString())

                            // Verified Status
                            Box(
                                modifier = Modifier
                                    .height(56.dp)
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (coach.verified) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Yes",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Text("No", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper Cell components to ensure rows align properly

@Composable
fun CellHeader(height: androidx.compose.ui.unit.Dp, label: String) {
    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label.uppercase(),
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun CellLabel(height: androidx.compose.ui.unit.Dp, label: String) {
    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CellContent(
    height: androidx.compose.ui.unit.Dp,
    text: String,
    isHighlighted: Boolean = false,
    highlightColor: Color = Color.Transparent,
    textColor: Color = Color.Unspecified,
    badgeText: String? = null,
    icon: @Composable (() -> Unit)? = null,
    maxLines: Int = 1
) {
    val bgModifier = if (isHighlighted) {
        Modifier.background(highlightColor)
    } else {
        Modifier.background(MaterialTheme.colorScheme.surface)
    }

    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            .then(bgModifier)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    icon()
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = text,
                    fontSize = 12.sp,
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (badgeText != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.height(14.dp)
                ) {
                    Text(
                        text = badgeText.uppercase(),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

// Utility functions to parse text metrics

private fun parsePrice(priceStr: String): Int {
    return priceStr.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 99999
}

private fun parseExperience(expStr: String): Int {
    return expStr.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
}
