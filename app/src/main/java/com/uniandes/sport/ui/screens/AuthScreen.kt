package com.uniandes.sport.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.uniandes.sport.R
import com.uniandes.sport.Routes
import com.uniandes.sport.viewmodels.auth.AuthViewModelInterface
import com.uniandes.sport.viewmodels.log.LogViewModelInterface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    authViewModel: AuthViewModelInterface,
    navController: NavController,
    logViewModel: LogViewModelInterface,
    onLoginSuccess: () -> Unit = { navController.navigate(Routes.MAIN_TABS) }
) {
    val screenName = "AuthScreen"
    var showDialog by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf("") }
    var isLoginMode by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        authViewModel.isUserLoggedIn(
            onSuccess = { isLogged ->
                if (isLogged) {
                    onLoginSuccess()
                }
            },
            onFailure = { exception ->
                logViewModel.crash(screenName, exception)
            }
        )
    }

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
            .background(Color(0xFFF9FAFB)) // Light gray background matching the app
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            Spacer(modifier = Modifier.height(32.dp))

            // App Header/Logo Area
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(28.dp), clip = false)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF0046B8))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.usports_logo),
                    contentDescription = "App Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Text(
                text = "USports",
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                color = Color(0xFF2C3138),
                modifier = Modifier.padding(top = 10.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))
            
            Text(
                text = if (isLoginMode) "¡Bienvenido de vuelta!" else "Únete a UniandesSports",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black
            )
            
            Text(
                text = if (isLoginMode) "Ingresa para continuar" else "Crea tu cuenta ahora",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
            )

            // Main Card Container for the Form
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
                    if (!isLoginMode) {
                        CustomOutlinedTextField(
                            value = authViewModel.fullName,
                            onValueChange = { authViewModel.fullName = it },
                            label = "Nombre Completo",
                            icon = Icons.Default.Person
                        )

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
                    }

                    CustomOutlinedTextField(
                        value = authViewModel.email,
                        onValueChange = { authViewModel.email = it },
                        label = "Correo Electrónico",
                        icon = Icons.Default.Email,
                        keyboardType = KeyboardType.Email
                    )

                    CustomOutlinedTextField(
                        value = authViewModel.password,
                        onValueChange = { authViewModel.password = it },
                        label = "Contraseña",
                        icon = Icons.Default.Lock,
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        onPasswordVisibilityChange = { passwordVisible = !passwordVisible }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Main Action Button
                    Button(
                        onClick = {
                            if (isLoginMode) {
                                authViewModel.login(
                                    onSuccess = {
                                        logViewModel.log(screenName, "USER_LOGGED_IN")
                                        onLoginSuccess()
                                    },
                                    onFailure = { exception ->
                                        dialogMessage = exception.message.toString()
                                        showDialog = true
                                        logViewModel.crash(screenName, exception)
                                    }
                                )
                            } else {
                                authViewModel.register(
                                    onSuccess = {
                                        logViewModel.log(screenName, "USER_REGISTERED")
                                        onLoginSuccess()
                                    },
                                    onFailure = { exception ->
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = if (isLoginMode) "Ingresar" else "Crear Cuenta",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Switch Mode Text
            Row(
                modifier = Modifier.clickable { isLoginMode = !isLoginMode },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isLoginMode) "¿No tienes cuenta? " else "¿Ya tienes cuenta? ",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Text(
                    text = if (isLoginMode) "Regístrate" else "Ingresa aquí",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recover Password Button
            TextButton(
                onClick = {
                    if (authViewModel.email.isBlank()) {
                        dialogMessage = "Por favor ingresa tu correo electrónico para recuperar la contraseña"
                        showDialog = true
                        return@TextButton
                    }
                    authViewModel.recoverPassword(
                        onSuccess = {
                            logViewModel.log(screenName, "PASSWORD_RECOVERED")
                            dialogMessage = "Contraseña de recuperación enviada a tu correo"
                            showDialog = true
                        },
                        onFailure = { exception ->
                            dialogMessage = exception.message.toString()
                            showDialog = true
                            logViewModel.crash(screenName, exception)
                        }
                    )
                }
            ) {
                Text("¿Olvidaste tu contraseña?", color = Color.Gray, fontWeight = FontWeight.Medium)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordVisibilityChange: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.Gray, fontSize = 13.sp) },
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            if (isPassword) {
                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = onPasswordVisibilityChange) {
                    Icon(imageVector = image, contentDescription = "Toggle password visibility", tint = Color.Gray)
                }
            }
        },
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color(0xFFF9FAFB),
            focusedContainerColor = Color(0xFFF3F4F6),
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        ),
        singleLine = true
    )
}

