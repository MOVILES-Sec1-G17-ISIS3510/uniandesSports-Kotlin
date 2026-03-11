package com.uniandes.sport.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.uniandes.sport.Routes
import com.uniandes.sport.viewmodels.auth.DummyAuthViewModel
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.uniandes.sport.viewmodels.auth.AuthViewModelInterface
import com.uniandes.sport.viewmodels.log.LogViewModelInterface

@Composable
fun AuthScreen(authViewModel: AuthViewModelInterface,
               navController: NavController,
               logViewModel: LogViewModelInterface) {

    val screenName = "AuthScreen"
    var showDialog = remember { mutableStateOf(false) }
    var dialogMessage = remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        authViewModel.isUserLoggedIn(onSuccess = { isLogged ->
            if (isLogged) {
                navController.navigate(Routes.WALL_SCREEN)
            }
        }, onFailure = { exception ->
            logViewModel.crash(screenName, exception)
        })
    }

    if (showDialog.value) {
        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            text = { Text(dialogMessage.value) },
            confirmButton = {
                Button(
                    onClick = { showDialog.value = false }
                ) {
                    Text("Aceptar")
                }
            }
        )
    }

    var isLoginMode by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        Text(
            text = if (isLoginMode) "Iniciar Sesión" else "Registro",
            style = MaterialTheme.typography.h4,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (!isLoginMode) {
            OutlinedTextField(
                value = authViewModel.fullName,
                onValueChange = { authViewModel.fullName = it },
                label = { Text("Nombre Completo") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )

            OutlinedTextField(
                value = authViewModel.program,
                onValueChange = { authViewModel.program = it },
                label = { Text("Programa (Ej. Ingeniería de Sistemas)") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )

            OutlinedTextField(
                value = authViewModel.semester,
                onValueChange = { authViewModel.semester = it },
                label = { Text("Semestre (Ej. 8)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )

            OutlinedTextField(
                value = authViewModel.mainSport,
                onValueChange = { authViewModel.mainSport = it },
                label = { Text("Deporte Principal (Ej. futbol)") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }

        OutlinedTextField(
            value = authViewModel.email,
            onValueChange = { authViewModel.email = it },
            label = { Text(text = "Email") },
            modifier = Modifier
                .padding(vertical = 4.dp)
                .fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
            )
        )

        OutlinedTextField(
            value = authViewModel.password,
            onValueChange = { authViewModel.password = it },
            label = { Text(text = "Password") },
            modifier = Modifier
                .padding(vertical = 4.dp)
                .fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoginMode) {
            Button(
                onClick = {
                    authViewModel.login(
                        onSuccess = {
                            logViewModel.log(screenName, "USER_LOGGED_IN")
                            navController.navigate(Routes.WALL_SCREEN)
                        },
                        onFailure = { exception ->
                            dialogMessage.value = exception.message.toString()
                            showDialog.value = true
                            logViewModel.crash(screenName, exception)
                        }
                    )
                },
                modifier = Modifier
                    .height(50.dp)
                    .fillMaxWidth()
            ) {
                Text("Ingresar")
            }
        } else {
            Button(
                onClick = {
                    authViewModel.register(
                        onSuccess = {
                            logViewModel.log(screenName, "USER_REGISTERED")
                            navController.navigate(Routes.WALL_SCREEN)
                        },
                        onFailure = { exception ->
                            dialogMessage.value = exception.message.toString()
                            showDialog.value = true
                            logViewModel.crash(screenName, exception)
                        }
                    )
                },
                modifier = Modifier
                    .height(50.dp)
                    .fillMaxWidth()
            ) {
                Text("Crear Cuenta")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = { isLoginMode = !isLoginMode },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoginMode) "¿No tienes cuenta? Regístrate" else "¿Ya tienes cuenta? Ingresa")
        }

        Button(
            onClick = {
                authViewModel.recoverPassword(
                    onSuccess = {
                        logViewModel.log(screenName, "PASSWORD_RECOVERED")
                        dialogMessage.value = "Password recuperado, comprueba tu correo"
                        showDialog.value = true
                    },
                    onFailure = { exception ->
                        dialogMessage.value = exception.message.toString()
                        showDialog.value = true
                        logViewModel.crash(screenName, exception)
                    }
                )
            },
            modifier = Modifier
                .height(60.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            Text("Recuperar contraseña")
        }

        Button(
            onClick = {
                throw RuntimeException("Test Crash")
            },
            modifier = Modifier
                .height(60.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            Text("Test Crashlytics")
        }
    }

}

