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
    
    //  Dynamic limits based on memory pressure
    private const val MIN_RECENT_LIMIT = 1
    private const val MAX_RECENT_LIMIT = 5
    private const val MIN_POPULAR_LIMIT = 1
    private const val MAX_POPULAR_LIMIT = 5
    
    //  Memory pressure thresholds (% of max heap used)
    private const val MEMORY_PRESSURE_LOW = 60f        // < 60%: show max suggestions
    private const val MEMORY_PRESSURE_MEDIUM = 75f     // 60-75%: show medium suggestions
    private const val MEMORY_PRESSURE_HIGH = 85f       // 75-85%: show fewer suggestions
    private const val MEMORY_PRESSURE_CRITICAL = 95f   // > 85%: show minimal suggestions

    private val cache = object : LinkedHashMap<String, List<OpenMatchLocationSuggestion>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<OpenMatchLocationSuggestion>>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    var hitCount = 0
        private set

    var missCount = 0
        private set
    
    /**
     * Calcula los límites de ubicaciones dinámicamente basado en presión de memoria
     * 
     * **Decisiones de implementación:**
     * - Usa Runtime.getRuntime() para obtener stats de memoria del proceso
     * - Calcula memory_pressure = (totalMemory - freeMemory) / maxMemory * 100%
     * - Escala RECENT_LIMIT y POPULAR_LIMIT según presión
     * 
     * **Rangos de memory pressure:**
     * - LOW (<60%): 5 recientes + 5 populares = 10 sugerencias (ideal)
     * - MEDIUM (60-75%): 3 recientes + 3 populares = 6 sugerencias (equilibrio)
     * - HIGH (75-85%): 2 recientes + 2 populares = 4 sugerencias (constrained)
     * - CRITICAL (>85%): 1 reciente + 1 popular = 2 sugerencias (emergency mode)
     * 
     * @return Pair<recentLimit, popularLimit>
     */
    private fun calculateDynamicLimits(): Pair<Int, Int> {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        
        // Memoria en uso (en bytes)
        val usedMemory = totalMemory - freeMemory
        
        // Porcentaje de presión = (usedMemory / maxMemory) * 100
        val memoryPressure = (usedMemory.toFloat() / maxMemory.toFloat()) * 100f
        
        val (recentLimit, popularLimit) = when {
            memoryPressure < MEMORY_PRESSURE_LOW -> {
                //  Mucha memoria disponible
                Pair(MAX_RECENT_LIMIT, MAX_POPULAR_LIMIT)  // 5 + 5
            }
            memoryPressure < MEMORY_PRESSURE_MEDIUM -> {
                //  Memoria moderada
                Pair(3, 3)  // Valores por defecto
            }
            memoryPressure < MEMORY_PRESSURE_HIGH -> {
                //  Memoria baja
                Pair(2, 2)
            }
            memoryPressure < MEMORY_PRESSURE_CRITICAL -> {
                //  Memoria crítica
                Pair(MIN_RECENT_LIMIT, MIN_POPULAR_LIMIT)  // 1 + 1
            }
            else -> {
                //  Emergency: solo 1 + 1
                Pair(MIN_RECENT_LIMIT, MIN_POPULAR_LIMIT)
            }
        }
        
        return recentLimit to popularLimit
    }

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
        // 🔧 Calcular límites dinámicos según memoria
        val (recentLimit, popularLimit) = calculateDynamicLimits()
        
        val recent = buildRecentSuggestions(historyEvents, recentLimit)
        val recentKeys = recent.mapTo(mutableSetOf()) { it.stableKey }
        val popular = buildPopularSuggestions(allEvents, recentKeys, popularLimit)
        return (recent + popular)
    }

    private fun buildRecentSuggestions(
        historyEvents: List<Event>,
        limit: Int
    ): List<OpenMatchLocationSuggestion> {
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

        return seen.values.take(limit)
    }

    private fun buildPopularSuggestions(
        allEvents: List<Event>,
        excludedKeys: Set<String>,
        limit: Int
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
            .take(limit)
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