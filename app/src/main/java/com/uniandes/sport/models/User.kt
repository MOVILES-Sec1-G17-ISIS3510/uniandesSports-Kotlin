package com.uniandes.sport.models

data class User(
    val uid: String = "",
    val email: String = "",
    val fullName: String = "",
    val program: String = "",
    val semester: Int = 0,
    val role: String = "athlete",
    val mainSport: String = "",
    val inferredPreferences: Map<String, Int> = emptyMap(),
    val photoUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)