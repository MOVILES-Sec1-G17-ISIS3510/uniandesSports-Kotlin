package com.uniandes.sport.patterns.event

import com.uniandes.sport.models.Event
import java.text.SimpleDateFormat
import java.util.*

/**
 * STRUCTURAL PATTERN: Adapter / Decorator
 * Adapts the raw Firestore Event (with Timestamps and UIDs) into 
 * a UI-friendly representation containing pre-formatted strings
 * and UI logic components.
 */
data class EventUIModel(
    val rawEvent: Event,
    val formattedDate: String,
    val participantsFraction: String,
    val isFull: Boolean
)

object EventUIAdapter {
    /**
     * TÁCTICA DE MEMORIA: Flyweight / Memoization
     * Almacena instancias generadas de EventUIModel en memoria usando un checksum.
     * Al hacer scroll rápido en Jetpack Compose, evitamos alocar masivamente 
     * cadenas de texto ("Hoy 3:00 PM") e instancias de formato de fechas 
     * múltiples veces por segundo, reduciendo la carga del recolector de basura (GC).
     */
    private val memoryCache = mutableMapOf<String, EventUIModel>()

    fun toUIModel(event: Event): EventUIModel {
        // Hashing key basado en el estado atómico del evento (ID + Versionamiento)
        val cacheKey = "${event.id}_${event.updatedAt?.seconds}_${event.membersCount}"
        
        memoryCache[cacheKey]?.let { return it }

        val dateFormateada = event.scheduledAt?.toDate()?.let { date ->
            // Si es hoy, muestra "Hoy hh:mm a". Si no, muestra la fecha normal
            val calToday = Calendar.getInstance()
            val calEvent = Calendar.getInstance().apply { time = date }
            
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val fullFormat = SimpleDateFormat("dd MMM h:mm a", Locale.getDefault())
            
            if (calToday.get(Calendar.YEAR) == calEvent.get(Calendar.YEAR) &&
                calToday.get(Calendar.DAY_OF_YEAR) == calEvent.get(Calendar.DAY_OF_YEAR)
            ) {
                "Hoy ${timeFormat.format(date)}"
            } else {
                fullFormat.format(date)
            }
        } ?: "Sin fecha"

        val count = event.membersCount.toInt()
        val max = event.maxParticipants
        
        val newModel = EventUIModel(
            rawEvent = event,
            formattedDate = dateFormateada,
            participantsFraction = "$count/$max",
            isFull = count >= max.toInt()
        )
        
        memoryCache[cacheKey] = newModel
        return newModel
    }
}
