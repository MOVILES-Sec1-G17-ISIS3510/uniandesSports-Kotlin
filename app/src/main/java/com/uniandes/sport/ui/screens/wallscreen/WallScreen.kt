package com.uniandes.sport.ui.screens.wallscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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
    var refreshKey by remember { mutableStateOf(0) }

    val feedState = fetchTweetsAsState(
        refreshKey = refreshKey,
        tweetsViewModel = tweetsViewModel,
        setErrorMessage = { errorMessage = it },
        setShowErrorDialog = { showErrorDialog = it },
        logViewModel = logViewModel
    )
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("COMMUNITY FEED", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(
                            text = "${feedState.tweets.size} posts and updates",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshKey++ },
                        enabled = !feedState.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh feed"
                        )
                    }
                    TextButton(onClick = {
                        authViewModel.logout(onSuccess = {
                            navController.navigate(Routes.AUTH_SCREEN) {
                                popUpTo(0)
                            }
                        }, onFailure = { exception ->
                            logViewModel.crash(screenName, exception)
                        })
                    }) {
                        Text(text = "Sign Out", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
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
                TweetList(
                    tweets = feedState.tweets,
                    isLoading = feedState.isLoading,
                    modifier = Modifier.fillMaxSize(),
                    listState = listState
                )
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
    refreshKey: Int,
    tweetsViewModel: TweetsViewModelInterface,
    setErrorMessage: (String) -> Unit,
    setShowErrorDialog: (Boolean) -> Unit,
    logViewModel: LogViewModelInterface
): TweetFeedState {
    val screenName = "WallScreen"
    var tweets by remember { mutableStateOf(emptyList<Tweet>()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(refreshKey) {
        isLoading = true
        tweetsViewModel.fetchTweets(
            onSuccess = { fetchedTweets ->
                tweets = fetchedTweets
                isLoading = false
            },
            onFailure = { exception -> 
                setErrorMessage(exception.message ?: "Unknown error")
                setShowErrorDialog(true)
                logViewModel.crash(screenName, exception)
                isLoading = false
            }
        )
    }

    return TweetFeedState(tweets = tweets, isLoading = isLoading)
}

data class TweetFeedState(
    val tweets: List<Tweet>,
    val isLoading: Boolean
)
