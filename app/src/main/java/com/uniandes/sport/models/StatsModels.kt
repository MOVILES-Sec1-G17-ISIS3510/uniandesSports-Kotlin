package com.uniandes.sport.models

/**
 * Lightweight data-transfer objects for the MyStats feature.
 *
 * These are NOT Room entities — pure Kotlin data classes that carry
 * data between the Repository layer and the ViewModel / UI layers.
 *
 * @author Juan Felipe Hernández
 * @since 26-may-2026
 */

/** A single run session represented as a chart data point. */
data class RunDataPoint(
    val distanceKm: Float,
    val timestamp: Long,
    val pace: String = ""
)

/**
 * A challenge the user is actively participating in,
 * with their personal progress in the 0–100 scale.
 */
data class ActiveChallengeData(
    val id: String,
    val title: String,
    val sport: String,
    val goalLabel: String,
    val userProgress: Double,   // 0–100 (same scale as Reto.progressByUser)
    val endDate: Long? = null   // nullable: some challenges have no end date
)

/** How many events a user has joined, broken down by sport. */
data class SportBreakdownItem(
    val sport: String,
    val count: Int
)
