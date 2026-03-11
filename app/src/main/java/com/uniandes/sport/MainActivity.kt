package com.uniandes.sport

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.uniandes.sport.ui.screens.AuthScreen
import com.uniandes.sport.ui.screens.WallScreen
import com.uniandes.sport.viewmodels.auth.DummyAuthViewModel
import com.uniandes.sport.viewmodels.log.DummyLogViewModel
import com.uniandes.sport.viewmodels.log.FirebaseLogViewModel
import com.uniandes.sport.viewmodels.storage.DummyStorageViewModel
import com.uniandes.sport.viewmodels.tweets.DummyTweetsViewModel
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
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
    private lateinit var  firebaseAnalytics: FirebaseAnalytics

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
        firebaseAnalytics = Firebase.analytics
        askNotificationPermission()
        setContent {
            val themeMode = remember { mutableStateOf(ThemeMode.SYSTEM) }
            
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
                                navController.navigate(Routes.MAIN_TABS) {
                                    popUpTo(Routes.AUTH_SCREEN) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(Routes.MAIN_TABS) {
                        MainScaffold(
                            themeMode = themeMode.value,
                            onThemeChange = { themeMode.value = it }
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
}
