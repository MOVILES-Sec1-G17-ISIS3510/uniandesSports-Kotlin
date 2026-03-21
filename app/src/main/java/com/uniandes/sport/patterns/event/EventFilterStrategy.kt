package com.uniandes.sport.patterns.event

import com.uniandes.sport.models.Event

/**
 * BEHAVIORAL PATTERN: Strategy
 * Defines an interchangeable strategy for filtering events without
 * modifying the viewmodel's internal logic.
 */
interface EventFilterStrategy {
    fun filter(events: List<Event>): List<Event>
}

class AllActiveEventsStrategy : EventFilterStrategy {
    override fun filter(events: List<Event>): List<Event> {
        return events.filter { it.status == "active" }
            .sortedBy { it.scheduledAt }
    }
}

class SportFilterStrategy(private val targetSport: String) : EventFilterStrategy {
    override fun filter(events: List<Event>): List<Event> {
        return events.filter { 
            it.status == "active" && 
            it.sport.lowercase() == targetSport.lowercase() 
        }.sortedBy { it.scheduledAt }
    }
}
