package com.uniandes.sport.models

import com.google.firebase.Timestamp

data class BookingRequest(
    var id: String = "",
    val userId: String = "", // Matches Existing Firebase structure
    val studentName: String = "", 
    val targetProfesorId: String = "", 
    val targetProfesorName: String = "", // Added for better history tracking
    val sport: String = "",
    val skillLevel: String = "",
    val schedule: String = "", // Matches Existing Firebase structure
    val notes: String = "",
    val status: String = "pending", // Matches your screenshot (lowercase)
    val createdAt: Timestamp = Timestamp.now()
)
