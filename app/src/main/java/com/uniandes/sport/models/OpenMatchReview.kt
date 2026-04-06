package com.uniandes.sport.models

import com.google.firebase.Timestamp

data class OpenMatchReview(
    val eventId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val text: String = "",
    val source: String = "text",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
