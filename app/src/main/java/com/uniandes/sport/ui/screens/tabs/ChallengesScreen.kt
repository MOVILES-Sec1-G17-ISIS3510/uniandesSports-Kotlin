package com.uniandes.sport.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.*
import com.uniandes.sport.models.*
import com.uniandes.sport.viewmodels.retos.RetosViewModelInterface
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
    val currentUserId = Firebase.auth.currentUser?.uid ?: "no_user"
    
    val creationStatus by viewModel.creationStatus.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(creationStatus) {
        if (creationStatus.startsWith("ERROR:")) {
            android.widget.Toast.makeText(context, "Failed: ${creationStatus.removePrefix("ERROR: ")}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Challenge")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA)),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // --- SECTION: ACTIVE CHALLENGES ---
            if (activeChallenges.isNotEmpty()) {
                item {
                    SectionHeader(title = "MY ACTIVE CHALLENGES", subtitle = "You are doing great!")
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        items(activeChallenges) { reto ->
                            ActiveChallengeCard(
                                reto = reto,
                                currentUserId = currentUserId,
                                onClick = { /* Navigate to detail */ }
                            )
                        }
                    }
                }
            }

            // --- SECTION: EXPLORE NEW CHALLENGES ---
            item {
                SectionHeader(title = "EXPLORE NEW CHALLENGES", subtitle = "Find your next goal")
            }

            // STICKY-LIKE FILTERS
            item {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    // Type Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Individual", "Team").forEach { type ->
                            FilterTab(
                                text = type,
                                selected = selectedType.equals(type, ignoreCase = true),
                                onClick = { viewModel.setTypeFilter(type) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sport Chips
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val sports = listOf("All Sports", "Soccer", "Running", "Calisthenics", "Tennis")
                        items(sports) { sport ->
                            SportChip(
                                text = sport,
                                selected = selectedSport.equals(sport, ignoreCase = true),
                                onClick = { 
                                    viewModel.setSportFilter(sport) 
                                    logViewModel.log(
                                        screen = "ChallengesScreen",
                                        action = "SPORT_FILTER_APPLIED",
                                        params = mapOf("sport_category" to sport)
                                    )
                                }
                            )
                        }
                    }
                }
            }

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
                            onJoin = { viewModel.joinReto(reto.id, currentUserId) }
                        )
                    }
                }
            }
        }
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
fun FilterTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (selected) Color(0xFF0D1B3E) else Color(0xFFE9F1F5),
        modifier = Modifier.height(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(text = text, color = if (selected) Color.White else Color(0xFF0D1B3E), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
fun SportChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontSize = 12.sp) },
        leadingIcon = if (selected) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) } } else null,
        shape = RoundedCornerShape(12.dp)
    )
}

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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("NEW CHALLENGE", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                    OutlinedTextField(value = difficulty, onValueChange = { difficulty = it }, label = { Text("Difficulty") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(value = sport, onValueChange = { sport = it }, label = { Text("Sport") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(value = goalLabel, onValueChange = { goalLabel = it }, label = { Text("Goal") }, placeholder = { Text("e.g. 100 km") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = startDateStr, onValueChange = { startDateStr = it }, label = { Text("Start") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), trailingIcon = { Icon(Icons.Default.CalendarToday, null) })
                    OutlinedTextField(value = endDateStr, onValueChange = { endDateStr = it }, label = { Text("End") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), trailingIcon = { Icon(Icons.Default.CalendarToday, null) })
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
                    Button(
                        onClick = {
                            val creator: RetoCreator = if (type.lowercase() == "team") TeamRetoCreator() else IndividualRetoCreator()
                            try {
                                val start = dateFormat.parse(startDateStr)
                                val end = dateFormat.parse(endDateStr)
                                val newReto = creator.createReto(name, sport, difficulty, goalLabel, currentUserId, type, start, end)
                                onCreate(newReto)
                            } catch (e: Exception) {
                                val newReto = creator.createReto(name, sport, difficulty, goalLabel, currentUserId, type, Date(), Date())
                                onCreate(newReto)
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        )
                    ) { Text("Create") }
                }
            }
        }
    }
}
