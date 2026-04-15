package com.uniandes.sport.ui.screens.wallscreen

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.uniandes.sport.models.Tweet
import com.uniandes.sport.models.TweetType
import com.uniandes.sport.viewmodels.auth.AuthViewModelInterface
import com.uniandes.sport.viewmodels.log.LogViewModelInterface
import com.uniandes.sport.viewmodels.storage.StorageViewModelInterface
import com.uniandes.sport.viewmodels.tweets.TweetsViewModelInterface
import java.text.SimpleDateFormat
import java.util.*


@Composable
fun TweetForm(
    tweetsViewModel: TweetsViewModelInterface,
    authViewModel: AuthViewModelInterface,
    storageViewModel: StorageViewModelInterface,
    logViewModel: LogViewModelInterface,
    setErrorMessage: (String) -> Unit,
    setShowErrorDialog: (Boolean) -> Unit,
    modifier: Modifier
) {
    val screenName = "TweetForm"
    val (tweetText, setTweetText) = remember { mutableStateOf("") }
    val maxLength = 240

    val takePictureLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            sendTweet(
                bitmap = bitmap,
                tweetType = TweetType.IMAGE,
                tweetsViewModel = tweetsViewModel,
                authViewModel = authViewModel,
                storageViewModel = storageViewModel,
                logViewModel = logViewModel,
                setErrorMessage = setErrorMessage,
                setShowErrorDialog = setShowErrorDialog,
                setTweetText = setTweetText
            )
        } else {
            val errorMessage = "Error taking picture.";
            setErrorMessage(errorMessage)
            setShowErrorDialog(true)
            logViewModel.crash(screenName, Exception(errorMessage))
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            takePictureLauncher.launch(null)
        } else {
            val errorMessage = "Permission for camera is denied.";
            setErrorMessage(errorMessage)
            setShowErrorDialog(true)
            logViewModel.crash(screenName, Exception(errorMessage))
        }
    }

    Column(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .imePadding()
    ) {
        OutlinedTextField(
            value = tweetText,
            onValueChange = { if (it.length <= maxLength) setTweetText(it) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 110.dp, max = 140.dp),
            label = { Text(text = "Share something with the community") },
            placeholder = { Text("How was your workout today?") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {}),
            maxLines = 5,
            shape = RoundedCornerShape(14.dp),
            singleLine = false
        )

        Text(
            text = "${tweetText.length}/$maxLength",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Image button",
                        modifier = Modifier.size(24.dp)
                    )
                    Text("Photo", modifier = Modifier.padding(start = 6.dp))
                }
            }

            Button(
                onClick = {
                    sendTweet(
                        tweetText = tweetText,
                        tweetType = TweetType.TEXT,
                        tweetsViewModel = tweetsViewModel,
                        authViewModel = authViewModel,
                        storageViewModel = storageViewModel,
                        logViewModel = logViewModel,
                        setErrorMessage = setErrorMessage,
                        setShowErrorDialog = setShowErrorDialog,
                        setTweetText = setTweetText
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = tweetText.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Send tweet button",
                        modifier = Modifier.size(24.dp)
                    )
                    Text("Post", modifier = Modifier.padding(start = 6.dp), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun sendTweet(
    tweetText: String? = null,
    bitmap: Bitmap? = null,
    tweetType: TweetType,
    tweetsViewModel: TweetsViewModelInterface,
    authViewModel: AuthViewModelInterface,
    storageViewModel: StorageViewModelInterface,
    logViewModel: LogViewModelInterface,
    setErrorMessage: (String) -> Unit,
    setShowErrorDialog: (Boolean) -> Unit,
    setTweetText: (String) -> Unit
) {
    val screenName = "sendTweet"

    authViewModel.getUser(
        onSuccess = { user ->
            val id = UUID.randomUUID().toString()

            val calendar = Calendar.getInstance()
            val formatter = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
            val formattedDateTime = formatter.format(calendar.time)

            when (tweetType) {
                TweetType.TEXT -> {

                    val tweet = Tweet(
                        id = id,
                        userName = user.email,
                        type = tweetType,
                        message = if(tweetText.isNullOrEmpty()) " " else tweetText,
                        timestamp = formattedDateTime
                    )

                    tweetsViewModel.postTweet(
                        tweet = tweet,
                        onSuccess = {
                            setTweetText("")
                            logViewModel.log(screenName, "TWEET_MESSAGE_POSTED")
                        },
                        onFailure = { exception ->
                            setErrorMessage(exception.message ?: "Unknown error")
                            setShowErrorDialog(true)
                            logViewModel.crash(screenName, exception)
                        }
                    )
                }
                TweetType.IMAGE -> {

                    if(bitmap != null) {

                        storageViewModel.uploadImage(
                            bitmap,
                            onSuccess = { uri ->
                                val tweet = Tweet(
                                    id = id,
                                    userName = user.email,
                                    type = tweetType,
                                    message = uri,
                                    timestamp = formattedDateTime
                                )
                                tweetsViewModel.postTweet(
                                    tweet = tweet,
                                    onSuccess = {
                                        setTweetText("")
                                        logViewModel.log(screenName, "TWEET_IMAGE_POSTED")
                                    },
                                    onFailure = { exception ->
                                        setErrorMessage(exception.message ?: "Unknown error")
                                        setShowErrorDialog(true)
                                        logViewModel.crash(screenName, exception)
                                    }
                                )
                            },
                            onFailure = { exception ->
                                setErrorMessage(exception.message ?: "Unknown error")
                                setShowErrorDialog(true)
                                logViewModel.crash(screenName, exception)
                            }
                         )
                    }
                }
            }

        },
        onFailure = { exception ->
            setErrorMessage(exception.message ?: "Unknown error")
            setShowErrorDialog(true)
            logViewModel.crash(screenName, exception)
        }
    )
}
