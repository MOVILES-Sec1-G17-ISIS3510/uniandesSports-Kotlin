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
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.viewmodels.auth.AuthViewModelInterface
import com.uniandes.sport.viewmodels.log.LogViewModelInterface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    authViewModel: AuthViewModelInterface,
    logViewModel: LogViewModelInterface,
    onFinishOnboarding: () -> Unit,
    onBackToLogin: () -> Unit
) {
    val screenName = "OnboardingScreen"
    var showDialog by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(authViewModel.semester) {
        if (authViewModel.semester.toIntOrNull() == null) {
            authViewModel.semester = "1"
        }
    }

    BackHandler {
        onBackToLogin()
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
            containerColor = Color.White
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Almost there...",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black
            )
            
            Text(
                text = "Complete these details to improve your experience",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
            )

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Show dynamically obtained Name and Email
                    if (authViewModel.fullName.isNotBlank()) {
                        Text(text = authViewModel.fullName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    if (authViewModel.email.isNotBlank()) {
                        Text(text = authViewModel.email, color = Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    ProgramSearchField(
                        value = authViewModel.program,
                        onValueChange = { authViewModel.program = it },
                        enabled = !isLoading
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SemesterStepperField(
                        semesterValue = authViewModel.semester,
                        onSemesterChange = { authViewModel.semester = it },
                        enabled = !isLoading
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    MainSportLabelsField(
                        selectedSportsCsv = authViewModel.mainSport,
                        onSelectionChange = { authViewModel.mainSport = it },
                        enabled = !isLoading
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (authViewModel.program.isBlank() || authViewModel.semester.isBlank() || authViewModel.mainSport.isBlank()) {
                                dialogMessage = "Please complete all fields"
                                showDialog = true
                                return@Button
                            }
                            
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
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "Complete Registration",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { onBackToLogin() },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Back to Login",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
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
        label = { Text("Semester", color = Color.Gray, fontSize = 13.sp) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.FormatListNumbered,
                contentDescription = null,
                tint = Color.Gray,
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
            unfocusedContainerColor = Color(0xFFF9FAFB),
            focusedContainerColor = Color(0xFFF3F4F6),
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
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Main Sport",
                fontSize = 13.sp,
                color = Color.Gray,
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
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                    unfocusedContainerColor = Color(0xFFF9FAFB),
                    focusedContainerColor = Color(0xFFF3F4F6),
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
            "Administración",
            "Antropología",
            "Arquitectura",
            "Arte",
            "Biología",
            "Ciencia Política",
            "Derecho",
            "Diseño",
            "Economía",
            "Filosofía",
            "Física",
            "Geociencias",
            "Gestión Pública y Gobierno",
            "Historia",
            "Historia del Arte",
            "Ingeniería Ambiental",
            "Ingeniería Biomédica",
            "Ingeniería Civil",
            "Ingeniería de Alimentos",
            "Ingeniería de Sistemas y Computación",
            "Ingeniería Eléctrica",
            "Ingeniería Electrónica",
            "Ingeniería Industrial",
            "Ingeniería Mecánica",
            "Ingeniería Química",
            "Lenguas y Cultura",
            "Licenciatura en Artes",
            "Licenciatura en Biología",
            "Licenciatura en Ciencias Sociales",
            "Licenciatura en Educación Infantil",
            "Licenciatura en Física",
            "Licenciatura en Humanidades",
            "Licenciatura en Matemáticas",
            "Licenciatura en Química",
            "Literatura",
            "Matemáticas",
            "Medicina",
            "Microbiología",
            "Música",
            "Narrativas Digitales",
            "Psicología",
            "Química"
        )
    }

    var expanded by remember { mutableStateOf(false) }
    
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
                onValueChange(newValue)
                expanded = newValue.isNotBlank() && filteredPrograms.isNotEmpty()
            },
            label = { Text("Search or enter program", color = Color.Gray, fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color(0xFFF9FAFB),
                focusedContainerColor = Color(0xFFF3F4F6),
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            ),
            enabled = enabled,
            singleLine = true
        )

        if (expanded && filteredPrograms.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
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
