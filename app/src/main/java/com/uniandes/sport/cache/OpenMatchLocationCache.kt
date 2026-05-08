package com.uniandes.sport.cache

import com.uniandes.sport.models.Event
import java.util.Locale

private val locationRegex = Regex(
    """Lat:\s*(-?\d+(?:\.\d+)?),\s*Lng:\s*(-?\d+(?:\.\d+)?)""",
    RegexOption.IGNORE_CASE
)

private fun Double.toStableCoordinateKey(): String {
    return String.format(Locale.US, "%.4f", this)
}

enum class OpenMatchLocationSuggestionType {
    RECENT,
    POPULAR
}

data class OpenMatchLocationSuggestion(
    val locationString: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val type: OpenMatchLocationSuggestionType,
    val score: Double,
    val eventCount: Int
) {
    val stableKey: String = "${latitude.toStableCoordinateKey()}_${longitude.toStableCoordinateKey()}"
}

object OpenMatchLocationCache {
    private const val MAX_ENTRIES = 24
    private const val RECENT_LIMIT = 3
    private const val POPULAR_LIMIT = 3

    private val cache = object : LinkedHashMap<String, List<OpenMatchLocationSuggestion>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<OpenMatchLocationSuggestion>>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    var hitCount = 0
        private set

    var missCount = 0
        private set

    fun getSuggestions(
        userId: String?,
        historyEvents: List<Event>,
        allEvents: List<Event>
    ): List<OpenMatchLocationSuggestion> {
        val cacheKey = buildCacheKey(userId, historyEvents, allEvents)
        cache[cacheKey]?.let {
            hitCount++
            return it
        }

        missCount++
        val computed = buildSuggestions(historyEvents, allEvents)
        cache[cacheKey] = computed
        return computed
    }

    fun clear() {
        cache.clear()
        hitCount = 0
        missCount = 0
    }

    private fun buildSuggestions(
        historyEvents: List<Event>,
        allEvents: List<Event>
    ): List<OpenMatchLocationSuggestion> {
        val recent = buildRecentSuggestions(historyEvents)
        val recentKeys = recent.mapTo(mutableSetOf()) { it.stableKey }
        val popular = buildPopularSuggestions(allEvents, recentKeys)
        return (recent + popular).take(RECENT_LIMIT + POPULAR_LIMIT)
    }

    private fun buildRecentSuggestions(historyEvents: List<Event>): List<OpenMatchLocationSuggestion> {
        val seen = LinkedHashMap<String, OpenMatchLocationSuggestion>()

        historyEvents
            .sortedByDescending { it.scheduledAt?.seconds ?: it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
            .forEach { event ->
                parseSuggestion(event)?.let { suggestion ->
                    if (!seen.containsKey(suggestion.stableKey)) {
                        seen[suggestion.stableKey] = suggestion.copy(type = OpenMatchLocationSuggestionType.RECENT)
                    }
                }
            }

        return seen.values.take(RECENT_LIMIT)
    }

    private fun buildPopularSuggestions(
        allEvents: List<Event>,
        excludedKeys: Set<String>
    ): List<OpenMatchLocationSuggestion> {
        val aggregates = LinkedHashMap<String, LocationAggregate>()

        allEvents.forEach { event ->
            if (event.status.equals("cancelled", ignoreCase = true)) return@forEach

            val suggestion = parseSuggestion(event) ?: return@forEach
            if (suggestion.stableKey in excludedKeys) return@forEach

            val crowdScore = when {
                event.maxParticipants > 0 -> event.membersCount.toDouble() / event.maxParticipants.toDouble()
                event.membersCount > 0 -> event.membersCount.toDouble()
                else -> 0.0
            }

            val current = aggregates[suggestion.stableKey]
            if (current == null) {
                aggregates[suggestion.stableKey] = LocationAggregate(
                    label = suggestion.label,
                    locationString = suggestion.locationString,
                    latitude = suggestion.latitude,
                    longitude = suggestion.longitude,
                    crowdScore = crowdScore,
                    eventCount = 1,
                    latestMillis = suggestion.latestMillis(event)
                )
            } else {
                current.crowdScore = maxOf(current.crowdScore, crowdScore)
                current.eventCount += 1
                current.latestMillis = maxOf(current.latestMillis, suggestion.latestMillis(event))
                if (current.label.isBlank()) {
                    current.label = suggestion.label
                }
            }
        }

        return aggregates.values
            .sortedWith(
                compareByDescending<LocationAggregate> { it.crowdScore }
                    .thenByDescending { it.eventCount }
                    .thenByDescending { it.latestMillis }
            )
            .take(POPULAR_LIMIT)
            .map {
                OpenMatchLocationSuggestion(
                    locationString = it.locationString,
                    label = it.label,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    type = OpenMatchLocationSuggestionType.POPULAR,
                    score = it.crowdScore,
                    eventCount = it.eventCount
                )
            }
    }

    private fun parseSuggestion(event: Event): OpenMatchLocationSuggestion? {
        val match = locationRegex.find(event.location) ?: return null
        val latitude = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return null
        val longitude = match.groupValues.getOrNull(2)?.toDoubleOrNull() ?: return null
        val label = normalizeLocationLabel(event.location)
        return OpenMatchLocationSuggestion(
            locationString = event.location,
            label = label,
            latitude = latitude,
            longitude = longitude,
            type = OpenMatchLocationSuggestionType.RECENT,
            score = 0.0,
            eventCount = 1
        )
    }

    private fun normalizeLocationLabel(rawLocation: String): String {
        val cleaned = rawLocation
            .replace(locationRegex, "")
            .replace("- ,", "")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ',', '•')

        return cleaned.ifBlank { rawLocation }
    }

    private fun buildCacheKey(
        userId: String?,
        historyEvents: List<Event>,
        allEvents: List<Event>
    ): String {
        return listOf(
            userId.orEmpty(),
            fingerprint(historyEvents),
            fingerprint(allEvents)
        ).joinToString("|")
    }

    private fun fingerprint(events: List<Event>): String {
        return events.joinToString(separator = ";") { event ->
            listOf(
                event.id,
                event.location,
                event.status,
                event.membersCount,
                event.maxParticipants,
                event.scheduledAt?.seconds ?: 0L,
                event.updatedAt?.seconds ?: 0L,
                event.createdAt?.seconds ?: 0L
            ).joinToString(":")
        }
    }

    private fun Double.toStableCoordinateKey(): String {
        return String.format(Locale.US, "%.4f", this)
    }

    private fun OpenMatchLocationSuggestion.latestMillis(event: Event): Long {
        return event.scheduledAt?.seconds ?: event.updatedAt?.seconds ?: event.createdAt?.seconds ?: 0L
    }

    private data class LocationAggregate(
        var label: String,
        val locationString: String,
        val latitude: Double,
        val longitude: Double,
        var crowdScore: Double,
        var eventCount: Int,
        var latestMillis: Long
    )
}