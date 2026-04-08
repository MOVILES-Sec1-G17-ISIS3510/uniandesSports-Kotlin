package com.uniandes.sport.viewmodels.play

import com.uniandes.sport.models.OpenMatchReview
import com.uniandes.sport.models.Event
import com.uniandes.sport.models.MatchMember
import kotlinx.coroutines.flow.StateFlow

interface PlayViewModelInterface {
    val events: StateFlow<List<Event>>
    val inProgressEvents: StateFlow<List<Event>>
    val finishedEvents: StateFlow<List<Event>>
    val isLoading: StateFlow<Boolean>
    val members: StateFlow<List<com.uniandes.sport.models.MatchMember>>
    val selectedSports: StateFlow<Set<String>>
    val joinedEventIds: StateFlow<Set<String>>
    val myReviewsByEventId: StateFlow<Map<String, OpenMatchReview>>
    val currentUserId: String?
    
    fun toggleSportFilter(sport: String)
    fun clearSportFilters()
    fun fetchEvents()
    fun refreshEvents()
    fun fetchMyReviewsForEvents(eventIds: List<String>)
    fun fetchEventMembersOnce(eventId: String, onSuccess: (List<MatchMember>) -> Unit, onError: (Exception) -> Unit = {})
    fun fetchMembers(eventId: String)
    fun joinEvent(eventId: String, userId: String, onSuccess: () -> Unit = {}, onError: (Exception) -> Unit = {})
    fun leaveEvent(eventId: String, userId: String, onSuccess: () -> Unit = {}, onError: (Exception) -> Unit = {})
    fun cancelEvent(eventId: String, onSuccess: () -> Unit = {}, onError: (Exception) -> Unit = {})
    fun createEvent(title: String, description: String, location: String, sport: String, modality: String, scheduledAt: java.util.Date, finishedAt: java.util.Date?, skillLevel: String, maxParticipants: Long, shouldJoin: Boolean, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun updateEvent(eventId: String, title: String, description: String, location: String, sport: String, scheduledAt: java.util.Date, finishedAt: java.util.Date?, skillLevel: String, maxParticipants: Long, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun kickMember(eventId: String, userId: String, onSuccess: () -> Unit = {}, onError: (Exception) -> Unit = {})

    fun submitReview(eventId: String, reviewText: String, rating: Int, attendanceByUserId: Map<String, Boolean>, source: String = "text", onSuccess: () -> Unit = {}, onError: (Exception) -> Unit = {})
    
    fun updateReviewAiAnalysis(eventId: String, userId: String, analysis: Map<String, Double>)
}
