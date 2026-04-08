package com.uniandes.sport.models

import com.google.firebase.Timestamp

data class CoachingNotification(
    var id: String = "",
    val targetProfesorId: String = "",
    val title: String = "",
    val message: String = "",
    val requestId: String = "",
    val studentId: String = "",
    val sport: String = "",
    val isRead: Boolean = false,
    val timestamp: Timestamp = Timestamp.now()
)
