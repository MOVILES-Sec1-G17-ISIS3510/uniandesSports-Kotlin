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
 * Summary counts for challenges the user has participated in.
 * total = completed + inProgress (always).
 */
data class ChallengeStats(
    val total: Int,       // all challenges joined regardless of progress
    val completed: Int,   // progressByUser[uid] >= 100
    val inProgress: Int   // progressByUser[uid] < 100 (or 0 = not started)
)

/**
 * A single open-match / event the user has joined.
 * Shown in the "My Events" section as scrollable cards.
 */
data class EventSummary(
    val id: String,
    val title: String,
    val sport: String,
    val status: String,
    val scheduledAt: Long? = null
)

/** How many events a user has joined, broken down by sport. */
data class SportBreakdownItem(
    val sport: String,
    val count: Int
)
