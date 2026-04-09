package com.uniandes.sport.ui.screens.tabs.play

import android.Manifest
import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Intent
import android.content.ContentUris
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.uniandes.sport.models.Event
import com.uniandes.sport.patterns.event.PhoneCalendarEvent
import java.util.Date
import java.text.SimpleDateFormat
import java.util.*
import com.uniandes.sport.ui.components.OptionSelectionRow
import com.uniandes.sport.ui.components.SportIconPicker
import com.uniandes.sport.ui.components.getSportAccentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventDialog(
    sport: String?,
    modality: String,
    onDismiss: () -> Unit,
    initialEvent: com.uniandes.sport.models.Event? = null,
    myEvents: List<Event> = emptyList(),
    onFinish: (sport: String, title: String, location: String, description: String, date: java.util.Date, endDate: java.util.Date?, skillLevel: String, maxParticipants: Long, shouldJoin: Boolean, onSuccess: () -> Unit, onError: (Exception) -> Unit) -> Unit
) {
    var title by remember { mutableStateOf(initialEvent?.title ?: "") }
    var selectedSport by remember { mutableStateOf<String?>(initialEvent?.sport ?: sport) }
    var isLocationSpecific by remember { mutableStateOf(true) }
    var customSportName by remember { mutableStateOf("") }
    // Helper to format date/time
    val initialDateString = initialEvent?.scheduledAt?.toDate()?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it) } ?: ""
    val initialTimeString = initialEvent?.scheduledAt?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: ""
    val initialFinishTimeString = initialEvent?.finishedAt?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: ""

    var dateString by remember { mutableStateOf(initialDateString) }
    var timeString by remember { mutableStateOf(initialTimeString) }
    var finishTimeString by remember { mutableStateOf(initialFinishTimeString) }
    
    var location by remember { mutableStateOf(initialEvent?.location ?: "") }
    var description by remember { mutableStateOf(initialEvent?.description ?: "") }
    var skillLevel by remember { mutableStateOf(initialEvent?.metadata?.get("skillLevel") as? String ?: "Open (any level)") }
    var maxParticipants by remember { mutableStateOf(initialEvent?.maxParticipants?.toString() ?: "10") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var shouldJoin by remember { mutableStateOf(initialEvent == null) } // Only auto-join for new events
    var isCreatedSuccessfully by remember { mutableStateOf(false) }
    var createdMatchDate by remember { mutableStateOf<Date?>(null) }
    var isCheckingSchedule by remember { mutableStateOf(false) }
    var scheduleCheckResult by remember { mutableStateOf<ScheduleCheckResult?>(null) }
    
    // Multi-day / Tournament support
    var isMultiDay by remember { 
        val event = initialEvent
        val finishedAt = event?.finishedAt
        mutableStateOf(event != null && finishedAt != null && 
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(event.scheduledAt?.toDate() ?: Date()) != 
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(finishedAt.toDate())) 
    }
    if (modality.lowercase() == "torneo" && initialEvent == null) {
        SideEffect { isMultiDay = true }
    }
    var endDateString by remember { mutableStateOf(initialEvent?.finishedAt?.toDate()?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it) } ?: "") }
    var showEndDatePicker by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current

    fun launchCalendarIntent(title: String, location: String, description: String, date: Date) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, date.time)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, date.time + 60 * 60 * 1000) // Default 1 hour
            putExtra(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_PRIVATE)
            putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        }
        context.startActivity(intent)
    }

    var showTimePicker by remember { mutableStateOf(false) }
    var showFinishTimePicker by remember { mutableStateOf(false) }
    var timePickerError by remember { mutableStateOf<String?>(null) }
    var dateValidationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dateString, timeString, finishTimeString, endDateString, isMultiDay) {
        scheduleCheckResult = null
        
        // Date Cross-Validation Logic
        if (dateString.isNotBlank() && timeString.isNotBlank()) {
            val startMillis = parseDateTime(dateString, timeString)
            val targetEndDateStr = if (isMultiDay && endDateString.isNotBlank()) endDateString else dateString
            val endMillis = if (finishTimeString.isNotBlank()) {
                parseDateTime(targetEndDateStr, finishTimeString)
            } else {
                null
            }

            if (startMillis != null && endMillis != null) {
                if (endMillis <= startMillis) {
                    dateValidationError = "End time must be after start time"
                } else {
                    dateValidationError = null
                }
            } else {
                dateValidationError = null
            }
        } else {
            dateValidationError = null
        }
    }

    
    // --- Picker States ---
    val todayStart = Calendar.getInstance().run {
        val year = get(Calendar.YEAR)
        val month = get(Calendar.MONTH)
        val day = get(Calendar.DAY_OF_MONTH)
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day, 0, 0, 0)
        }.timeInMillis
    }

    val datePickerState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis >= todayStart
            }
        }
    )
    var showDatePicker by remember { mutableStateOf(false) }
    
    val timePickerState = rememberTimePickerState(
        initialHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        initialMinute = Calendar.getInstance().get(Calendar.MINUTE),
        is24Hour = false
    )


    val endDatePickerState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis >= todayStart
            }
        }
    )
    
    val finishTimePickerState = rememberTimePickerState(
        initialHour = initialEvent?.finishedAt?.toDate()?.let { Calendar.getInstance().apply { time = it }.get(Calendar.HOUR_OF_DAY) }
            ?: ((Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1) % 24),
        initialMinute = initialEvent?.finishedAt?.toDate()?.let { Calendar.getInstance().apply { time = it }.get(Calendar.MINUTE) } ?: 0,
        is24Hour = false
    )
    
    
    val skillLevels = listOf("Open (any level)", "Beginner", "Amateur", "Advanced", "Professional")
    
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }
    
    val hasUnsavedData = title.isNotEmpty() || location.isNotEmpty() || description.isNotEmpty()
    
    fun handleDismiss() {
        if (hasUnsavedData) {
            showExitConfirmation = true
        } else {
            onDismiss()
        }
    }

    // Validation patterns (aligned with Challenges)
    val emojiRegex = "[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+".toRegex()
    val alphanumericRegex = "^[a-zA-Z0-9\\s]*$".toRegex()

    val isTitleValid = title.isNotBlank() && !emojiRegex.containsMatchIn(title) && alphanumericRegex.matches(title)
    val isCustomSportValid = selectedSport != "other" || (customSportName.isNotBlank() && !emojiRegex.containsMatchIn(customSportName) && alphanumericRegex.matches(customSportName))
    val isDescriptionValid = description.isEmpty() || !emojiRegex.containsMatchIn(description)

    // --- Date Picker Dialog ---
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val cal = Calendar.getInstance().apply { 
                            timeZone = TimeZone.getTimeZone("UTC")
                            timeInMillis = millis 
                        }
                        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        dateString = sdf.format(cal.time)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                headlineContentColor = MaterialTheme.colorScheme.onSurface,
                weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                subheadContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                dayContentColor = MaterialTheme.colorScheme.onSurface,
                selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
                todayContentColor = MaterialTheme.colorScheme.primary,
                todayDateBorderColor = MaterialTheme.colorScheme.primary
            )
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // --- Time Picker Dialog ---
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
                        "SET TIME",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
                    )
                    
                    if (timePickerError != null) {
                        Text(
                            text = timePickerError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    TimePicker(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = MaterialTheme.colorScheme.surfaceVariant,
                            clockDialSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                            clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                            selectorColor = MaterialTheme.colorScheme.primary,
                            periodSelectorBorderColor = MaterialTheme.colorScheme.outline,
                            periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            periodSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surface,
                            periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            periodSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                            timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surface,
                            timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            val selectedCal = Calendar.getInstance().apply {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                        timeInMillis = millis
                                    }
                                    set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                                    set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                                    set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                                }
                                set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                set(Calendar.MINUTE, timePickerState.minute)
                            }
                            
                            if (selectedCal.before(Calendar.getInstance())) {
                                timePickerError = "Please select a future time."
                                errorMessage = "Please select a future time."
                            } else {
                                val hour = String.format("%02d", timePickerState.hour)
                                val minute = String.format("%02d", timePickerState.minute)
                                timeString = "$hour:$minute"
                                errorMessage = null
                                timePickerError = null
                                showTimePicker = false
                            }
                        }) { Text("OK") }
                    }
                }
            }
        }
    }
    
    // --- Finish Time Picker Dialog ---
    if (showFinishTimePicker) {
        Dialog(onDismissRequest = { showFinishTimePicker = false }) {
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
                        "SET FINISH TIME",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
                    )
                    
                    TimePicker(state = finishTimePickerState)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showFinishTimePicker = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            val hour = String.format("%02d", finishTimePickerState.hour)
                            val minute = String.format("%02d", finishTimePickerState.minute)
                            finishTimeString = "$hour:$minute"
                            showFinishTimePicker = false
                        }) { Text("OK") }
                    }
                }
            }
        }
    }
    
    // --- End Date Picker Dialog ---
    if (showEndDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDatePickerState.selectedDateMillis?.let { millis ->
                        val cal = Calendar.getInstance().apply { 
                            timeZone = TimeZone.getTimeZone("UTC")
                            timeInMillis = millis 
                        }
                        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        endDateString = sdf.format(cal.time)
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = endDatePickerState)
        }
    }

    // --- Exit Confirmation Dialog ---
    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Discard Event?") },
            text = { Text("You have unsaved changes. Are you sure you want to discard this event?") },
            confirmButton = {
                TextButton(onClick = { 
                    showExitConfirmation = false
                    onDismiss() 
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text("Keep Editing") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showLocationPicker) {
        LocationPickerDialog(
            onDismiss = { showLocationPicker = false },
            onLocationSelected = { locationString ->
                location = locationString
                showLocationPicker = false
            }
        )
    }

    Dialog(
        onDismissRequest = { handleDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(scrollState)
            ) {
                if (!isCreatedSuccessfully) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { handleDismiss() }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { handleDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Titles
                Text(if (initialEvent != null) "EDIT EVENT" else "CREATE ${modality.uppercase()}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                val subtitleDescription = when (modality.lowercase()) {
                    "casual" -> "Relaxed play without strict competition."
                    "amateur" -> "Competitive play for enthusiasts."
                    "torneo" -> "Official competition with a bracket or points."
                    "training" -> "Improve your skills with practice sessions."
                    else -> "Set up the details for your $modality event"
                }
                Text(subtitleDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                
                // Sport Selector
                FormLabel("SELECT SPORT")
                Spacer(modifier = Modifier.height(8.dp))
                SportIconPicker(
                    selectedSport = selectedSport,
                    onSportSelected = { selectedSport = it }
                )
                
                if (selectedSport == "other") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customSportName,
                        onValueChange = { if (it.length <= 20) customSportName = it },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        placeholder = { Text("Sport name (e.g., Pádel, Golf)", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                        shape = RoundedCornerShape(12.dp),
                        isError = customSportName.isNotEmpty() && !isCustomSportValid,
                        trailingIcon = {
                            Text(
                                text = "${customSportName.length}/20",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (customSportName.length > 20 || (customSportName.isNotEmpty() && !isCustomSportValid)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            errorBorderColor = MaterialTheme.colorScheme.error
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                // Form Fields
                FormLabel("Title")
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 40) title = it },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    placeholder = { Text("Event title (e.g., Sunday morning game)", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    shape = RoundedCornerShape(12.dp),
                    isError = title.isNotEmpty() && !isTitleValid,
                    trailingIcon = {
                        Text(
                            text = "${title.length}/40",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (title.length > 40 || (title.isNotEmpty() && !isTitleValid)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        errorBorderColor = MaterialTheme.colorScheme.error
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Date & Time Section
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        FormLabel("Start Date")
                        Box(modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }) {
                            OutlinedTextField(
                                value = dateString,
                                onValueChange = { },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                readOnly = true,
                                enabled = false,
                                placeholder = { Text("dd/mm/aaaa", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        FormLabel("Start Time")
                        Box(modifier = Modifier.fillMaxWidth().clickable { showTimePicker = true }) {
                            OutlinedTextField(
                                value = timeString,
                                onValueChange = { },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                readOnly = true,
                                enabled = false,
                                placeholder = { Text("Start Time", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                trailingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Multi-day Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .clickable { isMultiDay = !isMultiDay }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isMultiDay) Icons.Default.EventAvailable else Icons.Default.Event,
                            contentDescription = null,
                            tint = if (isMultiDay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Multi-day / Tournament", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Event spans multiple days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = isMultiDay,
                        onCheckedChange = { isMultiDay = it },
                        thumbContent = if (isMultiDay) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                        } else null
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (isMultiDay) {
                        Column(modifier = Modifier.weight(1f)) {
                            FormLabel("End Date")
                            Box(modifier = Modifier.fillMaxWidth().clickable { showEndDatePicker = true }) {
                                OutlinedTextField(
                                    value = endDateString,
                                    onValueChange = { },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    readOnly = true,
                                    enabled = false,
                                    placeholder = { Text("dd/mm/aaaa", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                    trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                    
                    Column(modifier = Modifier.weight(if (isMultiDay) 1f else 1.5f)) {
                        FormLabel("Finish Time")
                        Box(modifier = Modifier.fillMaxWidth().clickable { showFinishTimePicker = true }) {
                            OutlinedTextField(
                                value = finishTimeString,
                                onValueChange = { },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                readOnly = true,
                                enabled = false,
                                placeholder = { Text("End Time", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                trailingIcon = { Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
                
                if (dateValidationError != null) {
                    Text(
                        text = dateValidationError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                // Skill Level
                FormLabel("Skill Level")
                OptionSelectionRow(
                    options = skillLevels,
                    selectedOption = skillLevel,
                    onOptionSelected = { skillLevel = it }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Max Players
                FormLabel("Max Players")
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = Color.Transparent,
                    modifier = Modifier.width(160.dp).height(56.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { 
                                val current = maxParticipants.toIntOrNull() ?: 10
                                if (current > 1) maxParticipants = (current - 1).toString()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        
                        Text(
                            text = maxParticipants,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        IconButton(
                            onClick = { 
                                val current = maxParticipants.toIntOrNull() ?: 10
                                if (current < 99) maxParticipants = (current + 1).toString()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                
                FormLabel("Notes")
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 250) description = it },
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    placeholder = { Text("Extra details...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    shape = RoundedCornerShape(12.dp),
                    isError = description.isNotEmpty() && !isDescriptionValid,
                    trailingIcon = {
                        Box(modifier = Modifier.fillMaxHeight().padding(bottom = 8.dp, end = 8.dp), contentAlignment = Alignment.BottomEnd) {
                            Text(
                                text = "${description.length}/250",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (description.length > 250 || (description.isNotEmpty() && !isDescriptionValid)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        errorBorderColor = MaterialTheme.colorScheme.error
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                // Location Toggle
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SPECIFIC LOCATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Switch(
                            checked = isLocationSpecific,
                            onCheckedChange = { isLocationSpecific = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                
                if (isLocationSpecific) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (modality.lowercase() == "training") {
                        Box(modifier = Modifier.fillMaxWidth().clickable { showLocationPicker = true }) {
                            OutlinedTextField(
                                value = location,
                                onValueChange = { },
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = true,
                                enabled = false,
                                placeholder = { Text("Where is it? Tap to pick on map", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = { Icon(Icons.Default.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.primary,
                                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Where is it?", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                            leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailingIcon = {
                                IconButton(onClick = { showLocationPicker = true }) {
                                    Icon(Icons.Default.Map, contentDescription = "Pick on map", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // Join as participant toggle
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("JOIN AS PARTICIPANT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Include yourself in the event initial members", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = shouldJoin,
                            onCheckedChange = { shouldJoin = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (errorMessage != null) {

                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                val isFormValid = isTitleValid &&
                    selectedSport != null &&
                    isCustomSportValid &&
                    isDescriptionValid &&
                    (!isLocationSpecific || location.isNotBlank()) &&
                    dateString.isNotBlank() &&
                    dateValidationError == null &&
                    timeString.isNotBlank() &&
                    (!isMultiDay || endDateString.isNotBlank())

                val candidateRange = buildCandidateRange(
                    dateString = dateString,
                    timeString = timeString,
                    finishTimeString = finishTimeString,
                    endDateString = endDateString,
                    isMultiDay = isMultiDay
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.EventAvailable, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Schedule check", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "Compare your app events and phone calendar before creating.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val result = scheduleCheckResult
                        when {
                            isCheckingSchedule -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Checking your schedule...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            result == null -> {
                                Text(
                                    "Tap the button below to verify overlaps with your events and calendar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            result.hasConflicts -> {
                                ConflictSummary(
                                    appConflicts = result.appConflicts,
                                    calendarConflicts = result.calendarConflicts
                                )
                            }

                            else -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("No overlaps found. You're clear to create.", color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val range = candidateRange
                                if (range == null) {
                                    errorMessage = "Please complete the date and time fields first."
                                    return@Button
                                }

                                coroutineScope.launch {
                                    isCheckingSchedule = true
                                    errorMessage = null
                                    try {
                                        val verified = verifyScheduleConflicts(
                                            context = context,
                                            myEvents = myEvents,
                                            candidateStartMillis = range.first,
                                            candidateEndMillis = range.second,
                                            excludeEventId = initialEvent?.id
                                        )
                                        scheduleCheckResult = verified
                                        if (verified.hasConflicts) {
                                            errorMessage = "Schedule overlap detected. Review the conflicts below before creating."
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Could not verify the schedule"
                                    } finally {
                                        isCheckingSchedule = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                            enabled = !isLoading && isFormValid && !isCheckingSchedule
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (scheduleCheckResult == null) "Verify my schedule" else "Re-check schedule", fontWeight = FontWeight.Bold)
                        }

                        if (result?.hasConflicts == true) {
                            Text(
                                "Fix these overlaps first. The create button stays locked until the schedule is clear.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        val finalLocation = if (isLocationSpecific) location else "Open Location"
                        val finalSport = if (selectedSport == "other") customSportName else selectedSport!!
                        
                        val startDate = try {
                            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                            sdf.parse("$dateString $timeString") ?: Date()
                        } catch (e: Exception) {
                            Date()
                        }
                        
                        val endDate = try {
                            if (finishTimeString.isNotBlank()) {
                                val targetDateStr = if (isMultiDay && endDateString.isNotBlank()) endDateString else dateString
                                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                sdf.parse("$targetDateStr $finishTimeString")
                            } else null
                        } catch (e: Exception) {
                            null
                        }
                        
                        errorMessage = null
                        isLoading = true

                        onFinish(
                            finalSport,
                            title, 
                            finalLocation, 
                            description, 
                            startDate, 
                            endDate,
                            skillLevel, 
                            maxParticipants.toLongOrNull() ?: 10L,
                            shouldJoin,
                            { 
                                isLoading = false
                                createdMatchDate = startDate
                                isCreatedSuccessfully = true
                            },
                            { e -> 
                                isLoading = false 
                                errorMessage = e.message ?: "Failed to save event"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    enabled = !isLoading && isFormValid && scheduleCheckResult?.hasConflicts == false && !isCheckingSchedule
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            if (initialEvent != null) "Save Changes" else "Create Event",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Success View
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2ECC71).copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2ECC71),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        "Event Created!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        "Your event is ready. Don't forget to add it to your calendar so you don't miss it!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = { 
                            createdMatchDate?.let { 
                                launchCalendarIntent(
                                    title = if (title.isBlank()) "Custom Event" else title,
                                    location = location,
                                    description = description,
                                    date = it
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add to Calendar", fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedButton(
                        onClick = { onDismiss() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text("Done", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
}

private data class ScheduleConflictItem(
    val source: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long
)

private data class ScheduleCheckResult(
    val appConflicts: List<ScheduleConflictItem>,
    val calendarConflicts: List<ScheduleConflictItem>
) {
    val hasConflicts: Boolean
        get() = appConflicts.isNotEmpty() || calendarConflicts.isNotEmpty()
}

private fun buildCandidateRange(
    dateString: String,
    timeString: String,
    finishTimeString: String,
    endDateString: String,
    isMultiDay: Boolean
): Pair<Long, Long>? {
    val startMillis = parseDateTime(dateString, timeString) ?: return null
    val explicitEndMillis = if (finishTimeString.isBlank()) {
        null
    } else {
        val targetDate = if (isMultiDay && endDateString.isNotBlank()) endDateString else dateString
        parseDateTime(targetDate, finishTimeString)
    }

    val endMillis = explicitEndMillis ?: (startMillis + 60 * 60 * 1000L)
    return if (endMillis > startMillis) startMillis to endMillis else null
}

private fun parseDateTime(dateString: String, timeString: String): Long? {
    return try {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        sdf.parse("$dateString $timeString")?.time
    } catch (_: Exception) {
        null
    }
}

private suspend fun verifyScheduleConflicts(
    context: android.content.Context,
    myEvents: List<Event>,
    candidateStartMillis: Long,
    candidateEndMillis: Long,
    excludeEventId: String? = null
): ScheduleCheckResult {
    val appConflicts = myEvents
        .filter { it.id != excludeEventId }
        .mapNotNull { event ->
            val eventStartMillis = event.scheduledAt?.toDate()?.time ?: return@mapNotNull null
            val eventEndMillis = event.finishedAt?.toDate()?.time ?: (eventStartMillis + 60 * 60 * 1000L)
            if (overlaps(candidateStartMillis, candidateEndMillis, eventStartMillis, eventEndMillis)) {
                ScheduleConflictItem(
                    source = "My events",
                    title = event.title.ifBlank { event.sport.ifBlank { "Event" } },
                    startMillis = eventStartMillis,
                    endMillis = eventEndMillis
                )
            } else {
                null
            }
        }

    val phoneCalendarEvents = loadPhoneCalendarEventsInRange(context, candidateStartMillis, candidateEndMillis)
    val calendarConflicts = phoneCalendarEvents
        .mapNotNull { event ->
            val eventStart = if (event.isAllDay) startOfDay(event.startMillis) else event.startMillis
            val eventEnd = if (event.isAllDay) endOfDay(event.startMillis) else event.endMillis
            if (overlaps(candidateStartMillis, candidateEndMillis, eventStart, eventEnd)) {
                ScheduleConflictItem(
                    source = "Phone calendar",
                    title = event.title.ifBlank { "Busy" },
                    startMillis = eventStart,
                    endMillis = eventEnd
                )
            } else {
                null
            }
        }

    return ScheduleCheckResult(
        appConflicts = appConflicts.sortedBy { it.startMillis },
        calendarConflicts = calendarConflicts.sortedBy { it.startMillis }
    )
}

private suspend fun loadPhoneCalendarEventsInRange(
    context: android.content.Context,
    startMillis: Long,
    endMillis: Long
): List<PhoneCalendarEvent> = withContext(Dispatchers.IO) {
    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(builder, startMillis)
    ContentUris.appendId(builder, endMillis)

    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY
    )

    val events = mutableListOf<PhoneCalendarEvent>()
    context.contentResolver.query(
        builder.build(),
        projection,
        null,
        null,
        "${CalendarContract.Instances.BEGIN} ASC"
    )?.use { cursor ->
        val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
        val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
        val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
        val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)

        while (cursor.moveToNext()) {
            val title = if (titleIdx >= 0) cursor.getString(titleIdx) else "Busy"
            val begin = if (beginIdx >= 0) cursor.getLong(beginIdx) else 0L
            val end = if (endIdx >= 0) cursor.getLong(endIdx) else begin
            val isAllDay = allDayIdx >= 0 && cursor.getInt(allDayIdx) == 1

            if (begin > 0L) {
                events += PhoneCalendarEvent(
                    title = title.takeIf { !it.isNullOrBlank() } ?: "Busy",
                    startMillis = begin,
                    endMillis = end,
                    isAllDay = isAllDay
                )
            }
        }
    }
    events
}

private fun overlaps(startA: Long, endA: Long, startB: Long, endB: Long): Boolean {
    return startA < endB && startB < endA
}

private fun startOfDay(millis: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun endOfDay(millis: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}

@Composable
private fun ConflictSummary(
    appConflicts: List<ScheduleConflictItem>,
    calendarConflicts: List<ScheduleConflictItem>
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ConflictSection(
            title = "Your app events",
            items = appConflicts
        )
        ConflictSection(
            title = "Phone calendar",
            items = calendarConflicts
        )
    }
}

@Composable
private fun ConflictSection(
    title: String,
    items: List<ScheduleConflictItem>
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            if (items.isEmpty()) {
                Text("No conflicts found here.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = "${formatConflictTime(item.startMillis)} - ${formatConflictTime(item.endMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = item.source,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatConflictTime(millis: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}

@Composable
private fun LocationPickerDialog(
    onDismiss: () -> Unit,
    onLocationSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val geocoder = remember { Geocoder(context) }
    val defaultLatLng = LatLng(4.6016, -74.0652) // Uniandes area fallback
    val cameraPositionState = rememberCameraPositionState()
    val coroutineScope = rememberCoroutineScope()
    var isLoadingAddress by remember { mutableStateOf(false) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(defaultLatLng, 15f))
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        cameraPositionState.move(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(location.latitude, location.longitude),
                                16f
                            )
                        )
                    }
                }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.78f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pick location",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = if (hasLocationPermission) {
                        "Map starts at your location. Move the map and confirm."
                    } else {
                        "Location permission denied. You can still move the map manually."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                        uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission)
                    )

                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoadingAddress
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            isLoadingAddress = true
                            val target = cameraPositionState.position.target
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val addresses = geocoder.getFromLocation(target.latitude, target.longitude, 1)
                                    val addressName = if (!addresses.isNullOrEmpty()) {
                                        val address = addresses[0]
                                        val parts = mutableListOf<String>()
                                        if (!address.thoroughfare.isNullOrBlank()) parts.add(address.thoroughfare)
                                        if (!address.subThoroughfare.isNullOrBlank()) parts.add(address.subThoroughfare)
                                        if (!address.locality.isNullOrBlank()) parts.add(address.locality)
                                        if (parts.isEmpty()) "${address.countryName}" else parts.joinToString(", ")
                                    } else {
                                        "Location"
                                    }
                                    val locationString = "$addressName - Lat: %.5f, Lng: %.5f".format(target.latitude, target.longitude)
                                    onLocationSelected(locationString)
                                } catch (e: Exception) {
                                    val fallbackLocation = "Lat: %.5f, Lng: %.5f".format(target.latitude, target.longitude)
                                    onLocationSelected(fallbackLocation)
                                } finally {
                                    isLoadingAddress = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoadingAddress
                    ) {
                        if (isLoadingAddress) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text("Use this location")
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun FormLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

