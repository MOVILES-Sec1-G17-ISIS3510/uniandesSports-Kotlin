package com.uniandes.sport.models

import com.google.firebase.Timestamp

/**
 * Representa un registro de progreso (Track) de un usuario en un evento deportivo.
 * Anteriormente conocido como "Review".
 */
data class Track(
    val eventId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val sport: String = "",
    val scheduledAt: Timestamp? = null,
    val text: String = "", // Activity Details / Progress Log
    val rating: Int = 0, // Activity effort/satisfaction (1-5)
    val participated: Boolean = false, // Reemplaza attendanceByUserId
    val source: String = "text",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val aiAnalysis: Map<String, Double> = emptyMap() // Map of challengeId to incremental %
)
