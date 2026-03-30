package com.uniandes.sport.viewmodels.play

import com.uniandes.sport.models.Event
import kotlinx.coroutines.flow.StateFlow

interface PlayViewModelInterface {
    val events: StateFlow<List<Event>>
    val isLoading: StateFlow<Boolean>
    val selectedSport: StateFlow<String?>
    val currentUserId: String?
    
    fun setSportFilter(sport: String?)
    fun fetchEvents()
    fun joinEvent(eventId: String, userId: String, onSuccess: () -> Unit = {}, onError: (Exception) -> Unit = {})
    fun createEvent(title: String, description: String, location: String, sport: String, modality: String, scheduledAt: java.util.Date, skillLevel: String, maxParticipants: Long, onSuccess: () -> Unit, onError: (Exception) -> Unit)
}
