package com.uniandes.sport

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniandes.sport.ui.screens.AuthScreen
import com.uniandes.sport.ui.screens.OnboardingScreen
import com.uniandes.sport.ui.screens.SplashScreen
import com.uniandes.sport.ui.screens.wallscreen.WallScreen
import com.uniandes.sport.viewmodels.log.FirebaseLogViewModel
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import android.util.Log
import com.uniandes.sport.viewmodels.auth.FirebaseAuthViewModel
import com.uniandes.sport.viewmodels.storage.FirebaseStorageViewModel
import com.uniandes.sport.viewmodels.tweets.FirestoreTweetsViewModel
import com.google.firebase.messaging.ktx.messaging
import com.uniandes.sport.ui.components.MainScaffold
import com.uniandes.sport.ui.theme.ThemeMode
import com.uniandes.sport.ui.theme.UniandesSportsKotlinTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.MutableState
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private lateinit var themeModeState: MutableState<ThemeMode>

    private val themePrefsName = "app_prefs"
    private val themePrefsKey = "theme_mode"
    private val initialTabState = mutableStateOf(0)
    private val pendingOpenMatchEventIdState = mutableStateOf<String?>(null)
    private val pendingCoachRequestState = mutableStateOf(false)

    companion object {
        const val EXTRA_NOTIFICATION_TYPE = "notification_type"
        const val EXTRA_EVENT_ID = "event_id"
        private const val PLAY_TAB_INDEX = 2
        private const val COACHES_TAB_INDEX = 4
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            Firebase.messaging.subscribeToTopic("all")
                .addOnCompleteListener { task ->
                    var msg = "Subscribed"
                    if (!task.isSuccessful) {
                        msg = "Subscribe failed"
                    }
                    Log.d("FCM", msg)
                }
        } else {
            // TODO: Inform user that that your app will not show notifications.
        }
    }

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parseNotificationIntent(intent)
        firebaseAnalytics = Firebase.analytics
        askNotificationPermission()
        syncCurrentUserFcmToken()
        val initialThemeMode = loadThemeMode()
        themeModeState = mutableStateOf(initialThemeMode)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        setContent {
            val themeMode = themeModeState.value
            
            UniandesSportsKotlinTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val authViewModel: FirebaseAuthViewModel = viewModel()
                val tweetsViewModel: FirestoreTweetsViewModel = viewModel()
                val storageViewModel: FirebaseStorageViewModel = viewModel()
                val logViewModel: FirebaseLogViewModel = viewModel()

                NavHost(navController = navController, startDestination = Routes.SPLASH_SCREEN){
                    composable(Routes.SPLASH_SCREEN) {
                        SplashScreen(
                            navController = navController,
                            authViewModel = authViewModel
                        )
                    }
                    composable(Routes.AUTH_SCREEN) {
                        AuthScreen(
                            navController = navController,
                            authViewModel = authViewModel,
                            logViewModel = logViewModel,
                            themeMode = themeMode,
                            onThemeChange = {
                                themeModeState.value = it
                                saveThemeMode(it)
                            },
                            onLoginSuccess = { isNewUser ->
                                // Ensure token is saved for the authenticated user immediately after login/register.
                                syncCurrentUserFcmToken()
                                val dest = if (isNewUser) Routes.ONBOARDING_SCREEN else Routes.MAIN_TABS
                                navController.navigate(dest) {
                                    popUpTo(Routes.AUTH_SCREEN) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(Routes.ONBOARDING_SCREEN) {
                        OnboardingScreen(
                            authViewModel = authViewModel,
                            logViewModel = logViewModel,
                            themeMode = themeMode,
                            onThemeChange = {
                                themeModeState.value = it
                                saveThemeMode(it)
                            },
                            onFinishOnboarding = {
                                navController.navigate(Routes.MAIN_TABS) {
                                    popUpTo(Routes.ONBOARDING_SCREEN) { inclusive = true }
                                }
                            },
                            onBackToLogin = {
                                authViewModel.logout(
                                    onSuccess = {
                                        navController.navigate(Routes.AUTH_SCREEN) {
                                            popUpTo(Routes.ONBOARDING_SCREEN) { inclusive = true }
                                        }
                                    },
                                    onFailure = {
                                        navController.navigate(Routes.AUTH_SCREEN) {
                                            popUpTo(Routes.ONBOARDING_SCREEN) { inclusive = true }
                                        }
                                    }
                                )
                            }
                        )
                    }
                    composable(Routes.MAIN_TABS) {
                        MainScaffold(
                            initialTabIndex = initialTabState.value,
                            pendingOpenMatchEventId = pendingOpenMatchEventIdState.value,
                            onOpenMatchConsumed = { pendingOpenMatchEventIdState.value = null },
                            pendingCoachRequest = pendingCoachRequestState.value,
                            onCoachRequestConsumed = { pendingCoachRequestState.value = false },
                            themeMode = themeModeState.value,
                            onThemeChange = {
                                themeModeState.value = it
                                saveThemeMode(it)
                            },
                            onExitApp = { finishAffinity() }
                        )
                    }
                    // WallScreen logic unmodified
                    composable(Routes.WALL_SCREEN) {
                        WallScreen(
                            tweetsViewModel = tweetsViewModel,
                            authViewModel = authViewModel,
                            navController = navController,
                            storageViewModel = storageViewModel,
                            logViewModel = logViewModel
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseNotificationIntent(intent)
    }

    private fun loadThemeMode(): ThemeMode {
        val prefs = getSharedPreferences(themePrefsName, MODE_PRIVATE)
        val raw = prefs.getString(themePrefsKey, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(raw)
        } catch (_: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    private fun saveThemeMode(themeMode: ThemeMode) {
        val prefs = getSharedPreferences(themePrefsName, MODE_PRIVATE)
        prefs.edit().putString(themePrefsKey, themeMode.name).apply()
    }

    private fun syncCurrentUserFcmToken() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        Firebase.messaging.token
            .addOnSuccessListener { token ->
                if (token.isBlank()) return@addOnSuccessListener
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .set(
                        mapOf(
                            "fcmToken" to token,
                            "fcmTokens" to FieldValue.arrayUnion(token)
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
            }
            .addOnFailureListener { e ->
                Log.e("FCM", "Failed to sync current user token", e)
            }
    }

    private fun parseNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val notificationType = intent.getStringExtra(EXTRA_NOTIFICATION_TYPE) ?: return

        if (notificationType == "open_match") {
            val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
            if (!eventId.isNullOrBlank()) {
                initialTabState.value = PLAY_TAB_INDEX
                pendingOpenMatchEventIdState.value = eventId
            }
        } else if (notificationType == "coach_request") {
            // Redirigir a la pestaña de Profesores (Dashboard del Coach)
            initialTabState.value = COACHES_TAB_INDEX
            pendingCoachRequestState.value = true
            Log.d("FCM_NAV", "Coach request received, triggering navigation")
        }
    }
    override fun onResume() {
        super.onResume()
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val lux = event.values[0]
            val currentMode = loadThemeMode() // Check the stored preference
            
            // Only apply sensor logic if the user has selected AUTO mode
            if (currentMode == ThemeMode.AUTO) {
                // Hysteresis logic to prevent flickering
                // Thresholds: < 10 lux for DARK, > 25 lux for LIGHT
                if (lux < 10f && themeModeState.value != ThemeMode.DARK) {
                    themeModeState.value = ThemeMode.DARK
                } else if (lux > 25f && themeModeState.value != ThemeMode.LIGHT) {
                    themeModeState.value = ThemeMode.LIGHT
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
}

