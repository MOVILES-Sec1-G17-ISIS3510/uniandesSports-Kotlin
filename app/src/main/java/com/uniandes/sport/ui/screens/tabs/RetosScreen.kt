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
fun RetosScreen(
    viewModel: RetosViewModelInterface,
    onNavigate: (String) -> Unit
) {
    val retos by viewModel.retos.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val selectedSport by viewModel.selectedSport.collectAsState()
    
    var showDialog by remember { mutableStateOf(false) }
    
    // bamos a traer el id del usuario ke esta logueado de verdad
    val currentUserId = com.google.firebase.ktx.Firebase.auth.currentUser?.uid ?: "no_user"
    
    // bamos a ber si se creo el reto para tirar el toast
    val creationStatus by viewModel.creationStatus.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(creationStatus) {
        if (creationStatus.startsWith("ERROR:")) {
            android.widget.Toast.makeText(context, "fallo: ${creationStatus.removePrefix("ERROR: ")}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val filteredRetos = retos.filter { reto ->
        val typeMatch = if (selectedType.equals("All", ignoreCase = true)) true 
                        else reto.type.trim().equals(selectedType.trim(), ignoreCase = true)
        val sportMatch = if (selectedSport.equals("All Sports", ignoreCase = true)) true 
                         else reto.sport.trim().equals(selectedSport.trim(), ignoreCase = true)
        typeMatch && sportMatch
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "nuevo reto")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
        ) {
            // tabs de arriba
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val types = listOf("All", "Individual", "Team")
                types.forEach { type ->
                    FilterTab(
                        text = type,
                        selected = selectedType.equals(type, ignoreCase = true),
                        onClick = { viewModel.setTypeFilter(type) }
                    )
                }
            }

            // chips de deportes
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                val sports = listOf("All Sports", "Soccer", "Running", "Calisthenics", "Tennis")
                items(sports) { sport ->
                    SportChip(
                        text = sport,
                        selected = selectedSport.equals(sport, ignoreCase = true),
                        onClick = { viewModel.setSportFilter(sport) }
                    )
                }
            }

            // lista de retos
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredRetos) { reto ->
                    ChallengeCard(
                        reto = reto,
                        currentUserId = currentUserId
                    )
                }
            }
        }
    }

    if (showDialog) {
        NewChallengeDialog(
            onDismiss = { showDialog = false },
            onCreate = { newReto ->
                viewModel.addReto(newReto)
                showDialog = false
            },
            currentUserId = currentUserId
        )
    }
}

@Composable
fun FilterTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (selected) Color(0xFF0D1B3E) else Color(0xFFE9F1F5),
        modifier = Modifier.height(40.dp).padding(horizontal = 4.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(text = text, color = if (selected) Color.White else Color(0xFF0D1B3E), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun SportChip(text: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(text, color = if (selected) Color.White else Color.Black) },
        colors = AssistChipDefaults.assistChipColors(containerColor = if (selected) Color(0xFF43A047) else Color.White),
        shape = RoundedCornerShape(12.dp),
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = Color.LightGray),
        leadingIcon = {
            val icon = when (text) {
                "Running" -> Icons.Default.DirectionsRun
                "Soccer" -> Icons.Default.SportsSoccer
                "Calisthenics" -> Icons.Default.FitnessCenter
                "Tennis" -> Icons.Default.SportsTennis
                else -> Icons.Default.FlashOn
            }
            Icon(icon, contentDescription = null, tint = if (selected) Color.White else Color.Gray)
        }
    )
}

@Composable
fun ChallengeCard(reto: Reto, currentUserId: String) {
    val strategy = remember { DefaultProgressStrategy() }
    val progressRaw = reto.progressByUser[currentUserId] ?: 0.0
    val progressPercent = strategy.getPercent(progressRaw)
    
    // calculamos los dias ke faltan de las fechas ke dio el user
    val daysLeft = remember(reto.endDate) {
        reto.endDate?.let {
            val diff = it.toDate().time - Date().time
            (diff / (1000 * 60 * 60 * 24)).coerceAtLeast(0).toInt()
        } ?: 0
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE3F2FD)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when(reto.sport.lowercase()) {
                            "running" -> Icons.Default.DirectionsRun
                            "soccer" -> Icons.Default.SportsSoccer
                            "calisthenics" -> Icons.Default.FitnessCenter
                            "tennis" -> Icons.Default.SportsTennis
                            else -> Icons.Default.Sports
                        },
                        contentDescription = null,
                        tint = Color(0xFF1976D2)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = reto.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Badge(text = reto.type.replaceFirstChar { it.uppercase() }, containerColor = Color(0xFFE3F2FD), contentColor = Color(0xFF1976D2))
                        val diffColor = when(reto.difficulty.lowercase()) {
                            "advanced" -> Color(0xFFFFEBEE)
                            "intermediate" -> Color(0xFFFFF3E0)
                            else -> Color(0xFFE8F5E9)
                        }
                        val diffText = when(reto.difficulty.lowercase()) {
                            "advanced" -> Color(0xFFD32F2F)
                            "intermediate" -> Color(0xFFEF6C00)
                            else -> Color(0xFF2E7D32)
                        }
                        Badge(text = reto.difficulty.replaceFirstChar { it.uppercase() }, containerColor = diffColor, contentColor = diffText)
                    }
                }
                
                Text(text = "$progressPercent%", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color(0xFF0D1B3E))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailItem(Icons.Default.TrackChanges, reto.goalLabel)
                DetailItem(Icons.Default.AccessTime, "${daysLeft}d left")
                DetailItem(Icons.Default.Groups, "${reto.participantsCount}")
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = { progressRaw.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = Color(0xFF00897B),
                trackColor = Color(0xFFE0E0E0)
            )
        }
    }
}

@Composable
fun Badge(text: String, containerColor: Color, contentColor: Color) {
    Surface(color = containerColor, shape = RoundedCornerShape(4.dp)) {
        Text(text = text, color = contentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun DetailItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text, fontSize = 12.sp, color = Color.Gray)
    }
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
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "cerrar") }
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
                            // usamos la fabrik para crear el reto con lo k puso el user
                            val creator: RetoCreator = if (type.lowercase() == "team") TeamRetoCreator() else IndividualRetoCreator()
                            try {
                                val start = dateFormat.parse(startDateStr)
                                val end = dateFormat.parse(endDateStr)
                                val newReto = creator.createReto(name, sport, difficulty, goalLabel, currentUserId, type, start, end)
                                onCreate(newReto)
                            } catch (e: Exception) {
                                // si puso mal la fecha pues le ponemso hoy
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
