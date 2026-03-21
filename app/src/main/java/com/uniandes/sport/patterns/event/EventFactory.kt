package com.uniandes.sport.patterns.event

import com.google.firebase.Timestamp
import com.uniandes.sport.models.Event
import java.util.Date

/**
 * CREATIONAL PATTERN: Factory Method
 * Encapsulates the instantiation logic for an Event, setting proper 
 * timestamps, ensuring the creator is in the participants list, and 
 * verifying status defaults.
 */
object EventFactory {
    fun createEvent(
        title: String,
        description: String,
        location: String,
        createdBy: String,
        sport: String,
        modality: String,
        maxParticipants: Long,
        scheduledAt: Date,
        metadata: Map<String, Any> = emptyMap()
    ): Event {
        val now = Timestamp.now()
        return Event(
            createdAt = now,
            updatedAt = now,
            createdBy = createdBy,
            description = description,
            location = location,
            maxParticipants = maxParticipants,
            metadata = metadata,
            modality = modality,
            participants = listOf(createdBy), // Creator is always the first participant
            scheduledAt = Timestamp(scheduledAt),
            sport = sport.lowercase(),
            status = "active",
            title = title
        )
    }
}
