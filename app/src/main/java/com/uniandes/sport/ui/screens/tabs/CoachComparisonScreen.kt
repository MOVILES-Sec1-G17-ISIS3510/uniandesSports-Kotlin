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
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.models.Profesor
import com.uniandes.sport.ui.components.CoachAvatar
import com.uniandes.sport.viewmodels.profesores.CoachComparisonViewModel
import com.uniandes.sport.viewmodels.profesores.CoachComparisonUiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachComparisonScreen(
    coachIds: String,
    viewModel: CoachComparisonViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateBack: () -> Unit,
    onBookClass: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(coachIds) {
        viewModel.loadComparison(coachIds)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is CoachComparisonUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is CoachComparisonUiState.EmptyOffline -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No connection and no cached data available.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadComparison(coachIds) }) {
                            Text("Retry")
                        }
                    }
                }
                is CoachComparisonUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadComparison(coachIds) }) {
                            Text("Retry")
                        }
                    }
                }
                is CoachComparisonUiState.Success -> {
                    ComparisonMatrixContent(
                        comparedCoaches = state.coaches,
                        isOffline = state.isOffline,
                        onBookClass = onBookClass
                    )
                }
            }
        }
    }
}

@Composable
fun ComparisonMatrixContent(
    comparedCoaches: List<Profesor>,
    isOffline: Boolean,
    onBookClass: (String) -> Unit
) {
    // Responsive design parameters
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // Adapt layout sizes dynamically for tablet vs. phone viewports
    val isTablet = screenWidth > 600.dp
    val labelColumnWidth = if (isTablet) 140.dp else 115.dp
    val columnWidthMin = if (isTablet) 180.dp else 145.dp
    val paddingHorizontal = 32.dp // 16.dp margin on each side of the screen
    val availableWidth = screenWidth - labelColumnWidth - paddingHorizontal

    // Calculate dynamic column width: divide available space equally if they fit, else fallback to columnWidthMin
    val columnWidth = remember(comparedCoaches.size, availableWidth, columnWidthMin) {
        if (comparedCoaches.isEmpty()) columnWidthMin
        else {
            val calculated = availableWidth / comparedCoaches.size
            if (calculated >= columnWidthMin) calculated else columnWidthMin
        }
    }

    // Calculations to determine best-in-class metrics
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
            .verticalScroll(scrollStateVertical)
    ) {
        // Non-intrusive offline fallback banner
        if (isOffline) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = "Offline",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "You are offline – showing cached data",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Premium Header Info Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
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
                    text = "Comparing ${comparedCoaches.size} coaches. Glowing borders and badges highlight the optimal selections.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Glassmorphic Comparison Matrix Container
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Sticky Left Column (Row Headers)
                Column(
                    modifier = Modifier.width(labelColumnWidth)
                ) {
                    CellHeader(height = 175.dp, label = "Coaches")
                    CellLabel(height = 56.dp, label = "Sport")
                    CellLabel(height = 56.dp, label = "Price / hr")
                    CellLabel(height = 56.dp, label = "Rating")
                    CellLabel(height = 56.dp, label = "Experience")
                    CellLabel(height = 100.dp, label = "Specialty")
                    CellLabel(height = 56.dp, label = "Sport Rank")
                    CellLabel(height = 56.dp, label = "Sessions")
                    CellLabel(height = 56.dp, label = "Wins")
                    CellLabel(height = 56.dp, label = "Verified")
                }

                // 2. Horizontally Scrollable Columns for the Coaches
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scrollStateHorizontal)
                ) {
                    comparedCoaches.forEach { coach ->
                        Column(
                            modifier = Modifier.width(columnWidth)
                        ) {
                            // Coach Header: Profile Picture, Name and Booking Button
                            Box(
                                modifier = Modifier
                                    .height(175.dp)
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(contentAlignment = Alignment.BottomEnd) {
                                        CoachAvatar(profesor = coach, size = 60.dp)
                                        if (coach.verified) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Verified",
                                                tint = Color(0xFF3B82F6),
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                                                    .padding(1.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = coach.nombre,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { onBookClass(coach.id) },
                                        contentPadding = PaddingValues(horizontal = 14.dp),
                                        modifier = Modifier.height(34.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiary,
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text("Book", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }

                            // Sport Row
                            CellContent(height = 56.dp, text = coach.deporte)

                            // Price per hour (Lowest is best)
                            val coachPriceVal = parsePrice(coach.precio)
                            val isBestPrice = coachPriceVal == lowestPrice && lowestPrice != 99999
                            CellContent(
                                height = 56.dp,
                                text = coach.precio,
                                isHighlighted = isBestPrice,
                                highlightBgColor = Color(0xFFE8F5E9),
                                highlightStrokeColor = Color(0xFF4CAF50),
                                textColor = if (isBestPrice) Color(0xFF1B5E20) else Color.Unspecified,
                                badgeText = if (isBestPrice) "Best Price" else null
                            )

                            // Rating (Highest is best)
                            val isBestRating = coach.rating == highestRating && highestRating > 0.0
                            CellContent(
                                height = 56.dp,
                                text = if (coach.totalReviews > 0) {
                                    "${String.format(Locale.US, "%.1f", coach.rating)} (${coach.totalReviews})"
                                } else {
                                    "New"
                                },
                                isHighlighted = isBestRating,
                                highlightBgColor = Color(0xFFFFFDE7),
                                highlightStrokeColor = Color(0xFFFFC107),
                                textColor = if (isBestRating) Color(0xFFE65100) else Color.Unspecified,
                                icon = {
                                    Icon(
                                        Icons.Default.Star,
                                        null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                badgeText = if (isBestRating) "Top Rated" else null
                            )

                            // Experience (Most experience is best)
                            val expYears = parseExperience(coach.experiencia)
                            val isBestExp = expYears == mostExperience && mostExperience > 0
                            CellContent(
                                height = 56.dp,
                                text = coach.experiencia,
                                isHighlighted = isBestExp,
                                highlightBgColor = Color(0xFFE3F2FD),
                                highlightStrokeColor = Color(0xFF2196F3),
                                textColor = if (isBestExp) Color(0xFF0D47A1) else Color.Unspecified,
                                badgeText = if (isBestExp) "Top Exp" else null
                            )

                            // Specialty
                            CellContent(
                                height = 100.dp,
                                text = coach.especialidad,
                                maxLines = 4
                            )

                            // Rank in sport (Lowest number rank is best, e.g. #1)
                            val isBestRank = coach.rankInSport == bestRank && bestRank != 99999
                            CellContent(
                                height = 56.dp,
                                text = "#${coach.rankInSport}",
                                isHighlighted = isBestRank,
                                highlightBgColor = Color(0xFFF3E5F5),
                                highlightStrokeColor = Color(0xFF9C27B0),
                                textColor = if (isBestRank) Color(0xFF4A148C) else Color.Unspecified,
                                badgeText = if (isBestRank) "Leader" else null
                            )

                            // Sessions Delivered
                            CellContent(height = 56.dp, text = coach.sessionsDelivered.toString())

                            // Tournament Wins
                            CellContent(height = 56.dp, text = coach.tournamentWins.toString())

                            // Verification status
                            Box(
                                modifier = Modifier
                                    .height(56.dp)
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
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
                                    Text(
                                        text = "No",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Aligned Table cells components

@Composable
fun CellHeader(height: androidx.compose.ui.unit.Dp, label: String) {
    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
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
    highlightBgColor: Color = Color.Transparent,
    highlightStrokeColor: Color = Color.Transparent,
    textColor: Color = Color.Unspecified,
    badgeText: String? = null,
    icon: @Composable (() -> Unit)? = null,
    maxLines: Int = 1
) {
    val borderStroke = if (isHighlighted && highlightStrokeColor != Color.Transparent) {
        androidx.compose.foundation.BorderStroke(1.5.dp, highlightStrokeColor)
    } else {
        androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }

    val bgModifier = if (isHighlighted && highlightBgColor != Color.Transparent) {
        Modifier.background(highlightBgColor)
    } else {
        Modifier.background(MaterialTheme.colorScheme.surface)
    }

    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .border(borderStroke)
            .then(bgModifier)
            .padding(6.dp),
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
                    fontSize = 11.5.sp,
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
                    color = highlightStrokeColor,
                    modifier = Modifier.height(13.dp)
                ) {
                    Text(
                        text = badgeText.uppercase(),
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                    )
                }
            }
        }
    }
}

private fun parsePrice(priceStr: String): Int {
    return priceStr.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 99999
}

private fun parseExperience(expStr: String): Int {
    return expStr.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
}
