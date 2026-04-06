package com.uniandes.sport.viewmodels.play

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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

    private val _joinedEventIds = MutableStateFlow<Set<String>>(emptySet())
    override val joinedEventIds: StateFlow<Set<String>> = _joinedEventIds.asStateFlow()

    private var joinedEventsListener: ListenerRegistration? = null

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
        observeCurrentUserJoinedEvents()
        fetchEvents()
    }

    private fun observeCurrentUserJoinedEvents() {
        val uid = currentUserId
        if (uid.isNullOrBlank()) {
            _joinedEventIds.value = emptySet()
            return
        }

        joinedEventsListener?.remove()
        joinedEventsListener = db.collectionGroup("members")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("PlayVM", "Error listening joined events: ${e.message}")
                    return@addSnapshotListener
                }

                val joined = snapshot?.documents
                    ?.mapNotNull { it.reference.parent.parent?.id }
                    ?.toSet()
                    ?: emptySet()
                _joinedEventIds.value = joined
            }
    }

    override fun fetchEvents() {
        // TÁCTICA DE DESEMPEÑO: Delegamos al CacheProxy para ahorrar Network calls redundantes
        com.uniandes.sport.repositories.EventCacheRepository.fetchEventsIfNeeded()
    }

    override fun refreshEvents() {
        com.uniandes.sport.repositories.EventCacheRepository.invalidateCache()
        com.uniandes.sport.repositories.EventCacheRepository.fetchEventsIfNeeded(forceRefresh = true)
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
        Log.d("PlayVM", "Fetching members for event: $eventId")
        db.collection("events").document(eventId).collection("members")
            .orderBy("joinedAt")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("PlayVM", "Error listening to members: ${e.message}")
                    return@addSnapshotListener
                }
                val membersList = snapshot?.toObjects(com.uniandes.sport.models.MatchMember::class.java) ?: emptyList()
                Log.d("PlayVM", "Fetched ${membersList.size} members for $eventId")
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
            val membersCount = snapshot.getLong("membersCount") ?: 0L
            val max = snapshot.getLong("maxParticipants") ?: 0L
            
            // Check if member already exists in subcollection
            val memberSnapshot = transaction.get(memberRef)
            if (memberSnapshot.exists()) {
                Log.d("PlayVM", "User $userId already joined subcollection. returning success.")
                return@runTransaction
            }

            if (membersCount < max) {
                val newMember = com.uniandes.sport.models.MatchMember(
                    userId = userId,
                    displayName = displayName,
                    joinedAt = System.currentTimeMillis(),
                    role = "member"
                )
                transaction.set(memberRef, newMember)
                transaction.update(docRef, "membersCount", com.google.firebase.firestore.FieldValue.increment(1))
                Log.d("PlayVM", "Transaction: User $userId added to subcollection 'members'. Counter incremented.")
            } else {
                Log.w("PlayVM", "Transaction: Match is full ($max)")
                throw Exception("Match is full")
            }
        }.addOnSuccessListener {
            Log.d("PlayVM", "User $userId successfully JOINED event $eventId")
            _joinedEventIds.value = _joinedEventIds.value + eventId
            com.uniandes.sport.repositories.EventCacheRepository.invalidateCache()
            com.uniandes.sport.repositories.EventCacheRepository.fetchEventsIfNeeded(forceRefresh = true)
            onSuccess()
        }.addOnFailureListener { e ->
            Log.e("PlayVM", "Transaction FAILED for joinEvent: ${e.message}")
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
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            onError(Exception("User not authenticated"))
            return
        }

        try {
            Log.d("PlayVM", "Starting createEvent for $title...")
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
            )
            
            // Atómicamente crear el evento y el primer miembro usando un Batch
            val eventDoc = db.collection("events").document()
            event.id = eventDoc.id
            val memberDoc = eventDoc.collection("members").document(uid)
            val batch = db.batch()
            
            batch.set(eventDoc, event)
            val organizer = com.uniandes.sport.models.MatchMember(
                userId = uid,
                displayName = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "Organizer",
                joinedAt = System.currentTimeMillis(),
                role = "organizer"
            )
            batch.set(memberDoc, organizer)
            
            Log.d("PlayVM", "Committing batch for event creation...")
            batch.commit()
                .addOnSuccessListener { 
                    Log.d("PlayVM", "Batch commit SUCCESS: Event and Organizer created.")
                    _joinedEventIds.value = _joinedEventIds.value + event.id
                    com.uniandes.sport.repositories.EventCacheRepository.invalidateCache()
                    com.uniandes.sport.repositories.EventCacheRepository.fetchEventsIfNeeded(forceRefresh = true)
                    onSuccess() 
                }
                .addOnFailureListener { e ->
                    Log.e("PlayVM", "Batch commit FAILED: ${e.message}")
                    onError(e) 
                }
        } catch (e: Exception) {
            Log.e("PlayVM", "EXCEPTION in createEvent: ${e.message}")
            onError(e)
        }
    }

    override fun kickMember(eventId: String, userId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val docRef = db.collection("events").document(eventId)
        val memberRef = docRef.collection("members").document(userId)

        db.runTransaction { transaction ->
            transaction.delete(memberRef)
            transaction.update(docRef, "membersCount", com.google.firebase.firestore.FieldValue.increment(-1))
            Log.d("PlayVM", "Transaction: Member $userId removed and counter decremented.")
        }.addOnSuccessListener {
            com.uniandes.sport.repositories.EventCacheRepository.invalidateCache()
            com.uniandes.sport.repositories.EventCacheRepository.fetchEventsIfNeeded(forceRefresh = true)
            onSuccess()
        }.addOnFailureListener { onError(it as? Exception ?: Exception(it.message)) }
    }

    override fun onCleared() {
        super.onCleared()
        joinedEventsListener?.remove()
    }
}
