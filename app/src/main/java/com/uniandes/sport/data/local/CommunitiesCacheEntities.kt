package com.uniandes.sport.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.uniandes.sport.models.Channel
import com.uniandes.sport.models.ChannelMessage
import com.uniandes.sport.models.Community
import com.uniandes.sport.models.CommunityMember
import com.uniandes.sport.models.MessageStatus
import com.uniandes.sport.models.Post
import com.uniandes.sport.models.PostComment
import org.json.JSONObject

// ─── Community ───────────────────────────────────────────────────────────────

@Entity(tableName = "cached_communities")
data class CachedCommunityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val sport: String,
    val description: String,
    val memberCount: Int,
    val channelCount: Int,
    val ownerId: String,
    val cachedAt: Long
)

fun Community.toEntity(now: Long = System.currentTimeMillis()): CachedCommunityEntity =
    CachedCommunityEntity(id, name, type, sport, description, memberCount, channelCount, ownerId, now)

fun CachedCommunityEntity.toModel(): Community =
    Community(id, name, type, sport, description, memberCount, channelCount, ownerId)

// ─── Post ────────────────────────────────────────────────────────────────────

@Entity(tableName = "cached_posts", primaryKeys = ["communityId", "postId"])
data class CachedPostEntity(
    val communityId: String,
    val postId: String,
    val author: String,
    val role: String,
    val content: String,
    val time: String,
    val pinned: Boolean,
    val likes: Int,
    val createdAt: Long,
    val cachedAt: Long
)

fun Post.toEntity(communityId: String, now: Long = System.currentTimeMillis()): CachedPostEntity =
    CachedPostEntity(communityId, id, author, role, content, time, pinned, likes, createdAt, now)

fun CachedPostEntity.toModel(): Post =
    Post(postId, author, role, content, time, pinned, likes, createdAt)

// ─── Channel ──────────────────────────────────────────────────────────────────

@Entity(tableName = "cached_channels", primaryKeys = ["communityId", "channelId"])
data class CachedChannelEntity(
    val communityId: String,
    val channelId: String,
    val name: String,
    val type: String,
    val mensajes: Int,
    val cachedAt: Long
)

fun Channel.toEntity(communityId: String, now: Long = System.currentTimeMillis()): CachedChannelEntity =
    CachedChannelEntity(communityId, id, name, type, mensajes, now)

fun CachedChannelEntity.toModel(): Channel =
    Channel(channelId, name, type, mensajes)

// ─── Member ───────────────────────────────────────────────────────────────────

@Entity(tableName = "cached_members", primaryKeys = ["communityId", "userId"])
data class CachedMemberEntity(
    val communityId: String,
    val userId: String,
    val memberId: String,
    val displayName: String,
    val role: String,
    val joinedAt: Long,
    val cachedAt: Long
)

fun CommunityMember.toEntity(communityId: String, now: Long = System.currentTimeMillis()): CachedMemberEntity =
    CachedMemberEntity(communityId, userId, id, displayName, role, joinedAt, now)

fun CachedMemberEntity.toModel(): CommunityMember =
    CommunityMember(memberId, userId, displayName, role, joinedAt)

// ─── PostComment ──────────────────────────────────────────────────────────────

@Entity(tableName = "cached_post_comments", primaryKeys = ["communityId", "postId", "commentId"])
data class CachedPostCommentEntity(
    val communityId: String,
    val postId: String,
    val commentId: String,
    val authorId: String,
    val authorName: String,
    val content: String,
    val createdAt: Long,
    val cachedAt: Long
)

fun PostComment.toEntity(communityId: String, postId: String, now: Long = System.currentTimeMillis()): CachedPostCommentEntity =
    CachedPostCommentEntity(communityId, postId, id, authorId, authorName, content, createdAt, now)

fun CachedPostCommentEntity.toModel(): PostComment =
    PostComment(commentId, authorId, authorName, content, createdAt)

// ─── User Membership ──────────────────────────────────────────────────────────

@Entity(tableName = "cached_memberships", primaryKeys = ["userId", "communityId"])
data class CachedMembershipEntity(
    val userId: String,
    val communityId: String,
    val cachedAt: Long
)

// ─── ChannelMessage ───────────────────────────────────────────────────────────

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
    val status: String,
    val cachedAt: Long
)

@Entity(tableName = "pending_messages")
data class PendingMessageEntity(
    @PrimaryKey val localId: String,
    val communityId: String,
    val channelId: String,
    val authorId: String,
    val authorName: String,
    val content: String,
    val createdAt: Long,
    val retryCount: Int = 0
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
        status = status.name,
        cachedAt = now
    )
}

fun CachedChannelMessageEntity.toModel(): ChannelMessage {
    val reactions = mutableMapOf<String, Long>()
    val reactionsObject = JSONObject(reactionsJson)
    reactionsObject.keys().forEach { key -> reactions[key] = reactionsObject.optLong(key, 0L) }

    val userReactions = mutableMapOf<String, String>()
    val userReactionsObject = JSONObject(userReactionsJson)
    userReactionsObject.keys().forEach { key -> userReactions[key] = userReactionsObject.optString(key) }

    return ChannelMessage(
        id = messageId,
        authorId = authorId,
        authorName = authorName,
        content = content,
        createdAt = createdAt,
        reactions = reactions,
        userReactions = userReactions,
        status = MessageStatus.valueOf(status)
    )
}
