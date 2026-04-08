package com.uniandes.sport.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.ui.components.ThemeModeToggle
import com.uniandes.sport.ui.theme.ThemeMode
import com.uniandes.sport.viewmodels.auth.AuthViewModelInterface
import com.uniandes.sport.viewmodels.log.LogViewModelInterface

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    authViewModel: AuthViewModelInterface,
    logViewModel: LogViewModelInterface,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onFinishOnboarding: () -> Unit,
    onBackToLogin: () -> Unit
) {
    val screenName = "OnboardingScreen"
    val colorScheme = MaterialTheme.colorScheme
    var currentStep by remember { mutableStateOf(1) }
    val totalSteps = 3

    var showDialog by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val isStep1Valid = authViewModel.program.isNotBlank() && authViewModel.semester.isNotBlank()
    val isStep2Valid = authViewModel.mainSport.isNotBlank()

    LaunchedEffect(authViewModel.semester) {
        if (authViewModel.semester.toIntOrNull() == null) {
            authViewModel.semester = "1"
        }
    }

    BackHandler {
        if (currentStep > 1) {
            currentStep--
        } else {
            onBackToLogin()
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = "Notice", fontWeight = FontWeight.Bold) },
            text = { Text(dialogMessage) },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("OK")
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = colorScheme.surface
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Progress Indicator
            StepIndicator(currentStep = currentStep, totalSteps = totalSteps)

            Spacer(modifier = Modifier.height(32.dp))

            // Dynamic Header
            Text(
                text = when(currentStep) {
                    1 -> "Tell us about your studies"
                    2 -> "What's your sport?"
                    else -> "Review your profile"
                },
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = when(currentStep) {
                    1 -> "Help us personalize your academic schedule"
                    2 -> "We'll suggest events based on your interests"
                    else -> "Make sure everything looks correct"
                },
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
                textAlign = TextAlign.Center
            )

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            if (targetState > initialState) {
                                slideInHorizontally { it } + fadeIn() with
                                        slideOutHorizontally { -it } + fadeOut()
                            } else {
                                slideInHorizontally { -it } + fadeIn() with
                                        slideOutHorizontally { it } + fadeOut()
                            }
                        }
                    ) { step ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            when (step) {
                                1 -> {
                                    ProgramSearchField(
                                        value = authViewModel.program,
                                        onValueChange = { authViewModel.program = it },
                                        enabled = !isLoading
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    SemesterStepperField(
                                        semesterValue = authViewModel.semester,
                                        onSemesterChange = { authViewModel.semester = it },
                                        enabled = !isLoading
                                    )
                                }
                                2 -> {
                                    MainSportLabelsField(
                                        selectedSportsCsv = authViewModel.mainSport,
                                        onSelectionChange = { authViewModel.mainSport = it },
                                        enabled = !isLoading
                                    )
                                }
                                3 -> {
                                    SummaryStep(
                                        fullName = authViewModel.fullName,
                                        email = authViewModel.email,
                                        program = authViewModel.program,
                                        semester = authViewModel.semester,
                                        sports = authViewModel.mainSport
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            if (currentStep < totalSteps) {
                                currentStep++
                            } else {
                                isLoading = true
                                authViewModel.saveOnboardingData(
                                    onSuccess = {
                                        isLoading = false
                                        logViewModel.log(screenName, "ONBOARDING_COMPLETED")
                                        onFinishOnboarding()
                                    },
                                    onFailure = { exception ->
                                        isLoading = false
                                        dialogMessage = exception.message.toString()
                                        showDialog = true
                                        logViewModel.crash(screenName, exception)
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isLoading && when(currentStep) {
                            1 -> isStep1Valid
                            2 -> isStep2Valid
                            else -> true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = if (currentStep < totalSteps) "Next Step" else "Complete Profile",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (currentStep > 1) {
                        TextButton(
                            onClick = { currentStep-- },
                            enabled = !isLoading,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Previous Step", color = colorScheme.primary)
                        }
                    } else {
                        TextButton(
                            onClick = { onBackToLogin() },
                            enabled = !isLoading,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Back to Login", color = colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        ThemeModeToggle(
            themeMode = themeMode,
            onThemeChange = onThemeChange,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        )
    }
}

@Composable
fun StepIndicator(currentStep: Int, totalSteps: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val step = index + 1
            val isActive = step <= currentStep
            val isCurrent = step == currentStep
            
            Box(
                modifier = Modifier
                    .size(if (isCurrent) 12.dp else 8.dp)
                    .background(
                        color = if (isActive) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50)
                    )
            )
            
            if (index < totalSteps - 1) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(2.dp)
                        .background(
                            if (step < currentStep) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }
}

@Composable
fun SummaryStep(
    fullName: String,
    email: String,
    program: String,
    semester: String,
    sports: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        SummaryItem(label = "Name", value = fullName)
        SummaryItem(label = "Email", value = email)
        SummaryItem(label = "Program", value = program)
        SummaryItem(label = "Semester", value = semester)
        SummaryItem(label = "Sports", value = sports)
    }
}

@Composable
fun SummaryItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SemesterStepperField(
    semesterValue: String,
    onSemesterChange: (String) -> Unit,
    enabled: Boolean
) {
    val minSemester = 1
    val maxSemester = 20
    val currentSemester = semesterValue.toIntOrNull()?.coerceIn(minSemester, maxSemester) ?: minSemester

    OutlinedTextField(
        value = currentSemester.toString(),
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text("Semester", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.FormatListNumbered,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { onSemesterChange((currentSemester + 1).coerceAtMost(maxSemester).toString()) },
                    enabled = enabled && currentSemester < maxSemester,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Increase semester",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { onSemesterChange((currentSemester - 1).coerceAtLeast(minSemester).toString()) },
                    enabled = enabled && currentSemester > minSemester,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Decrease semester",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ),
        singleLine = true
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainSportLabelsField(
    selectedSportsCsv: String,
    onSelectionChange: (String) -> Unit,
    enabled: Boolean
) {
    val sportOptions = remember {
        listOf("Football", "Basketball", "Tenis", "Calistennics", "Running")
    }
    val customSports = remember { mutableStateListOf<String>() }
    var customSportInput by remember { mutableStateOf("") }

    val selectedSports = remember(selectedSportsCsv) {
        selectedSportsCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    LaunchedEffect(selectedSportsCsv) {
        selectedSports
            .filter { selected -> sportOptions.none { it.equals(selected, ignoreCase = true) } }
            .forEach { selectedCustom ->
                if (customSports.none { it.equals(selectedCustom, ignoreCase = true) }) {
                    customSports.add(selectedCustom)
                }
            }
    }

    val displaySports = sportOptions + customSports.filter { custom ->
        sportOptions.none { it.equals(custom, ignoreCase = true) }
    }



    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, Color.Transparent, RoundedCornerShape(18.dp))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Sports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Main Sport",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            displaySports.forEach { sport ->
                val selected = selectedSports.any { it.equals(sport, ignoreCase = true) }
                FilterChip(
                    selected = selected,
                    onClick = {
                        val updated = if (selected) {
                            selectedSports.filterNot { it.equals(sport, ignoreCase = true) }
                        } else {
                            selectedSports + sport
                        }
                        onSelectionChange(updated.joinToString(", "))
                    },
                    enabled = enabled,
                    label = {
                        Text(
                            sport,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    },
                    leadingIcon = {
                        com.uniandes.sport.ui.components.SportIconBox(
                            sport = sport,
                            size = 24.dp
                        )
                    },
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = enabled,
                        selected = selected,
                        borderColor = Color.Transparent,
                        selectedBorderColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = customSportInput,
                onValueChange = { customSportInput = it },
                label = { Text("Add custom sport") },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    val newSport = customSportInput.trim()
                    if (newSport.isBlank()) return@FilledIconButton

                    if (customSports.none { it.equals(newSport, ignoreCase = true) }) {
                        customSports.add(newSport)
                    }

                    val updated = (selectedSports + newSport)
                        .distinctBy { it.lowercase() }
                        .joinToString(", ")
                    onSelectionChange(updated)
                    customSportInput = ""
                },
                enabled = enabled && customSportInput.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add sport"
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgramSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean
) {
    val uniAndesprograms = remember {
        listOf(
            "Administration",
            "Anthropology",
            "Architecture",
            "Art",
            "Biology",
            "Political Science",
            "Law",
            "Design",
            "Economics",
            "Philosophy",
            "Physics",
            "Geosciences",
            "Public Management and Government",
            "History",
            "Art History",
            "Environmental Engineering",
            "Biomedical Engineering",
            "Civil Engineering",
            "Food Engineering",
            "Systems and Computer Engineering",
            "Electrical Engineering",
            "Electronic Engineering",
            "Industrial Engineering",
            "Mechanical Engineering",
            "Chemical Engineering",
            "Languages and Culture",
            "Bachelor in Arts",
            "Bachelor in Biology",
            "Bachelor in Social Sciences",
            "Bachelor in Early Childhood Education",
            "Bachelor in Physics",
            "Bachelor in Humanities",
            "Bachelor in Mathematics",
            "Bachelor in Chemistry",
            "Literature",
            "Mathematics",
            "Medicine",
            "Microbiology",
            "Music",
            "Digital Narratives",
            "Psychology",
            "Chemistry"
        )
    }

    var expanded by remember { mutableStateOf(false) }
    var selectedFromSuggestions by remember { mutableStateOf(false) }
    
    val filteredPrograms = remember(value) {
        if (value.isBlank()) {
            emptyList()
        } else {
            uniAndesprograms.filter { program ->
                program.contains(value, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                selectedFromSuggestions = false
                onValueChange(newValue)
                expanded = newValue.isNotBlank() && filteredPrograms.isNotEmpty()
            },
            label = { Text("Search or enter program", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            trailingIcon = if (selectedFromSuggestions) {
                {
                    IconButton(onClick = {
                        selectedFromSuggestions = false
                        expanded = false
                        onValueChange("")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear selected program",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            ),
            enabled = enabled,
            readOnly = selectedFromSuggestions,
            singleLine = true
        )

        if (!selectedFromSuggestions && expanded && filteredPrograms.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    filteredPrograms.forEach { program ->
                        TextButton(
                            onClick = {
                                onValueChange(program)
                                expanded = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = program,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
