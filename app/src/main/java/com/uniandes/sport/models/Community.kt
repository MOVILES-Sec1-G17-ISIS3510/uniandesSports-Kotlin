package com.uniandes.sport.models

data class Community(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val sport: String = "",
    val description: String = "",
    val memberCount: Int = 0,
    val channelCount: Int = 0,
    val ownerId: String = ""
)

data class Post(
    val id: String = "",
    val author: String = "",
    val role: String = "",
    val content: String = "",
    val time: String = "",
    val pinned: Boolean = false,
    val likes: Int = 0,
    val createdAt: Long = 0L
)

data class Channel(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val mensajes: Int = 0
)

data class CommunityMember(
    val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val role: String = "member",
    val joinedAt: Long = 0L
)

data class ChannelMessage(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val content: String = "",
    val createdAt: Long = 0L,
    val reactions: Map<String, Long> = emptyMap(),
    val userReactions: Map<String, String> = emptyMap()
)

data class PostComment(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val content: String = "",
    val createdAt: Long = 0L
)
