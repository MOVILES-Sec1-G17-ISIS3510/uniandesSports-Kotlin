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
import com.uniandes.sport.ui.screens.AuthScreen
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

class MainActivity : ComponentActivity() {
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    private val themePrefsName = "app_prefs"
    private val themePrefsKey = "theme_mode"
    private val initialTabState = mutableStateOf(0)
    private val pendingOpenMatchEventIdState = mutableStateOf<String?>(null)

    companion object {
        const val EXTRA_NOTIFICATION_TYPE = "notification_type"
        const val EXTRA_EVENT_ID = "event_id"
        private const val PLAY_TAB_INDEX = 2
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
        setContent {
            val themeMode = remember { mutableStateOf(initialThemeMode) }
            
            UniandesSportsKotlinTheme(themeMode = themeMode.value) {
                val navController = rememberNavController()
                val authViewModel = FirebaseAuthViewModel()
                val tweetsViewModel = FirestoreTweetsViewModel()
                val storageViewModel = FirebaseStorageViewModel()
                val logViewModel = FirebaseLogViewModel()

                NavHost(navController = navController, startDestination = Routes.AUTH_SCREEN){
                    composable(Routes.AUTH_SCREEN) {
                        AuthScreen(
                            navController = navController,
                            authViewModel = authViewModel,
                            logViewModel = logViewModel,
                            onLoginSuccess = {
                                // Ensure token is saved for the authenticated user immediately after login/register.
                                syncCurrentUserFcmToken()
                                navController.navigate(Routes.MAIN_TABS) {
                                    popUpTo(Routes.AUTH_SCREEN) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(Routes.MAIN_TABS) {
                        MainScaffold(
                            initialTabIndex = initialTabState.value,
                            pendingOpenMatchEventId = pendingOpenMatchEventIdState.value,
                            onOpenMatchConsumed = { pendingOpenMatchEventIdState.value = null },
                            themeMode = themeMode.value,
                            onThemeChange = {
                                themeMode.value = it
                                saveThemeMode(it)
                            }
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
        }
    }
}
