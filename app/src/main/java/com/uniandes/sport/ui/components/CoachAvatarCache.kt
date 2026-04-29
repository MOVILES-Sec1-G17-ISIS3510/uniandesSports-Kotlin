package com.uniandes.sport.ui.components

import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.uniandes.sport.models.Profesor
import java.net.URLEncoder

data class CoachAvatarPayload(
    val imageUrl: String,
    val initials: String,
    val sport: String
)

object CoachAvatarMemoryCache {
    private const val MAX_ENTRIES = 48

    /**
     * LRU cache keyed by a stable coach signature.
     *
     * Decision:
     * - We cache only lightweight avatar metadata, not raw bitmaps.
     * - `MAX_ENTRIES = 48` is enough for the current coaches list plus profile revisits.
     * - `sizeOf = 1` makes eviction entry-based, which keeps behavior predictable.
     */
    private val cache = object : LruCache<String, CoachAvatarPayload>(MAX_ENTRIES) {
        override fun sizeOf(key: String, value: CoachAvatarPayload): Int = 1
    }

    fun getOrCreate(profesor: Profesor): CoachAvatarPayload {
        val cacheKey = buildString {
            append(profesor.id.ifBlank { profesor.nombre.trim() })
            append("|")
            append(profesor.nombre.trim())
            append("|")
            append(profesor.deporte.trim().lowercase())
            append("|")
            append(profesor.photoUrl.trim())
        }

        return cache.get(cacheKey) ?: buildPayload(profesor).also { payload ->
            cache.put(cacheKey, payload)
        }
    }

    fun currentSize(): Int = cache.size()

    private fun buildPayload(profesor: Profesor): CoachAvatarPayload {
        val displayName = profesor.nombre.ifBlank { "Coach" }
        val encodedName = URLEncoder.encode(displayName, "UTF-8").replace("+", "%20")
        val background = sportBackgroundHex(profesor.deporte)

        return CoachAvatarPayload(
            imageUrl = profesor.photoUrl.ifBlank {
                "https://ui-avatars.com/api/?name=$encodedName&background=$background&color=ffffff&size=256&bold=true&format=png"
            },
            initials = displayName.split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
                .ifBlank { "C" },
            sport = profesor.deporte
        )
    }

    private fun sportBackgroundHex(sport: String): String {
        return when (sport.lowercase()) {
            "soccer", "fútbol", "futbol", "football" -> "2ECC71"
            "basketball", "baloncesto" -> "E67E22"
            "tennis", "tenis" -> "F1C40F"
            "running", "correr" -> "E74C3C"
            "swimming", "natación", "natacion" -> "3498DB"
            "calisthenics", "calistenia", "calistennics" -> "9B59B6"
            else -> "95A5A6"
        }
    }
}

@Composable
fun CoachAvatar(
    profesor: Profesor,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val payload = remember(profesor.id, profesor.nombre, profesor.deporte) {
        CoachAvatarMemoryCache.getOrCreate(profesor)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(getSportAccentColor(profesor.deporte).copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(payload.imageUrl)
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = "Avatar de ${profesor.nombre}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = {
                InitialsAvatar(
                    name = profesor.nombre.ifBlank { payload.initials },
                    size = size,
                    modifier = Modifier.fillMaxSize()
                )
            },
            error = {
                SportIconBox(
                    sport = payload.sport,
                    size = size,
                    modifier = Modifier.fillMaxSize()
                )
            },
            success = { SubcomposeAsyncImageContent() }
        )
    }
}
