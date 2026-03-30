package com.uniandes.sport.viewmodels.play

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.uniandes.sport.models.Event
import com.uniandes.sport.patterns.event.AllActiveEventsStrategy
import com.uniandes.sport.patterns.event.EventFilterStrategy
import com.uniandes.sport.patterns.event.SportFilterStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FirestorePlayViewModel : ViewModel(), PlayViewModelInterface {
    private val db = FirebaseFirestore.getInstance()

    private val _rawEvents = MutableStateFlow<List<Event>>(emptyList())
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    override val events: StateFlow<List<Event>> = _events.asStateFlow()

    private val _members = MutableStateFlow<List<com.uniandes.sport.models.MatchMember>>(emptyList())
    override val members: StateFlow<List<com.uniandes.sport.models.MatchMember>> = _members.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedSport = MutableStateFlow<String?>(null)
    override val selectedSport: StateFlow<String?> = _selectedSport.asStateFlow()

    override val currentUserId: String?
        get() = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    private var currentFilterStrategy: EventFilterStrategy = AllActiveEventsStrategy()

    init {
        viewModelScope.launch {
            com.uniandes.sport.repositories.EventCacheRepository.cachedEvents.collect { list ->
                _rawEvents.value = list
                applyCurrentStrategy()
            }
        }
        viewModelScope.launch {
            com.uniandes.sport.repositories.EventCacheRepository.isLoading.collect { loading ->
                _isLoading.value = loading
            }
        }
        fetchEvents()
    }

    override fun fetchEvents() {
        // TÁCTICA DE DESEMPEÑO: Delegamos al CacheProxy para ahorrar Network calls redundantes
        com.uniandes.sport.repositories.EventCacheRepository.fetchEventsIfNeeded()
    }

    override fun setSportFilter(sport: String?) {
        _selectedSport.value = sport
        currentFilterStrategy = if (sport != null) {
            SportFilterStrategy(sport)
        } else {
            AllActiveEventsStrategy()
        }
        applyCurrentStrategy()
    }

    private fun applyCurrentStrategy() {
        _events.value = currentFilterStrategy.filter(_rawEvents.value)
    }

    override fun fetchMembers(eventId: String) {
        db.collection("events").document(eventId).collection("members")
            .orderBy("joinedAt")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val membersList = snapshot?.toObjects(com.uniandes.sport.models.MatchMember::class.java) ?: emptyList()
                _members.value = membersList
            }
    }

    override fun joinEvent(eventId: String, userId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val docRef = db.collection("events").document(eventId)
        val memberRef = docRef.collection("members").document(userId)
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val displayName = currentUser?.email ?: "User ${userId.take(5)}"

        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val participants = (snapshot.get("participants") as? List<String>)?.toMutableList() ?: mutableListOf()
            val max = snapshot.getLong("maxParticipants") ?: 0L
            
            if (participants.size < max && !participants.contains(userId)) {
                participants.add(userId)
                transaction.update(docRef, "participants", participants)
                
                val newMember = com.uniandes.sport.models.MatchMember(
                    userId = userId,
                    displayName = displayName,
                    joinedAt = System.currentTimeMillis(),
                    role = "member"
                )
                transaction.set(memberRef, newMember)
            } else {
                throw Exception("Cannot join: Either match is full or you are already a participant")
            }
        }.addOnSuccessListener {
            Log.d("PlayVM", "User $userId joined event $eventId")
            com.uniandes.sport.repositories.EventCacheRepository.invalidateCache()
            com.uniandes.sport.repositories.EventCacheRepository.fetchEventsIfNeeded(forceRefresh = true)
            onSuccess()
        }.addOnFailureListener { e ->
            Log.e("PlayVM", "Failed to join event: ${e.message}")
            onError(e as? Exception ?: Exception(e.message))
        }
    }

    override fun createEvent(
        title: String,
        description: String,
        location: String,
        sport: String,
        modality: String,
        scheduledAt: java.util.Date,
        skillLevel: String,
        maxParticipants: Long,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return onError(Exception("User not authenticated"))
        
        val event = com.uniandes.sport.patterns.event.EventFactory.createEvent(
            title = title,
            description = description,
            location = location,
            createdBy = uid,
            sport = sport,
            modality = modality,
            maxParticipants = maxParticipants,
            scheduledAt = scheduledAt,
            metadata = mapOf("skillLevel" to skillLevel)
        ).apply {
            participants = listOf(uid)
        }
        
        db.collection("events").add(event)
            .addOnSuccessListener { doc ->
                val member = com.uniandes.sport.models.MatchMember(
                    userId = uid,
                    displayName = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "Organizer",
                    joinedAt = System.currentTimeMillis(),
                    role = "organizer"
                )
                doc.collection("members").document(uid).set(member)
                
                com.uniandes.sport.repositories.EventCacheRepository.invalidateCache()
                com.uniandes.sport.repositories.EventCacheRepository.fetchEventsIfNeeded(forceRefresh = true)
                onSuccess() 
            }
            .addOnFailureListener { onError(it) }
    }

    override fun kickMember(eventId: String, userId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val docRef = db.collection("events").document(eventId)
        val memberRef = docRef.collection("members").document(userId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val participants = (snapshot.get("participants") as? List<String>)?.toMutableList() ?: mutableListOf()
            
            if (participants.contains(userId)) {
                participants.remove(userId)
                transaction.update(docRef, "participants", participants)
                transaction.delete(memberRef)
            }
        }.addOnSuccessListener {
            com.uniandes.sport.repositories.EventCacheRepository.invalidateCache()
            com.uniandes.sport.repositories.EventCacheRepository.fetchEventsIfNeeded(forceRefresh = true)
            onSuccess()
        }.addOnFailureListener { onError(it as? Exception ?: Exception(it.message)) }
    }
}
