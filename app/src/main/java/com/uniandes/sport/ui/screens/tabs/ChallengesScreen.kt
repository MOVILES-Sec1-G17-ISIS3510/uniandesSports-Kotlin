package com.uniandes.sport.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import kotlinx.coroutines.flow.*
import com.uniandes.sport.models.*
import com.uniandes.sport.viewmodels.retos.RetosViewModelInterface
import com.uniandes.sport.ui.components.FabMenuItem
import com.uniandes.sport.ui.navigation.Screen
import androidx.compose.ui.window.Dialog
import java.util.*
import java.text.SimpleDateFormat
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

/**
 * RETOS FEATURE IMPLEMENTATION SUMMARY:
 * 1. DESIGN PATTERNS: 
 *    - Factory Method: Used for creating different challenge types (Individual/Team) via RetoFactory.
 *    - Strategy Pattern: Used for calculating challenge progress through ProgressStrategy.
 * 2. FIRESTORE INTEGRATION:
 *    - Connected to 'challenges' collection using Map-based serialization to ensure data integrity.
 *    - Integrated real-time authentication (Firebase Auth) for 'createdBy' and 'participants' fields.
 *    - Handled PERMISSION_DENIED issues by aligning UI UID with Firestore security rules.
 */

// imPORTAANTE los retos para el usuario

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengesScreen(
    viewModel: RetosViewModelInterface,
    logViewModel: com.uniandes.sport.viewmodels.log.LogViewModelInterface,
    onNavigate: (String) -> Unit
) {
    val activeChallenges by viewModel.activeChallenges.collectAsState()
    val exploreChallenges by viewModel.exploreChallenges.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val selectedSport by viewModel.selectedSport.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var showDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var retoToLeave by remember { mutableStateOf<Reto?>(null) }
    var isFabExpanded by remember { mutableStateOf(false) }
    var selectedReto by remember { mutableStateOf<Reto?>(null) }
    val currentUserId = Firebase.auth.currentUser?.uid ?: "no_user"
    
    val creationStatus by viewModel.creationStatus.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(creationStatus) {
        if (creationStatus.startsWith("ERROR:")) {
            android.widget.Toast.makeText(context, "Failed: ${creationStatus.removePrefix("ERROR: ")}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {

            // --- SECTION: ACTIVE CHALLENGES ---
            if (activeChallenges.isNotEmpty()) {
                item {
                    SectionHeader(title = "MY ACTIVE CHALLENGES", subtitle = "You are doing great!")
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        items(activeChallenges) { reto ->
                            CircularChallengeItem(
                                reto = reto,
                                currentUserId = currentUserId,
                                onClick = { selectedReto = reto }
                            )
                        }
                    }
                }
            }

            // --- SECTION: EXPLORE NEW CHALLENGES ---
            item {
                SectionHeader(title = "EXPLORE NEW CHALLENGES", subtitle = "Find your next goal")
            }

            // STICKY FILTERS REMOVED FOR A CLEANER DASHBOARD
            // Discovery is now handled in the dedicated Search Modal

            if (isLoading && exploreChallenges.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else if (exploreChallenges.isEmpty()) {
                item {
                    EmptyDiscoveryState()
                }
            } else {
                items(exploreChallenges) { reto ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        ExploreChallengeCard(
                            reto = reto,
                            onJoin = { viewModel.joinReto(reto.id, currentUserId) },
                            onClick = { selectedReto = reto }
                        )
                    }
                }
            }
        }

        // FAB Menu Overlay
        if (isFabExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable { isFabExpanded = false },
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(
                    modifier = Modifier.padding(end = 20.dp, bottom = 100.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FabMenuItem(
                        text = "Search Challenges",
                        icon = Icons.Default.Search,
                        onClick = { 
                            showSearchDialog = true
                            isFabExpanded = false
                        }
                    )
                    FabMenuItem(
                        text = "Leave Challenge",
                        icon = Icons.Default.Logout,
                        onClick = { 
                            showLeaveDialog = true
                            isFabExpanded = false
                        }
                    )
                    FabMenuItem(
                        text = "New Challenge",
                        icon = Icons.Default.AddCircle,
                        onClick = { 
                            showDialog = true
                            isFabExpanded = false
                        }
                    )
                }
            }
        }

        // Animated Expanding FAB
        FloatingActionButton(
            onClick = { isFabExpanded = !isFabExpanded },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            val rotation by animateFloatAsState(targetValue = if (isFabExpanded) 135f else 0f, label = "fabRotation")
            Icon(
                Icons.Default.Add, 
                contentDescription = "Expand Menu",
                modifier = Modifier.rotate(rotation)
            )
        }

    }


    if (selectedReto != null) {
        ChallengeDetailModal(
            reto = selectedReto,
            currentUserId = currentUserId,
            onDismiss = { selectedReto = null },
            onJoin = { 
                selectedReto?.let { viewModel.joinReto(it.id, currentUserId) }
            },
            onLeave = {
                selectedReto?.let { 
                    viewModel.leaveReto(it.id, currentUserId)
                }
                selectedReto = null
            }
        )


    }

    if (showDialog) {
        NewChallengeDialog(
            onDismiss = { showDialog = false },
            onCreate = { newReto ->
                viewModel.addReto(newReto)
                logViewModel.log(
                    screen = "RetosScreen",
                    action = "EVENT_REGISTERED",
                    params = mapOf("type" to newReto.type, "sport" to newReto.sport)
                )
                showDialog = false
            },
            currentUserId = currentUserId
        )
    }

    if (showSearchDialog) {
        SearchChallengesDialog(
            viewModel = viewModel,
            onDismiss = { showSearchDialog = false }
        )
    }

    if (showLeaveDialog) {
        LeaveChallengeDialog(
            activeChallenges = activeChallenges,
            onDismiss = { showLeaveDialog = false },
            onLeaveClicked = { reto ->
                retoToLeave = reto
                showLeaveDialog = false
            }
        )
    }

    if (retoToLeave != null) {
        AlertDialog(
            onDismissRequest = { retoToLeave = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("¿Estás seguro?", fontWeight = FontWeight.Black) },
            text = { Text("¡Ey! Perderás todo tu progreso en este reto si te sales ahora.") },
            confirmButton = {
                Button(
                    onClick = {
                        retoToLeave?.let { viewModel.leaveReto(it.id, currentUserId) }
                        retoToLeave = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("SÍ, SALIR", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { retoToLeave = null }) {
                    Text("CANCELAR", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp
        )
    }
}

@Composable
fun EmptyDiscoveryState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
        Spacer(modifier = Modifier.height(16.dp))
        Text("No challenges found", color = Color.Gray, fontWeight = FontWeight.Medium)
        Text("Try changing filters or search query", color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
fun SearchChallengesDialog(
    viewModel: RetosViewModelInterface,
    onDismiss: () -> Unit
) {
    val selectedSport by viewModel.selectedSport.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    val sports = listOf("All Sports", "Soccer", "Running", "Calisthenics", "Tennis", "Basketball", "Swimming", "Other")
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Search, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Discovery",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Search by keyword or filter by sport",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Word Search
                CustomOutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    label = "Search by words...",
                    icon = Icons.Default.TextFields
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Type Selection
                Text(
                    "Challenge Category",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start).padding(start = 4.dp, bottom = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Individual", "Team").forEach { type ->
                        FilterTab(
                            text = type,
                            selected = selectedType.equals(type, ignoreCase = true),
                            onClick = { viewModel.setTypeFilter(type) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Sport Grid
                Text(
                    "Select Sport",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start).padding(start = 4.dp, bottom = 12.dp)
                )
                
                // Optimized to 2 columns to prevent text cutoff
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val chunks = sports.chunked(2)
                    chunks.forEach { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            chunk.forEach { sport ->
                                SportChip(
                                    text = sport,
                                    selected = selectedSport.equals(sport, ignoreCase = true),
                                    onClick = { viewModel.setSportFilter(sport) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Filler boxes if chunk is not full
                            if (chunk.size < 2) {
                                repeat(2 - chunk.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("SHOW RESULTS", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun FilterTab(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.height(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text, 
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, 
                fontWeight = FontWeight.Bold, 
                fontSize = 12.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { 
            Text(
                text, 
                fontSize = 11.sp, 
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            ) 
        },
        leadingIcon = {
            if (text == "All Sports") {
                Icon(Icons.Default.Sports, null, modifier = Modifier.size(16.dp))
            } else {
                com.uniandes.sport.ui.components.SportIconBox(sport = text, size = 16.dp)
            }
        },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondary,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.Transparent,
            selectedBorderColor = MaterialTheme.colorScheme.secondary
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChallengeDialog(onDismiss: () -> Unit, onCreate: (Reto) -> Unit, currentUserId: String) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Individual") }
    var difficulty by remember { mutableStateOf("Beginner") }
    var sport by remember { mutableStateOf("Soccer") }
    var goalLabel by remember { mutableStateOf("") }
    var startDateStr by remember { mutableStateOf("20/03/2026") }
    var endDateStr by remember { mutableStateOf("30/03/2026") }

    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val scrollState = androidx.compose.foundation.rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Segment (Style from Play)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AddCircle, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "New Challenge",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Set your next goal and start competing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                CustomOutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = "Challenge Name",
                    icon = Icons.Default.Edit
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        CustomOutlinedTextField(
                            value = type, 
                            onValueChange = { type = it }, 
                            label = "Type",
                            icon = Icons.Default.Groups
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CustomOutlinedTextField(
                            value = difficulty, 
                            onValueChange = { difficulty = it }, 
                            label = "Difficulty",
                            icon = Icons.Default.Speed
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                CustomOutlinedTextField(
                    value = sport, 
                    onValueChange = { sport = it }, 
                    label = "Sport",
                    icon = Icons.Default.Sports
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                CustomOutlinedTextField(
                    value = goalLabel, 
                    onValueChange = { goalLabel = it }, 
                    label = "Goal (e.g. 100 km)",
                    icon = Icons.Default.Flag
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        CustomOutlinedTextField(
                            value = startDateStr, 
                            onValueChange = { startDateStr = it }, 
                            label = "Start",
                            icon = Icons.Default.CalendarToday
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CustomOutlinedTextField(
                            value = endDateStr, 
                            onValueChange = { endDateStr = it }, 
                            label = "End",
                            icon = Icons.Default.Event
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss, 
                        modifier = Modifier.weight(1f).height(56.dp), 
                        shape = RoundedCornerShape(16.dp)
                    ) { 
                        Text("Cancel", fontWeight = FontWeight.Bold) 
                    }
                    Button(
                        onClick = {
                            val creator: RetoCreator = if (type.lowercase() == "team") TeamRetoCreator() else IndividualRetoCreator()
                            try {
                                val start = dateFormat.parse(startDateStr) ?: Date()
                                val end = dateFormat.parse(endDateStr) ?: Date()
                                val newReto = creator.createReto(name, sport, difficulty, goalLabel, currentUserId, type, start, end)
                                onCreate(newReto)
                            } catch (e: Exception) {
                                val newReto = creator.createReto(name, sport, difficulty, goalLabel, currentUserId, type, Date(), Date())
                                onCreate(newReto)
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) { 
                        Text("Create Reto", fontWeight = FontWeight.ExtraBold) 
                    }
                }
            }
        }
    }
}

@Composable
fun LeaveChallengeDialog(
    activeChallenges: List<Reto>,
    onDismiss: () -> Unit,
    onLeaveClicked: (Reto) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Logout, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Withdraw",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Select a challenge you wish to leave",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                if (activeChallenges.isEmpty()) {
                    Text(
                        "You don't have any active challenges.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        activeChallenges.forEach { reto ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onLeaveClicked(reto) },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    com.uniandes.sport.ui.components.SportIconBox(sport = reto.sport, size = 32.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        reto.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("CLOSE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
        ),
        singleLine = true
    )
}

