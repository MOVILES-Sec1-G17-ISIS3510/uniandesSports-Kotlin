package com.uniandes.sport.viewmodels.play

import com.uniandes.sport.models.Track
import com.uniandes.sport.models.Event
import com.uniandes.sport.models.MatchMember
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface del ViewModel para gestionar la lógica de "Play" (Partidos y Seguimiento).
 */
interface PlayViewModelInterface {
    val events: StateFlow<List<Event>>
    val inProgressEvents: StateFlow<List<Event>>
    val finishedEvents: StateFlow<List<Event>>
    val isLoading: StateFlow<Boolean>
    val members: StateFlow<List<com.uniandes.sport.models.MatchMember>>
    val selectedSports: StateFlow<Set<String>>
    val joinedEventIds: StateFlow<Set<String>>
    val myTracksByEventId: StateFlow<Map<String, Track>> // Antes: myReviewsByEventId
    val currentUserId: String?
    
    fun toggleSportFilter(sport: String)
    fun clearSportFilters()
    fun fetchEvents()
    fun refreshEvents()
    
    /** Busca los registros de progreso (Tracks) del usuario para una lista de eventos */
    fun fetchMyTracksForEvents(eventIds: List<String>)
    
    fun fetchEventMembersOnce(eventId: String, onSuccess: (List<MatchMember>) -> Unit, onError: (Exception) -> Unit = {})
    fun fetchMembers(eventId: String)
    fun joinEvent(eventId: String, userId: String, sport: String, onSuccess: () -> Unit = {}, onError: (Exception) -> Unit = {})
    fun leaveEvent(eventId: String, userId: String, onSuccess: () -> Unit = {}, onError: (Exception) -> Unit = {})
    fun cancelEvent(eventId: String, onSuccess: () -> Unit = {}, onError: (Exception) -> Unit = {})
    fun createEvent(title: String, description: String, location: String, sport: String, modality: String, scheduledAt: java.util.Date, finishedAt: java.util.Date?, skillLevel: String, maxParticipants: Long, shouldJoin: Boolean, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun updateEvent(eventId: String, title: String, description: String, location: String, sport: String, scheduledAt: java.util.Date, finishedAt: java.util.Date?, skillLevel: String, maxParticipants: Long, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun kickMember(eventId: String, userId: String, onSuccess: () -> Unit = {}, onError: (Exception) -> Unit = {})

    /** Envía el registro de progreso (Track) de una sesión */
    fun submitTrack(
        eventId: String, 
        text: String, 
        rating: Int, 
        participated: Boolean, 
        source: String = "text", 
        onSuccess: () -> Unit = {}, 
        onError: (Exception) -> Unit = {}
    )
    
    /** Actualiza el análisis de IA de un registro de progreso ya existente */
    fun updateTrackAiAnalysis(eventId: String, userId: String, analysis: Map<String, Double>)
}
