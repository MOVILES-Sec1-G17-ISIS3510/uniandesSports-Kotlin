package com.uniandes.sport.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.viewmodels.auth.AuthViewModelInterface
import com.uniandes.sport.viewmodels.log.LogViewModelInterface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    authViewModel: AuthViewModelInterface,
    logViewModel: LogViewModelInterface,
    onFinishOnboarding: () -> Unit
) {
    val screenName = "OnboardingScreen"
    var showDialog by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = "Aviso", fontWeight = FontWeight.Bold) },
            text = { Text(dialogMessage) },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Aceptar")
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
                text = "Casi listos...",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black
            )
            
            Text(
                text = "Completa estos datos para mejorar tu experiencia",
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
                    CustomOutlinedTextField(
                        value = authViewModel.program,
                        onValueChange = { authViewModel.program = it },
                        label = "Programa (Ej. Ing. de Sistemas)",
                        icon = Icons.Default.School
                    )

                    CustomOutlinedTextField(
                        value = authViewModel.semester,
                        onValueChange = { authViewModel.semester = it },
                        label = "Semestre (Ej. 8)",
                        icon = Icons.Default.FormatListNumbered,
                        keyboardType = KeyboardType.Number
                    )

                    CustomOutlinedTextField(
                        value = authViewModel.mainSport,
                        onValueChange = { authViewModel.mainSport = it },
                        label = "Deporte Principal",
                        icon = Icons.Default.Sports
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (authViewModel.program.isBlank() || authViewModel.semester.isBlank() || authViewModel.mainSport.isBlank()) {
                                dialogMessage = "Por favor completa todos los campos"
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
                                text = "Finalizar Registro",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
