package com.uniandes.sport.ui.screens.wallscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.uniandes.sport.models.Tweet
import com.uniandes.sport.models.TweetType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TweetCard(tweet: Tweet) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tweet.userName.ifBlank { "Anonymous athlete" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatTweetTimestamp(tweet.timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )

                if (tweet.type == TweetType.TEXT) {
                    Text(
                        text = tweet.message,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else if (tweet.type == TweetType.IMAGE) {
                    val imageLoader = rememberImagePainter(
                        data = tweet.message,
                        builder = {
                            crossfade(true)
                        }
                    )
                    Image(
                        painter = imageLoader,
                        contentDescription = "Tweet Image",
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 320.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }
}

private fun formatTweetTimestamp(rawTimestamp: String): String {
    return try {
        val parser = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.getDefault())
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy • HH:mm", Locale.getDefault())
        LocalDateTime.parse(rawTimestamp, parser).format(formatter)
    } catch (_: Exception) {
        rawTimestamp
    }
}
