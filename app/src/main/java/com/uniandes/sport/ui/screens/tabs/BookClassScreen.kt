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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniandes.sport.viewmodels.booking.BookClassViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import java.util.TimeZone
import androidx.compose.foundation.clickable
import androidx.compose.ui.window.Dialog

// Opciones predefinidas
val sports = listOf("Soccer", "Tennis", "Basketball", "Swimming", "Running")
val skillLevels = listOf("Beginner", "Intermediate", "Advanced")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookClassScreen(
    profesorId: String,
    viewModel: BookClassViewModel = viewModel(),
    authViewModel: com.uniandes.sport.viewmodels.auth.FirebaseAuthViewModel = viewModel(),
    logViewModel: com.uniandes.sport.viewmodels.log.LogViewModelInterface = androidx.lifecycle.viewmodel.compose.viewModel<com.uniandes.sport.viewmodels.log.FirebaseLogViewModel>(),
    onNavigateBack: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    profesoresViewModel: com.uniandes.sport.viewmodels.profesores.FirestoreProfesoresViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var userUid by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    
    val currentProfesores by profesoresViewModel.profesores.collectAsState()
    val profesorName = remember(currentProfesores, profesorId) {
        currentProfesores.find { it.id == profesorId }?.nombre ?: "Coach"
    }

    LaunchedEffect(Unit) {
        profesoresViewModel.fetchProfesores()
        authViewModel.getUser(
            onSuccess = { user -> 
                userUid = user.uid
                userName = user.fullName.ifBlank { user.email.split("@")[0] }
            },
            onFailure = { /* Not logged in */ }
        )
    }

    // Native Calendar & Time State
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                // Bloquear días pasados (comparado con el inicio del día de hoy en UTC)
                return utcTimeMillis >= todayStart
            }
        }
    )
    
    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = 10,
        initialMinute = 0,
        is24Hour = false
    )

    var showEndTimePicker by remember { mutableStateOf(false) }
    val endTimePickerState = rememberTimePickerState(
        initialHour = 11,
        initialMinute = 0,
        is24Hour = false
    )

    var tempStartTime by remember { mutableStateOf("") }
    
    val dateFormatter = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }
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
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Info Card - premium style
            Surface(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                            )
                        )
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "Booking Request",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 16.sp
                    )
                }
            }

            Text(
                "DETAILS", 
                fontWeight = FontWeight.Black, 
                fontSize = 14.sp, 
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                ExposedDropdownMenu(
                    expanded = sportExpanded,
                    onDismissRequest = { sportExpanded = false },
                    modifier = Modifier
                        .width(with(density) { textFieldSize.width.toDp() })
                        .background(MaterialTheme.colorScheme.surface)
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
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                ExposedDropdownMenu(
                    expanded = skillExpanded,
                    onDismissRequest = { skillExpanded = false },
                    modifier = Modifier
                        .width(with(density) { textFieldSize.width.toDp() })
                        .background(MaterialTheme.colorScheme.surface)
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

            // Calendar Dialog
            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            if (datePickerState.selectedDateMillis != null) {
                                showTimePicker = true // Move to time selection
                            }
                            showDatePicker = false
                        }) {
                            Text("Next", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            // Time Picker Dialog
            if (showTimePicker) {
                Dialog(onDismissRequest = { showTimePicker = false }) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier.width(IntrinsicSize.Min)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "SELECT TIME",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.Start).padding(bottom = 16.dp)
                            )
                            
                            TimePicker(state = timePickerState)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                                TextButton(onClick = {
                                    // Validar si es hoy y la hora ya pasó
                                    val isToday = datePickerState.selectedDateMillis?.let { millis ->
                                        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
                                        val today = Calendar.getInstance()
                                        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) && 
                                        cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                                    } ?: false

                                    if (isToday) {
                                        val now = Calendar.getInstance()
                                        val selectedTime = Calendar.getInstance().apply {
                                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                            set(Calendar.MINUTE, timePickerState.minute)
                                        }
                                        if (selectedTime.before(now)) {
                                            android.widget.Toast.makeText(context, "Start time must be in the future", android.widget.Toast.LENGTH_SHORT).show()
                                            return@TextButton
                                        }
                                    }

                                    val hour = String.format("%02d", if (timePickerState.hour > 12) timePickerState.hour - 12 else if (timePickerState.hour == 0) 12 else timePickerState.hour)
                                    val minute = String.format("%02d", timePickerState.minute)
                                    val amPm = if (timePickerState.hour >= 12) "PM" else "AM"
                                    
                                    tempStartTime = "$hour:$minute $amPm"
                                    showTimePicker = false
                                    showEndTimePicker = true
                                }) { 
                                    Text("Next", fontWeight = FontWeight.Bold) 
                                }
                            }
                        }
                    }
                }
            }

            // End Time Picker Dialog
            if (showEndTimePicker) {
                Dialog(onDismissRequest = { showEndTimePicker = false }) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier.width(IntrinsicSize.Min)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "SELECT END TIME",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.Start).padding(bottom = 16.dp)
                            )
                            
                            TimePicker(state = endTimePickerState)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showEndTimePicker = false }) { Text("Cancel") }
                                TextButton(onClick = {
                                    // Validar que hora fin sea después de hora inicio
                                    val startTotalMinutes = timePickerState.hour * 60 + timePickerState.minute
                                    val endTotalMinutes = endTimePickerState.hour * 60 + endTimePickerState.minute
                                    
                                    if (endTotalMinutes <= startTotalMinutes) {
                                        android.widget.Toast.makeText(context, "End time must be after start time", android.widget.Toast.LENGTH_SHORT).show()
                                        return@TextButton
                                    }

                                    datePickerState.selectedDateMillis?.let { millis ->
                                        val datePart = dateFormatter.format(Date(millis))
                                        val hourStr = String.format("%02d", if (endTimePickerState.hour > 12) endTimePickerState.hour - 12 else if (endTimePickerState.hour == 0) 12 else endTimePickerState.hour)
                                        val minuteStr = String.format("%02d", endTimePickerState.minute)
                                        val amPmStr = if (endTimePickerState.hour >= 12) "PM" else "AM"
                                        
                                        val endTimeFormatted = "$hourStr:$minuteStr $amPmStr"
                                        viewModel.preferredSchedule = "$datePart from $tempStartTime to $endTimeFormatted"
                                    }
                                    showEndTimePicker = false
                                }) { 
                                    Text("Confirm", fontWeight = FontWeight.Bold) 
                                }
                            }
                        }
                    }
                }
            }

            // Schedule Input (Read Only + Calendar Trigger)
            OutlinedTextField(
                value = preferredSchedule,
                onValueChange = { },
                readOnly = true, // Force calendar usage
                label = { Text("Preferred Schedule") },
                placeholder = { Text("Tap to select date...") },
                leadingIcon = { 
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarToday, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) 
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                enabled = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            // Notes Input
            val maxNotesChars = 200
            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= maxNotesChars) viewModel.notes = it },
                label = { Text("Additional Notes") },
                placeholder = { Text("Any specific goals or questions...") },
                supportingText = {
                    Text(
                        text = "${notes.length} / $maxNotesChars",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
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
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
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
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { 
                        if (userUid.isBlank()) {
                            android.widget.Toast.makeText(context, "Please sign in to book", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        if (preferredSchedule.isBlank() || !preferredSchedule.contains("from") || !preferredSchedule.contains("to")) {
                            android.widget.Toast.makeText(context, "Please use the calendar to select a valid schedule range", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
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

                        viewModel.submitBooking(
                            profesorId = profesorId,
                            profesorName = profesorName,
                            studentId = userUid,
                            studentName = userName,
                            onSuccess = { isPending ->
                                if (isPending) {
                                    android.widget.Toast.makeText(context, "You are offline. Booking request is pending and will sync automatically.", android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    android.widget.Toast.makeText(context, "Request sent to coaches!", android.widget.Toast.LENGTH_LONG).show()
                                }
                                onNavigateBack()
                            },
                            onError = { error ->
                                android.widget.Toast.makeText(context, "Error: $error", android.widget.Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    enabled = !viewModel.isSubmitting
                ) {
                    if (viewModel.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Submit Request", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
