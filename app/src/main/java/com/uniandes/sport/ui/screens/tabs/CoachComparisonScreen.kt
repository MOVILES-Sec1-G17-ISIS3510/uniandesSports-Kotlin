package com.uniandes.sport.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
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
import com.uniandes.sport.viewmodels.profesores.ComparisonInsights
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachComparisonScreen(
    coachIds: String,
    viewModel: CoachComparisonViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateBack: () -> Unit,
    onBookClass: (String) -> Unit,
    onViewProfile: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val insights by viewModel.insights.collectAsState()

    LaunchedEffect(coachIds) {
        viewModel.loadComparison(coachIds)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Comparison",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
                        onBookClass = onBookClass,
                        onViewProfile = onViewProfile,
                        insights = insights
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
    onBookClass: (String) -> Unit,
    onViewProfile: (String) -> Unit,
    insights: ComparisonInsights = ComparisonInsights()
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // Insights are computed asynchronously on Dispatchers.Default (background thread)
    // in CoachComparisonViewModel.computeInsightsAsync()
    val bestPriceCoach = insights.bestPriceCoach
    val bestRatingCoach = insights.bestRatingCoach
    val bestExpCoach = insights.mostExperiencedCoach
    val bestWinsCoach = insights.mostWinsCoach

    var selectedChip by remember { mutableStateOf<String?>(null) }

    val scrollStateVertical = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollStateVertical)
            .padding(bottom = 24.dp)
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

        // Horizontal Category Chips Row
        ChipsHeaderRow(
            selectedChip = selectedChip,
            onChipSelected = { label -> selectedChip = if (selectedChip == label) null else label }
        )

        // Best-in-class highlights panel (pill highlights in image 1)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // First Row: Best rated and Best value
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (bestRatingCoach != null) {
                        HighlightPill(
                            icon = "⭐",
                            title = "Best rated",
                            coachName = bestRatingCoach.nombre.split(" ").firstOrNull() ?: bestRatingCoach.nombre,
                            borderColor = Color(0xFFFCD34D),
                            bgColor = Color(0xFFFEF3C7),
                            textColor = Color(0xFFB45309),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (bestPriceCoach != null) {
                        HighlightPill(
                            icon = "$",
                            title = "Best value",
                            coachName = bestPriceCoach.nombre.split(" ").firstOrNull() ?: bestPriceCoach.nombre,
                            borderColor = Color(0xFFA7F3D0),
                            bgColor = Color(0xFFECFDF5),
                            textColor = Color(0xFF047857),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Second Row: Most experienced
                if (bestExpCoach != null) {
                    HighlightPill(
                        icon = "🎓",
                        title = "Most experienced",
                        coachName = bestExpCoach.nombre.split(" ").firstOrNull() ?: bestExpCoach.nombre,
                        borderColor = Color(0xFFBFDBFE),
                        bgColor = Color(0xFFEFF6FF),
                        textColor = Color(0xFF1D4ED8)
                    )
                }

                // Third Row: Most wins
                if (bestWinsCoach != null) {
                    HighlightPill(
                        icon = "🏆",
                        title = "Most wins",
                        coachName = bestWinsCoach.nombre.split(" ").firstOrNull() ?: bestWinsCoach.nombre,
                        borderColor = Color(0xFFFECACA),
                        bgColor = Color(0xFFFEF2F2),
                        textColor = Color(0xFFB91C1C)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Side-by-side Coach Cards Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            comparedCoaches.forEach { coach ->
                val cardWidth = if (comparedCoaches.size > 2) 200.dp else (screenWidth - 48.dp) / 2
                CoachComparisonColumnCard(
                    coach = coach,
                    modifier = Modifier.width(cardWidth),
                    onBookClass = onBookClass,
                    onViewProfile = onViewProfile,
                    highlightedAttribute = selectedChip
                )
            }
        }
    }
}

@Composable
fun ChipsHeaderRow(
    selectedChip: String? = null,
    onChipSelected: (String) -> Unit = {}
) {
    val chips = listOf(
        "Rating" to Icons.Default.Star,
        "Reviews" to Icons.Default.Chat,
        "Price" to Icons.Default.AttachMoney,
        "Sessions" to Icons.Default.CalendarToday,
        "Wins" to Icons.Default.EmojiEvents,
        "Rank" to Icons.Default.Leaderboard,
        "Experience" to Icons.Default.School
    )
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(chips) { (label, icon) ->
            val isSelected = selectedChip == label
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) Color(0xFF009688) else Color(0xFFE2F0EC),
                border = androidx.compose.foundation.BorderStroke(
                    if (isSelected) 1.5.dp else 0.5.dp,
                    if (isSelected) Color(0xFF00796B) else Color(0xFFB5DFD5)
                ),
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable { onChipSelected(label) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White
                               else if (label == "Rating") Color(0xFFFFB300)
                               else Color(0xFF0F766E),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color(0xFF0F766E),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun HighlightPill(
    icon: String,
    title: String,
    coachName: String,
    borderColor: Color,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(text = icon, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = " • ",
                color = textColor,
                fontSize = 12.sp
            )
            Text(
                text = coachName,
                color = textColor.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun CoachComparisonColumnCard(
    coach: Profesor,
    modifier: Modifier = Modifier,
    onBookClass: (String) -> Unit,
    onViewProfile: (String) -> Unit,
    highlightedAttribute: String? = null
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Coach image avatar
            CoachAvatar(
                profesor = coach,
                size = 64.dp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Name with verified badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = coach.nombre,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (coach.verified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Text(
                text = coach.deporte,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))
            
            // Attributes vertical list
            // 1. Rating
            CoachAttributeRow(
                icon = Icons.Default.Star,
                iconTint = Color(0xFFFFB300),
                label = "Rating",
                value = if (coach.totalReviews > 0) "${coach.rating}" else "-",
                isHighlighted = highlightedAttribute == "Rating"
            )
            
            // 2. Reviews
            CoachAttributeRow(
                icon = Icons.Default.Chat,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                label = "Reviews",
                value = "${coach.totalReviews}",
                isHighlighted = highlightedAttribute == "Reviews"
            )
            
            // 3. Price
            CoachAttributeRow(
                icon = Icons.Default.AttachMoney,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                label = "Price",
                value = coach.precio,
                isHighlighted = highlightedAttribute == "Price"
            )
            
            // 4. Sessions
            CoachAttributeRow(
                icon = Icons.Default.CalendarToday,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                label = "Sessions",
                value = if (coach.sessionsDelivered > 0) "${coach.sessionsDelivered}" else "-",
                isHighlighted = highlightedAttribute == "Sessions"
            )
            
            // 5. Wins
            CoachAttributeRow(
                icon = Icons.Default.EmojiEvents,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                label = "Wins",
                value = "${coach.tournamentWins}",
                isHighlighted = highlightedAttribute == "Wins"
            )
            
            // 6. Rank
            CoachAttributeRow(
                icon = Icons.Default.Leaderboard,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                label = "Rank",
                value = "#${coach.rankInSport} / ${coach.totalReviews.coerceAtLeast(1)}",
                isHighlighted = highlightedAttribute == "Rank"
            )
            
            // 7. Experience
            CoachAttributeRow(
                icon = Icons.Default.School,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                label = "Experience",
                value = coach.experiencia,
                isHighlighted = highlightedAttribute == "Experience"
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // View Profile Button
            Button(
                onClick = { onViewProfile(coach.id) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF009688)), // Teal
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "View Profile",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CoachAttributeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    isHighlighted: Boolean = false
) {
    val bgColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isHighlighted) Color(0xFF009688) else iconTint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal
            )
        }
        Text(
            text = value,
            color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = if (isHighlighted) 14.sp else 13.sp,
            modifier = Modifier.padding(start = 24.dp)
        )
    }
}
