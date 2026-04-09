package com.uniandes.sport.ui.screens.tabs

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Picture Placeholder
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

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = user?.fullName ?: "Unknown User",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = user?.email ?: "",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

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
                                user = user?.copy(mainSport = selectedSportsCsv)
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

                Spacer(modifier = Modifier.weight(1f))

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

    val selectedSports = remember(selectedSportsCsv) {
        selectedSportsCsv
            .split(",")
            .map { normalizeSportId(it) }
            .filter { it.isNotBlank() }
            .toSet()
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
