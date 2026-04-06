package com.uniandes.sport.viewmodels.play

import com.uniandes.sport.models.Event
import kotlinx.coroutines.flow.StateFlow

interface PlayViewModelInterface {
    val events: StateFlow<List<Event>>
    val inProgressEvents: StateFlow<List<Event>>
    val finishedEvents: StateFlow<List<Event>>
    val isLoading: StateFlow<Boolean>
    val members: StateFlow<List<com.uniandes.sport.models.MatchMember>>
    val selectedSport: StateFlow<String?>
    val joinedEventIds: StateFlow<Set<String>>
    val currentUserId: String?
    
    fun setSportFilter(sport: String?)
    fun fetchEvents()
    fun refreshEvents()
    fun fetchMembers(eventId: String)
    fun joinEvent(eventId: String, userId: String, onSuccess: () -> Unit = {}, onError: (Exception) -> Unit = {})
    fun createEvent(title: String, description: String, location: String, sport: String, modality: String, scheduledAt: java.util.Date, skillLevel: String, maxParticipants: Long, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun kickMember(eventId: String, userId: String, onSuccess: () -> Unit = {}, onError: (Exception) -> Unit = {})
    fun submitReview(eventId: String, reviewText: String, source: String = "text", onSuccess: () -> Unit = {}, onError: (Exception) -> Unit = {})
}
