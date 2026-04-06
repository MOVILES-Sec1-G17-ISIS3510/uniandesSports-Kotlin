package com.uniandes.sport.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniandes.sport.viewmodels.booking.BookClassViewModel

// Opciones predefinidas
val sports = listOf("Soccer", "Tennis", "Basketball", "Swimming", "Running")
val skillLevels = listOf("Beginner", "Intermediate", "Advanced")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookClassScreen(
    profesorId: String,
    viewModel: BookClassViewModel = viewModel(),
    logViewModel: com.uniandes.sport.viewmodels.log.LogViewModelInterface = androidx.lifecycle.viewmodel.compose.viewModel<com.uniandes.sport.viewmodels.log.FirebaseLogViewModel>(),
    onNavigateBack: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val selectedSport = viewModel.selectedSport
    val selectedSkillLevel = viewModel.selectedSkillLevel
    val preferredSchedule = viewModel.preferredSchedule
    val notes = viewModel.notes

    var sportExpanded by remember { mutableStateOf(false) }
    var skillExpanded by remember { mutableStateOf(false) }

    var textFieldSize by remember { mutableStateOf(Size.Zero) }
    val density = LocalDensity.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            "Book a Class",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Text(
                            "PERSONAL COACHING REQUEST",
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
                actions = {
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        containerColor = Color(0xFFF9FAFB)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Info Card - simplified to only show "Booking Request"
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "Booking Request",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp
                    )
                }
            }

            Text(
                "DETAILS", 
                fontWeight = FontWeight.Black, 
                fontSize = 14.sp, 
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Sport Selection
            ExposedDropdownMenuBox(
                expanded = sportExpanded,
                onExpandedChange = { sportExpanded = !sportExpanded }
            ) {
                OutlinedTextField(
                    value = selectedSport,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Sport") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sportExpanded) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            textFieldSize = coordinates.size.toSize()
                        },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color(0xFFE5E7EB),
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White
                    )
                )
                ExposedDropdownMenu(
                    expanded = sportExpanded,
                    onDismissRequest = { sportExpanded = false },
                    modifier = Modifier
                        .width(with(density) { textFieldSize.width.toDp() })
                        .background(Color.White)
                ) {
                    sports.forEach { sport ->
                        DropdownMenuItem(
                            text = { Text(sport) },
                            onClick = {
                                viewModel.selectedSport = sport
                                sportExpanded = false
                            }
                        )
                    }
                }
            }

            // Skill Level Selection
            ExposedDropdownMenuBox(
                expanded = skillExpanded,
                onExpandedChange = { skillExpanded = !skillExpanded }
            ) {
                OutlinedTextField(
                    value = selectedSkillLevel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Skill Level") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = skillExpanded) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            textFieldSize = coordinates.size.toSize()
                        },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color(0xFFE5E7EB),
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White
                    )
                )
                ExposedDropdownMenu(
                    expanded = skillExpanded,
                    onDismissRequest = { skillExpanded = false },
                    modifier = Modifier
                        .width(with(density) { textFieldSize.width.toDp() })
                        .background(Color.White)
                ) {
                    skillLevels.forEach { level ->
                        DropdownMenuItem(
                            text = { Text(level) },
                            onClick = {
                                viewModel.selectedSkillLevel = level
                                skillExpanded = false
                            }
                        )
                    }
                }
            }

            // Schedule Input
            OutlinedTextField(
                value = preferredSchedule,
                onValueChange = { viewModel.preferredSchedule = it },
                label = { Text("Preferred Schedule") },
                placeholder = { Text("e.g. Mon & Wed 4-6 PM") },
                leadingIcon = { Icon(Icons.Default.CalendarToday, null, tint = Color.Gray, modifier = Modifier.size(20.dp)) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color(0xFFE5E7EB),
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                )
            )

            // Notes Input
            OutlinedTextField(
                value = notes,
                onValueChange = { viewModel.notes = it },
                label = { Text("Additional Notes") },
                placeholder = { Text("Any specific goals or questions...") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color(0xFFE5E7EB),
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { 
                        // Analytics Engine: BQ3 (Most scheduled sport)
                        logViewModel.log(
                            screen = "BookClassScreen",
                            action = "SESSION_SCHEDULED",
                            params = mapOf(
                                "sport_category" to selectedSport,
                                "session_type" to "coaching"
                            )
                        )
                        // Analytics Engine: BQ5 (Persist User Property for Time Elapsed query)
                        logViewModel.setUserProperty("last_coaching_date", System.currentTimeMillis().toString())

                        viewModel.submitBooking(profesorId) {
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("Submit Request", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
