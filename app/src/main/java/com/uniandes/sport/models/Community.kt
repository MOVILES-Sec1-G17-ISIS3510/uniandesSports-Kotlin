package com.uniandes.sport.models

data class Community(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val sport: String = "",
    val description: String = "",
    val memberCount: Int = 0,
    val channelCount: Int = 0
)

data class Post(
    val id: String = "",
    val author: String = "",
    val role: String = "",
    val content: String = "",
    val time: String = "",
    val pinned: Boolean = false,
    val likes: Int = 0
)

data class Channel(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val mensajes: Int = 0
)
