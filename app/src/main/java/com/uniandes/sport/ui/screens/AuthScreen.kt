package com.uniandes.sport.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.uniandes.sport.R
import com.uniandes.sport.Routes
import com.uniandes.sport.viewmodels.auth.AuthViewModelInterface
import com.uniandes.sport.viewmodels.log.LogViewModelInterface

private fun googleErrorMessage(statusCode: Int): String {
    return when (statusCode) {
        GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Sign in cancelled."
        GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS -> "Sign in already in progress. Try again in a few seconds."
        CommonStatusCodes.NETWORK_ERROR -> "Network error during Google Sign-In. Check your connection."
        CommonStatusCodes.DEVELOPER_ERROR -> "Invalid Google Sign-In config (error 10). Check SHA-1/SHA-256 and Web client ID."
        CommonStatusCodes.INTERNAL_ERROR -> "Internal Google Sign-In error. Try again."
        else -> "Could not sign in with Google (code $statusCode)."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    authViewModel: AuthViewModelInterface,
    navController: NavController,
    logViewModel: LogViewModelInterface,
    onLoginSuccess: (isNewUser: Boolean) -> Unit = { isNewUser -> 
        if (isNewUser) {
            navController.navigate(Routes.ONBOARDING_SCREEN)
        } else {
            navController.navigate(Routes.MAIN_TABS)
        }
    }
) {
    val screenName = "AuthScreen"
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val googleWebClientId = stringResource(id = R.string.google_web_client_id).trim()
    var showDialog by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf("") }
    var isLoginMode by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }

    val googleSignInClient = remember(googleWebClientId) {
        if (googleWebClientId.isBlank()) {
            null
        } else {
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(googleWebClientId)
                .requestEmail()
                .build()
            GoogleSignIn.getClient(context, options)
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)

        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken

            if (idToken.isNullOrBlank()) {
                isGoogleLoading = false
                dialogMessage = "Could not get a valid Google token."
                showDialog = true
                return@rememberLauncherForActivityResult
            }

            authViewModel.loginWithGoogleIdToken(
                idToken = idToken,
                onSuccess = { _, isNewUser ->
                    isGoogleLoading = false
                    logViewModel.log(screenName, "USER_GOOGLE_LOGGED_IN")
                    onLoginSuccess(isNewUser)
                },
                onFailure = { exception ->
                    isGoogleLoading = false
                    dialogMessage = exception.message.toString()
                    showDialog = true
                    logViewModel.crash(screenName, exception)
                }
            )
        } catch (exception: ApiException) {
            isGoogleLoading = false

            if (exception.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                return@rememberLauncherForActivityResult
            }

            dialogMessage = googleErrorMessage(exception.statusCode)
            showDialog = true
            logViewModel.crash(screenName, exception)
        } catch (exception: Exception) {
            isGoogleLoading = false
            dialogMessage = exception.message ?: "No se pudo iniciar sesión con Google."
            showDialog = true
            logViewModel.crash(screenName, exception)
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.isUserLoggedIn(
            onSuccess = { isLogged, isNewUser ->
                if (isLogged) {
                    onLoginSuccess(isNewUser)
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
                    .background(colorScheme.primary)
                    .border(1.dp, colorScheme.onPrimary.copy(alpha = 0.2f), RoundedCornerShape(28.dp)),
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
                color = colorScheme.onBackground,
                modifier = Modifier.padding(top = 10.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))
            
            Text(
                text = if (isLoginMode) "Welcome back!" else "Join UniandesSports",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = colorScheme.onBackground
            )
            
            Text(
                text = if (isLoginMode) "Sign in to continue" else "Create your account now",
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
            )

            // Main Card Container for the Form
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
                    if (!isLoginMode) {
                        CustomOutlinedTextField(
                            value = authViewModel.fullName,
                            onValueChange = { authViewModel.fullName = it },
                            label = "Full Name",
                            icon = Icons.Default.Person
                        )

                        CustomOutlinedTextField(
                            value = authViewModel.program,
                            onValueChange = { authViewModel.program = it },
                            label = "Program (e.g. Systems Engineering)",
                            icon = Icons.Default.School
                        )

                        CustomOutlinedTextField(
                            value = authViewModel.semester,
                            onValueChange = { authViewModel.semester = it },
                            label = "Semester (e.g. 8)",
                            icon = Icons.Default.FormatListNumbered,
                            keyboardType = KeyboardType.Number
                        )

                        CustomOutlinedTextField(
                            value = authViewModel.mainSport,
                            onValueChange = { authViewModel.mainSport = it },
                            label = "Main Sport",
                            icon = Icons.Default.Sports
                        )
                    }

                    CustomOutlinedTextField(
                        value = authViewModel.email,
                        onValueChange = { authViewModel.email = it },
                        label = "Email Address",
                        icon = Icons.Default.Email,
                        keyboardType = KeyboardType.Email
                    )

                    CustomOutlinedTextField(
                        value = authViewModel.password,
                        onValueChange = { authViewModel.password = it },
                        label = "Password",
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
                                    onSuccess = { _, isNewUser ->
                                        logViewModel.log(screenName, "USER_LOGGED_IN")
                                        onLoginSuccess(isNewUser)
                                    },
                                    onFailure = { exception ->
                                        dialogMessage = exception.message.toString()
                                        showDialog = true
                                        logViewModel.crash(screenName, exception)
                                    }
                                )
                            } else {
                                authViewModel.register(
                                    onSuccess = { _ ->
                                        logViewModel.log(screenName, "USER_REGISTERED")
                                        onLoginSuccess(false)
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
                        colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary, contentColor = colorScheme.onPrimary)
                    ) {
                            Text(
                                text = if (isLoginMode) "Sign In" else "Create Account",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isLoginMode) {
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                if (isGoogleLoading) return@OutlinedButton

                                if (googleSignInClient == null) {
                                    dialogMessage = "google_web_client_id is missing in strings.xml."
                                    showDialog = true
                                    return@OutlinedButton
                                }

                                isGoogleLoading = true
                                googleSignInClient.signOut()
                                    .addOnCompleteListener { task ->
                                        if (!task.isSuccessful) {
                                            isGoogleLoading = false
                                            val exception = task.exception ?: Exception("Could not reset Google session.")
                                            dialogMessage = exception.message ?: "Could not reset Google session."
                                            showDialog = true
                                            logViewModel.crash(screenName, exception)
                                            return@addOnCompleteListener
                                        }

                                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                    }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isGoogleLoading,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = colorScheme.surface,
                                contentColor = colorScheme.onSurface
                            )
                        ) {
                            if (isGoogleLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = "Continue with Google",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
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
                    text = if (isLoginMode) "Don't have an account? " else "Already have an account? ",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                Text(
                    text = if (isLoginMode) "Sign Up" else "Sign In",
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

                Text("Forgot your password?", color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)

            // Recover Password Button
            TextButton(
                onClick = {
                    if (authViewModel.email.isBlank()) {
                        dialogMessage = "Please enter your email to recover your password"
                        showDialog = true
                        return@TextButton
                    }
                    authViewModel.recoverPassword(
                        onSuccess = {
                            logViewModel.log(screenName, "PASSWORD_RECOVERED")
                            dialogMessage = "Password recovery link sent to your email"
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
                Text("Forgot your password?", color = Color.Gray, fontWeight = FontWeight.Medium)
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
        label = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) },
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            if (isPassword) {
                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = onPasswordVisibilityChange) {
                    Icon(imageVector = image, contentDescription = "Toggle password visibility", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        ),
        singleLine = true
    )
}

