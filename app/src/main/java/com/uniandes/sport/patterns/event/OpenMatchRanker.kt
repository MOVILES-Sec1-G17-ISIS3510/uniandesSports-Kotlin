package com.uniandes.sport.patterns.event

import android.location.Location
import com.uniandes.sport.models.Event
import java.util.Calendar
import java.util.Date
import kotlin.math.max
import kotlin.math.min

private val locationRegex = Regex("""Lat:\s*(-?\d+(?:\.\d+)?),\s*Lng:\s*(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

data class RankedOpenMatch(
    val event: Event,
    val score: Double,
    val reasons: List<String>
)

object OpenMatchRanker {
    fun rank(
        openEvents: List<Event>,
        joinedEvents: List<Event>,
        preferredSports: Set<String>,
        currentLocation: Location? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): List<RankedOpenMatch> {
        val now = Date(nowMillis)
        val currentWeekJoinedEvents = joinedEvents.filter { it.isInCurrentWeek(nowMillis) }
        val primarySport = preferredSports.firstOrNull()?.lowercase() ?: currentWeekJoinedEvents
            .groupingBy { it.sport.lowercase() }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        val nextBusyStartMillis = currentWeekJoinedEvents
            .mapNotNull { it.scheduledAt?.toDate()?.time }
            .filter { it > nowMillis }
            .minOrNull()

        return openEvents
            .filter { it.status.lowercase() == "active" }
            .filter { event ->
                val eventStart = event.scheduledAt?.toDate()?.time ?: Long.MAX_VALUE
                eventStart > nowMillis
            }
            .map { event ->
                scoreEvent(
                    event = event,
                    primarySport = primarySport,
                    currentLocation = currentLocation,
                    nextBusyStartMillis = nextBusyStartMillis,
                    nowMillis = nowMillis
                )
            }
            .sortedWith(compareByDescending<RankedOpenMatch> { it.score }.thenBy { it.event.scheduledAt?.seconds ?: Long.MAX_VALUE })
    }

    fun parsePreferredSports(raw: String): Set<String> {
        return raw
            .split(',', ';', '|')
            .mapNotNull { it.trim().takeIf { value -> value.isNotBlank() } }
            .map { normalizeSport(it) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun scoreEvent(
        event: Event,
        primarySport: String?,
        currentLocation: Location?,
        nextBusyStartMillis: Long?,
        nowMillis: Long
    ): RankedOpenMatch {
        val reasons = mutableListOf<String>()
        var score = 0.0

        val eventSport = normalizeSport(event.sport)
        if (primarySport != null && eventSport == primarySport) {
            score += 4.0
            reasons += "Tu deporte favorito"
        }

        val minutesUntilStart = ((event.scheduledAt?.toDate()?.time ?: nowMillis) - nowMillis) / 60000.0
        when {
            minutesUntilStart <= 15 -> {
                score += 4.0
                reasons += "Empieza ya"
            }
            minutesUntilStart <= 60 -> {
                score += 3.0
                reasons += "Empieza pronto"
            }
            minutesUntilStart <= 120 -> score += 1.5
        }

        val remainingSpots = max(0, event.maxParticipants.toInt() - event.membersCount.toInt())
        when {
            remainingSpots <= 1 -> {
                score += 3.5
                reasons += "Ultimo cupo"
            }
            remainingSpots <= 2 -> {
                score += 2.0
                reasons += "Casi lleno"
            }
            remainingSpots <= 4 -> score += 1.0
        }

        val eventLocation = extractLocation(event.location)
        if (currentLocation != null && eventLocation != null) {
            val distanceKm = distanceKm(currentLocation, eventLocation)
            when {
                distanceKm <= 1.0 -> {
                    score += 4.0
                    reasons += "Cerca de ti"
                }
                distanceKm <= 3.0 -> {
                    score += 3.0
                    reasons += "A buena distancia"
                }
                distanceKm <= 6.0 -> score += 1.0
                else -> score -= 1.5
            }
        }

        val eventEndMillis = event.finishedAt?.toDate()?.time ?: (event.scheduledAt?.toDate()?.time?.plus(60 * 60 * 1000L) ?: nowMillis)
        if (nextBusyStartMillis != null) {
            if (eventEndMillis <= nextBusyStartMillis) {
                score += 4.0
                reasons += "Cabe en tu hueco"
            } else {
                score -= 3.0
                reasons += "Choca con tu agenda"
            }
        } else {
            score += 1.5
        }

        if (event.scheduledAt?.toDate()?.time != null) {
            val eventCalendar = Calendar.getInstance().apply { time = event.scheduledAt!!.toDate() }
            if (eventCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || eventCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                score += 0.5
            }
        }

        val cappedScore = min(20.0, score)
        return RankedOpenMatch(event = event, score = cappedScore, reasons = reasons.distinct().take(3))
    }

    private fun normalizeSport(value: String): String {
        return when (value.trim().lowercase()) {
            "fútbol", "futbol", "football", "soccer" -> "soccer"
            "baloncesto", "basketball" -> "basketball"
            "tenis", "tennis" -> "tennis"
            "calistenia", "calisthenics", "calistennics" -> "calisthenics"
            "correr", "running" -> "running"
            else -> value.trim().lowercase()
        }
    }

    private fun extractLocation(rawLocation: String): Location? {
        val match = locationRegex.find(rawLocation) ?: return null
        val latitude = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return null
        val longitude = match.groupValues.getOrNull(2)?.toDoubleOrNull() ?: return null
        return Location("open_match").apply {
            this.latitude = latitude
            this.longitude = longitude
        }
    }

    private fun distanceKm(from: Location, to: Location): Double {
        return from.distanceTo(to).toDouble() / 1000.0
    }

    private fun Event.isInCurrentWeek(nowMillis: Long): Boolean {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val startOfWeek = calendar.apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endOfWeek = Calendar.getInstance().apply {
            timeInMillis = startOfWeek
            add(Calendar.DAY_OF_YEAR, 7)
        }.timeInMillis

        val eventTime = scheduledAt?.toDate()?.time ?: return false
        return eventTime in startOfWeek until endOfWeek
    }
}
