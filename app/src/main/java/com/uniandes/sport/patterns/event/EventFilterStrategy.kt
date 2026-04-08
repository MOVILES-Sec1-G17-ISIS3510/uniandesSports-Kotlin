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
        val now = com.google.firebase.Timestamp.now()
        return events.filter { 
            it.status == "active" && 
            (it.scheduledAt ?: com.google.firebase.Timestamp(0, 0)) > now 
        }.sortedBy { it.scheduledAt }
    }
}

class MultiSportFilterStrategy(private val targetSports: Set<String>) : EventFilterStrategy {
    override fun filter(events: List<Event>): List<Event> {
        val now = com.google.firebase.Timestamp.now()
        return events.filter { 
            it.status == "active" && 
            (targetSports.isEmpty() || targetSports.contains(it.sport.lowercase())) &&
            (it.scheduledAt ?: com.google.firebase.Timestamp(0, 0)) > now
        }.sortedBy { it.scheduledAt }
    }
}
