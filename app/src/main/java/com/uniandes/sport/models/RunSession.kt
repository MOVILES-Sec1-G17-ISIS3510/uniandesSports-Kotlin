package com.uniandes.sport.models

data class RunSession(
    val id: String = "",
    val userId: String = "",
    val distanceKm: Float = 0f,
    val pace: String = "",
    val elevationGain: Float = 0f,
    val cadence: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val aiFeedback: String = ""
)
