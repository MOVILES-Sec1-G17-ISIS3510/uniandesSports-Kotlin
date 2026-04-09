package com.uniandes.sport.patterns.event

import android.location.Location
import com.uniandes.sport.models.Event
import java.util.Calendar
import java.util.Date
import kotlin.math.max
import kotlin.math.min

private val locationRegex = Regex("""Lat:\s*(-?\d+(?:\.\d+)?),\s*Lng:\s*(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

data class ScoreContribution(
    val label: String,
    val points: Double
)

data class PhoneCalendarEvent(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean
)

data class RankedOpenMatch(
    val event: Event,
    val score: Double,
    val reasons: List<String>,
    val contributions: List<ScoreContribution>,
    val dayCalendarEvents: List<PhoneCalendarEvent>,
    val conflictingCalendarEvents: List<PhoneCalendarEvent>
)

object OpenMatchRanker {
    fun rank(
        openEvents: List<Event>,
        joinedEvents: List<Event>,
        historyEvents: List<Event> = joinedEvents,
        preferredSports: Set<String>,
        phoneCalendarEvents: List<PhoneCalendarEvent> = emptyList(),
        currentLocation: Location? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): List<RankedOpenMatch> {
        val now = Date(nowMillis)
        val currentWeekJoinedEvents = joinedEvents.filter { it.isInCurrentWeek(nowMillis) }
        val history = historyEvents.filter { it.scheduledAt != null }
        val normalizedPreferredSports = preferredSports
            .map { normalizeSport(it) }
            .filter { it.isNotBlank() }
            .toSet()
        val primarySport = preferredSports.firstOrNull()?.lowercase() ?: history
            .groupingBy { it.sport.lowercase() }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val dayPreferenceCounts = history
            .mapNotNull { it.scheduledAt?.toDate()?.let { date ->
                Calendar.getInstance().apply { time = date }.get(Calendar.DAY_OF_WEEK)
            } }
            .groupingBy { it }
            .eachCount()
        val hourBucketPreferenceCounts = history
            .mapNotNull { it.scheduledAt?.toDate()?.let { date ->
                val hour = Calendar.getInstance().apply { time = date }.get(Calendar.HOUR_OF_DAY)
                toHourBucket(hour)
            } }
            .groupingBy { it }
            .eachCount()
        val preferredHistoryLocation = history
            .mapNotNull { extractLocation(it.location) }
            .takeIf { it.isNotEmpty() }
            ?.let { locations ->
                Location("history_centroid").apply {
                    latitude = locations.map { it.latitude }.average()
                    longitude = locations.map { it.longitude }.average()
                }
            }

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
                    preferredSports = normalizedPreferredSports,
                    currentLocation = currentLocation,
                    preferredHistoryLocation = preferredHistoryLocation,
                    dayPreferenceCounts = dayPreferenceCounts,
                    hourBucketPreferenceCounts = hourBucketPreferenceCounts,
                    nextBusyStartMillis = nextBusyStartMillis,
                    phoneCalendarEvents = phoneCalendarEvents,
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
        preferredSports: Set<String>,
        currentLocation: Location?,
        preferredHistoryLocation: Location?,
        dayPreferenceCounts: Map<Int, Int>,
        hourBucketPreferenceCounts: Map<Int, Int>,
        nextBusyStartMillis: Long?,
        phoneCalendarEvents: List<PhoneCalendarEvent>,
        nowMillis: Long
    ): RankedOpenMatch {
        val contributions = mutableListOf<ScoreContribution>()
        var score = 0.0

        val eventStartMillis = event.scheduledAt?.toDate()?.time ?: nowMillis
        val eventEndMillis = event.finishedAt?.toDate()?.time ?: (eventStartMillis + 60 * 60 * 1000L)

        val dayCalendarEvents = phoneCalendarEvents
            .filter { !it.isAllDay }
            .filter {
            isSameDay(it.startMillis, eventStartMillis)
            }
        val conflictingCalendarEvents = dayCalendarEvents.filter {
            overlaps(
                startA = eventStartMillis,
                endA = eventEndMillis,
                startB = it.startMillis,
                endB = if (it.isAllDay) endOfDay(it.startMillis) else it.endMillis
            )
        }

        val eventSport = normalizeSport(event.sport)
        if (preferredSports.isNotEmpty() && preferredSports.contains(eventSport)) {
            score += 4.0
            contributions += ScoreContribution("One of your favorite sports", 4.0)
        }

        if (primarySport != null && eventSport == normalizeSport(primarySport)) {
            score += 1.0
            contributions += ScoreContribution("Your top sport", 1.0)
        }

        val eventCalendar = Calendar.getInstance().apply { timeInMillis = eventStartMillis }
        val preferredDayCount = dayPreferenceCounts[eventCalendar.get(Calendar.DAY_OF_WEEK)] ?: 0
        val topDayCount = dayPreferenceCounts.values.maxOrNull() ?: 0
        if (preferredDayCount > 0 && topDayCount > 0) {
            val dayScore = 2.0 * (preferredDayCount.toDouble() / topDayCount.toDouble())
            score += dayScore
            contributions += ScoreContribution("Matches your usual day", dayScore)
        }

        val eventHourBucket = toHourBucket(eventCalendar.get(Calendar.HOUR_OF_DAY))
        val preferredHourCount = hourBucketPreferenceCounts[eventHourBucket] ?: 0
        val topHourCount = hourBucketPreferenceCounts.values.maxOrNull() ?: 0
        if (preferredHourCount > 0 && topHourCount > 0) {
            val hourScore = 2.5 * (preferredHourCount.toDouble() / topHourCount.toDouble())
            score += hourScore
            contributions += ScoreContribution("Matches your usual time", hourScore)
        }

        val minutesUntilStart = (eventStartMillis - nowMillis) / 60000.0
        when {
            minutesUntilStart <= 15 -> {
                score += 4.0
                contributions += ScoreContribution("Starting now", 4.0)
            }
            minutesUntilStart <= 60 -> {
                score += 3.0
                contributions += ScoreContribution("Starting soon", 3.0)
            }
            minutesUntilStart <= 120 -> {
                score += 1.5
                contributions += ScoreContribution("Good start time", 1.5)
            }
        }

        val remainingSpots = max(0, event.maxParticipants.toInt() - event.membersCount.toInt())
        when {
            remainingSpots <= 1 -> {
                score += 3.5
                contributions += ScoreContribution("Last spot", 3.5)
            }
            remainingSpots <= 2 -> {
                score += 2.0
                contributions += ScoreContribution("Almost full", 2.0)
            }
            remainingSpots <= 4 -> {
                score += 1.0
                contributions += ScoreContribution("Limited spots", 1.0)
            }
        }

        val eventLocation = extractLocation(event.location)
        if (currentLocation != null && eventLocation != null) {
            val distanceKm = distanceKm(currentLocation, eventLocation)
            when {
                distanceKm <= 1.0 -> {
                    score += 4.0
                    contributions += ScoreContribution("Near you", 4.0)
                }
                distanceKm <= 3.0 -> {
                    score += 3.0
                    contributions += ScoreContribution("Good distance", 3.0)
                }
                distanceKm <= 6.0 -> {
                    score += 1.0
                    contributions += ScoreContribution("Reachable distance", 1.0)
                }
                else -> {
                    score -= 1.5
                    contributions += ScoreContribution("Far from current location", -1.5)
                }
            }
        }

        if (preferredHistoryLocation != null && eventLocation != null) {
            val distanceToHistoryKm = distanceKm(preferredHistoryLocation, eventLocation)
            when {
                distanceToHistoryKm <= 1.0 -> {
                    score += 2.5
                    contributions += ScoreContribution("Near your usual zone", 2.5)
                }
                distanceToHistoryKm <= 3.0 -> {
                    score += 1.5
                    contributions += ScoreContribution("Close to your usual zone", 1.5)
                }
                distanceToHistoryKm > 8.0 -> {
                    score -= 1.0
                    contributions += ScoreContribution("Outside your usual zone", -1.0)
                }
            }
        }

        if (nextBusyStartMillis != null) {
            if (eventEndMillis <= nextBusyStartMillis) {
                score += 4.0
                contributions += ScoreContribution("Fits your free window", 4.0)
            } else {
                score -= 3.0
                contributions += ScoreContribution("Schedule conflict", -3.0)
            }
        } else {
            score += 1.5
            contributions += ScoreContribution("No upcoming conflict", 1.5)
        }

        if (conflictingCalendarEvents.isNotEmpty()) {
            score -= 4.0
            contributions += ScoreContribution("Conflicts with phone calendar", -4.0)
        } else if (dayCalendarEvents.isNotEmpty()) {
            score += 1.0
            contributions += ScoreContribution("Compatible with phone calendar day", 1.0)
        }

        if (eventCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || eventCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            score += 0.5
            contributions += ScoreContribution("Weekend flexibility", 0.5)
        }

        val cappedScore = min(20.0, score)
        val reasons = contributions
            .filter { it.points > 0 }
            .sortedByDescending { it.points }
            .map { it.label }
            .distinct()
            .take(3)
        return RankedOpenMatch(
            event = event,
            score = cappedScore,
            reasons = reasons,
            contributions = contributions,
            dayCalendarEvents = dayCalendarEvents,
            conflictingCalendarEvents = conflictingCalendarEvents
        )
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

    private fun isSameDay(aMillis: Long, bMillis: Long): Boolean {
        val a = Calendar.getInstance().apply { timeInMillis = aMillis }
        val b = Calendar.getInstance().apply { timeInMillis = bMillis }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun overlaps(startA: Long, endA: Long, startB: Long, endB: Long): Boolean {
        return startA < endB && startB < endA
    }

    private fun endOfDay(millis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
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

    private fun toHourBucket(hourOfDay: Int): Int {
        return (hourOfDay / 3).coerceIn(0, 7)
    }
}
