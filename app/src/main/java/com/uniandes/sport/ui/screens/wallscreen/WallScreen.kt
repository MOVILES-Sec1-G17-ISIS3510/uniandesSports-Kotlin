package com.uniandes.sport.ui.screens.wallscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.uniandes.sport.Routes
import com.uniandes.sport.models.Tweet
import com.uniandes.sport.viewmodels.tweets.TweetsViewModelInterface
import com.uniandes.sport.viewmodels.storage.StorageViewModelInterface
import com.uniandes.sport.viewmodels.auth.AuthViewModelInterface
import com.uniandes.sport.viewmodels.log.LogViewModelInterface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallScreen(
    tweetsViewModel: TweetsViewModelInterface,
    authViewModel: AuthViewModelInterface,
    storageViewModel: StorageViewModelInterface,
    navController: NavController,
    logViewModel: LogViewModelInterface
) {
    val screenName = "WallScreen"
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Simplificación para el estado de los tweets (MVVM)
    val tweets = fetchTweetsAsState(tweetsViewModel, { errorMessage = it }, { showErrorDialog = it }, logViewModel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("COMUNIDAD", fontWeight = FontWeight.Black, fontSize = 18.sp) },
                actions = {
                    TextButton(onClick = {
                        authViewModel.logout(onSuccess = {
                            navController.navigate(Routes.AUTH_SCREEN) {
                                popUpTo(0)
                            }
                        }, onFailure = { exception ->
                            logViewModel.crash(screenName, exception)
                        })
                    }) {
                        Text(text = "Cerrar Sesión", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        containerColor = Color(0xFFF9FAFB)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TweetList(tweets, modifier = Modifier.fillMaxSize())
            }
            
            Divider(color = Color(0xFFE5E7EB))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(bottom = 8.dp)
            ) {
                TweetForm(
                    tweetsViewModel,
                    authViewModel,
                    storageViewModel,
                    logViewModel,
                    { errorMessage = it },
                    { showErrorDialog = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text(text = "Error") },
            text = { Text(text = errorMessage) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun fetchTweetsAsState(
    tweetsViewModel: TweetsViewModelInterface,
    setErrorMessage: (String) -> Unit,
    setShowErrorDialog: (Boolean) -> Unit,
    logViewModel: LogViewModelInterface
): List<Tweet> {
    val screenName = "WallScreen"
    var tweets by remember { mutableStateOf(emptyList<Tweet>()) }

    LaunchedEffect(Unit) {
        tweetsViewModel.fetchTweets(
            onSuccess = { fetchedTweets -> tweets = fetchedTweets },
            onFailure = { exception -> 
                setErrorMessage(exception.message ?: "Unknown error")
                setShowErrorDialog(true)
                logViewModel.crash(screenName, exception)
            }
        )
    }

    return tweets
}
