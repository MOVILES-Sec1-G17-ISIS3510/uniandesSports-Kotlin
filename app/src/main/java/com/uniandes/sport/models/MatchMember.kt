package com.uniandes.sport.models

data class MatchMember(
    val userId: String = "",
    val displayName: String = "",
    val joinedAt: Long = 0,
    val role: String = "member"
)
