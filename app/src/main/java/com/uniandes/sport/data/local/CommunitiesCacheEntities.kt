package com.uniandes.sport.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.uniandes.sport.models.ChannelMessage
import com.uniandes.sport.models.Community
import org.json.JSONObject

@Entity(tableName = "cached_communities")
data class CachedCommunityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val sport: String,
    val description: String,
    val memberCount: Int,
    val channelCount: Int,
    val cachedAt: Long
)

fun Community.toEntity(now: Long = System.currentTimeMillis()): CachedCommunityEntity {
    return CachedCommunityEntity(
        id = id,
        name = name,
        type = type,
        sport = sport,
        description = description,
        memberCount = memberCount,
        channelCount = channelCount,
        cachedAt = now
    )
}

fun CachedCommunityEntity.toModel(): Community {
    return Community(
        id = id,
        name = name,
        type = type,
        sport = sport,
        description = description,
        memberCount = memberCount,
        channelCount = channelCount
    )
}

@Entity(
    tableName = "cached_channel_messages",
    primaryKeys = ["communityId", "channelId", "messageId"]
)
data class CachedChannelMessageEntity(
    val communityId: String,
    val channelId: String,
    val messageId: String,
    val authorId: String,
    val authorName: String,
    val content: String,
    val createdAt: Long,
    val reactionsJson: String,
    val userReactionsJson: String,
    val cachedAt: Long
)

fun ChannelMessage.toEntity(
    communityId: String,
    channelId: String,
    now: Long = System.currentTimeMillis()
): CachedChannelMessageEntity {
    val reactionsObject = JSONObject()
    reactions.forEach { (emoji, count) -> reactionsObject.put(emoji, count) }

    val userReactionsObject = JSONObject()
    userReactions.forEach { (uid, emoji) -> userReactionsObject.put(uid, emoji) }

    return CachedChannelMessageEntity(
        communityId = communityId,
        channelId = channelId,
        messageId = id,
        authorId = authorId,
        authorName = authorName,
        content = content,
        createdAt = createdAt,
        reactionsJson = reactionsObject.toString(),
        userReactionsJson = userReactionsObject.toString(),
        cachedAt = now
    )
}

fun CachedChannelMessageEntity.toModel(): ChannelMessage {
    val reactions = mutableMapOf<String, Long>()
    val reactionsObject = JSONObject(reactionsJson)
    reactionsObject.keys().forEach { key ->
        reactions[key] = reactionsObject.optLong(key, 0L)
    }

    val userReactions = mutableMapOf<String, String>()
    val userReactionsObject = JSONObject(userReactionsJson)
    userReactionsObject.keys().forEach { key ->
        userReactions[key] = userReactionsObject.optString(key)
    }

    return ChannelMessage(
        id = messageId,
        authorId = authorId,
        authorName = authorName,
        content = content,
        createdAt = createdAt,
        reactions = reactions,
        userReactions = userReactions
    )
}
