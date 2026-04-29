package com.uniandes.sport.ui.screens.tabs

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniandes.sport.MainActivity
import com.uniandes.sport.models.User
import com.uniandes.sport.viewmodels.auth.FirebaseAuthViewModel
import com.uniandes.sport.viewmodels.profesores.FirestoreProfesoresViewModel
import com.uniandes.sport.data.local.ProfesoresFileStorage
import java.text.Normalizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilUsuarioScreen(
    onNavigate: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val authViewModel: FirebaseAuthViewModel = viewModel()
    var user by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoggingOut by remember { mutableStateOf(false) }
    var selectedSportsCsv by remember { mutableStateOf("") }
    var isSavingSports by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }

    val profesoresViewModel: FirestoreProfesoresViewModel = viewModel()
    val profesores by profesoresViewModel.profesores.collectAsState()

    LaunchedEffect(Unit) {
        profesoresViewModel.fetchProfesores()
    }

    LaunchedEffect(Unit) {
        authViewModel.getUser(
            onSuccess = { 
                user = it
                selectedSportsCsv = it.mainSport
                isLoading = false
            },
            onFailure = { 
                isLoading = false
                Toast.makeText(context, "Error loading profile", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Picture UI
                Box(contentAlignment = Alignment.BottomEnd) {
                    Surface(
                        modifier = Modifier.size(100.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(60.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = { Toast.makeText(context, "Photo upload coming soon", Toast.LENGTH_SHORT).show() },
                        modifier = Modifier
                            .size(36.dp)
                            .offset(x = (-4).dp, y = (-4).dp)
                            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Edit Photo", modifier = Modifier.size(18.dp), tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user?.fullName ?: "Unknown User",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { showEditProfileDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
                
                Text(
                    text = user?.email ?: "",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Gamification Stats Row
                ProfileStatsRow(
                    classesCount = "12", 
                    sportsCount = selectedSportsCsv.split(",").filter { it.isNotBlank() }.size.toString()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Info Cards
                InfoRow(icon = Icons.Default.School, label = "Program", value = user?.program ?: "Not set")
                InfoRow(icon = Icons.Default.Timeline, label = "Semester", value = user?.semester?.toString() ?: "0")
                EditableMainSportsCard(
                    selectedSportsCsv = selectedSportsCsv,
                    onSelectionChange = { selectedSportsCsv = it },
                    isSaving = isSavingSports,
                    onSave = {
                        isSavingSports = true
                        authViewModel.updateMainSports(
                            newMainSportsCsv = selectedSportsCsv,
                            onSuccess = {
                                selectedSportsCsv = authViewModel.mainSport
                                user = user?.copy(mainSport = authViewModel.mainSport)
                                isSavingSports = false
                                Toast.makeText(context, "Main sports updated", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { exception ->
                                isSavingSports = false
                                Toast.makeText(context, exception.message ?: "Could not update sports", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                )
                InfoRow(icon = Icons.Default.Badge, label = "Role", value = user?.role?.uppercase() ?: "ATHLETE")

                Spacer(modifier = Modifier.height(32.dp))

                // Settings & Storage Section
                Text(
                    text = "SETTINGS & DATA",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                InfoRowWithSwitch(
                    icon = Icons.Default.Notifications,
                    label = "Push Notifications",
                    value = "Receive class reminders",
                    checked = notificationsEnabled,
                    onCheckedChange = { 
                        notificationsEnabled = it 
                        Toast.makeText(context, "Notifications " + if(it) "enabled" else "disabled", Toast.LENGTH_SHORT).show()
                    }
                )

                InfoRowWithAction(
                    icon = Icons.Default.SdStorage,
                    label = "Coaches Backup",
                    value = "Export all coaches into a JSON file",
                    actionIcon = Icons.Default.Download,
                    onAction = {
                        try {
                            if (profesores.isNotEmpty()) {
                                val file = ProfesoresFileStorage.exportProfesoresSnapshot(context, profesores)
                                
                                // Logic to share/open the file so the user can actually SEE it
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "com.uniandes.sport.fileprovider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Open backup with..."))
                                
                                Toast.makeText(context, "Backup saved: ${file.name}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "No coaches data to backup", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Logout Button
                Button(
                    onClick = {
                        isLoggingOut = true
                        authViewModel.logout(
                            onSuccess = {
                                val intent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                context.startActivity(intent)
                            },
                            onFailure = { e ->
                                isLoggingOut = false
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    if (isLoggingOut) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.error)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("LOGOUT", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showEditProfileDialog) {
        var editName by remember { mutableStateOf(user?.fullName ?: "") }
        var editProgram by remember { mutableStateOf(user?.program ?: "") }
        var editSemester by remember { mutableStateOf(user?.semester?.toString() ?: "") }
        var isSavingInfo by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSavingInfo) showEditProfileDialog = false },
            title = { Text("Edit Profile", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editProgram,
                        onValueChange = { editProgram = it },
                        label = { Text("Program / Major") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editSemester,
                        onValueChange = { editSemester = it.filter { char -> char.isDigit() } },
                        label = { Text("Semester") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSavingInfo = true
                        authViewModel.updateUserProfile(
                            newName = editName,
                            newProgram = editProgram,
                            newSemester = editSemester,
                            onSuccess = {
                                isSavingInfo = false
                                showEditProfileDialog = false
                                user = user?.copy(fullName = editName, program = editProgram, semester = editSemester.toIntOrNull() ?: 0)
                                Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { error ->
                                isSavingInfo = false
                                Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = !isSavingInfo
                ) {
                    if (isSavingInfo) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }, enabled = !isSavingInfo) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditableMainSportsCard(
    selectedSportsCsv: String,
    onSelectionChange: (String) -> Unit,
    isSaving: Boolean,
    onSave: () -> Unit
) {
    val sportOptions = remember {
        listOf(
            "soccer" to "Soccer",
            "basketball" to "Basketball",
            "tennis" to "Tennis",
            "calisthenics" to "Calisthenics",
            "running" to "Running"
        )
    }
    val customSports = remember { mutableStateListOf<String>() }
    var customSportInput by remember { mutableStateOf("") }

    val selectedSports = remember(selectedSportsCsv) {
        selectedSportsCsv
            .split(",")
            .map { normalizeSportId(it) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    LaunchedEffect(selectedSportsCsv) {
        selectedSports
            .filter { selected -> sportOptions.none { it.first == selected } }
            .forEach { selectedCustom ->
                if (customSports.none { normalizeSportId(it) == selectedCustom }) {
                    customSports.add(selectedCustom)
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Sports, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = "Main Sports", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(text = if (selectedSports.isEmpty()) "Select one or more sports" else selectedSports.joinToString(", ") { displaySportLabel(it) }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sportOptions.forEach { (sportId, sportLabel) ->
                val selected = selectedSports.contains(sportId)
                FilterChip(
                    selected = selected,
                    onClick = {
                        val updated = if (selected) {
                            selectedSports.filterNot { it == sportId }
                        } else {
                            selectedSports + sportId
                        }
                        onSelectionChange(updated.joinToString(","))
                    },
                    label = { Text(sportLabel) },
                    leadingIcon = {
                        com.uniandes.sport.ui.components.SportIconBox(
                            sport = sportId,
                            size = 20.dp,
                            modifier = Modifier.padding(end = 2.dp)
                        )
                    }
                )
            }

            customSports
                .filter { custom -> sportOptions.none { it.first == normalizeSportId(custom) } }
                .forEach { customSport ->
                    val sportId = normalizeSportId(customSport)
                    val selected = selectedSports.contains(sportId)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val updated = if (selected) {
                                selectedSports.filterNot { it == sportId }
                            } else {
                                selectedSports + sportId
                            }
                            onSelectionChange(updated.joinToString(","))
                        },
                        label = { Text(displaySportLabel(customSport)) },
                        leadingIcon = {
                            com.uniandes.sport.ui.components.SportIconBox(
                                sport = sportId,
                                size = 20.dp,
                                modifier = Modifier.padding(end = 2.dp)
                            )
                        }
                    )
                }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = customSportInput,
                onValueChange = { customSportInput = it },
                label = { Text("Add custom sport") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    val newSport = normalizeSportId(customSportInput)
                    if (newSport.isBlank()) return@FilledIconButton

                    if (sportOptions.any { it.first == newSport }) {
                        onSelectionChange((selectedSports + newSport).distinct().joinToString(","))
                        customSportInput = ""
                        return@FilledIconButton
                    }

                    if (customSports.none { normalizeSportId(it) == newSport }) {
                        customSports.add(newSport)
                    }
                    onSelectionChange((selectedSports + newSport).distinct().joinToString(","))
                    customSportInput = ""
                },
                enabled = customSportInput.isNotBlank() && !isSaving
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add sport")
            }
        }

        FilledTonalButton(
            onClick = onSave,
            enabled = selectedSports.isNotEmpty() && !isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Save Main Sports", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun normalizeSportId(value: String): String {
    val withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    val clean = withoutAccents.trim().lowercase()

    return when (clean) {
        "soccer", "football", "futbol" -> "soccer"
        "basket", "basketball", "baloncesto" -> "basketball"
        "tenis", "tennis" -> "tennis"
        "calistenia", "calisthenics", "calistennics" -> "calisthenics"
        "running", "correr" -> "running"
        else -> clean
    }
}

private fun displaySportLabel(value: String): String {
    return normalizeSportId(value).replaceFirstChar { it.uppercase() }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun InfoRowWithAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }

        IconButton(
            onClick = onAction,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.size(36.dp)
        ) {
            Icon(actionIcon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun InfoRowWithSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun ProfileStatsRow(classesCount: String, sportsCount: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(value = classesCount, label = "Classes")
        Box(modifier = Modifier.height(40.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        StatItem(value = sportsCount, label = "Sports")
    }
}

@Composable
fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
    }
}
