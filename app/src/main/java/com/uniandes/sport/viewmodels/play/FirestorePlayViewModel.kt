package com.uniandes.sport.viewmodels.play

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.uniandes.sport.models.Event
import com.uniandes.sport.models.MatchMember
import com.uniandes.sport.models.OpenMatchReview
import com.uniandes.sport.patterns.event.AllActiveEventsStrategy
import com.uniandes.sport.patterns.event.EventFilterStrategy
import com.uniandes.sport.patterns.event.SportFilterStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirestorePlayViewModel : ViewModel(), PlayViewModelInterface {
    private val db = FirebaseFirestore.getInstance()

    private val _rawEvents = MutableStateFlow<List<Event>>(emptyList())
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    override val events: StateFlow<List<Event>> = _events.asStateFlow()

    private val _inProgressEvents = MutableStateFlow<List<Event>>(emptyList())
    override val inProgressEvents: StateFlow<List<Event>> = _inProgressEvents.asStateFlow()

    private val _finishedEvents = MutableStateFlow<List<Event>>(emptyList())
    override val finishedEvents: StateFlow<List<Event>> = _finishedEvents.asStateFlow()

    private val _members = MutableStateFlow<List<com.uniandes.sport.models.MatchMember>>(emptyList())
    override val members: StateFlow<List<com.uniandes.sport.models.MatchMember>> = _members.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedSport = MutableStateFlow<String?>(null)
    override val selectedSport: StateFlow<String?> = _selectedSport.asStateFlow()

    private val _joinedEventIds = MutableStateFlow<Set<String>>(emptySet())
    override val joinedEventIds: StateFlow<Set<String>> = _joinedEventIds.asStateFlow()

    private val _myReviewsByEventId = MutableStateFlow<Map<String, OpenMatchReview>>(emptyMap())
    override val myReviewsByEventId: StateFlow<Map<String, OpenMatchReview>> = _myReviewsByEventId.asStateFlow()

    private var joinedEventsListener: ListenerRegistration? = null

    override val currentUserId: String?
        get() = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    private var currentFilterStrategy: EventFilterStrategy = AllActiveEventsStrategy()

    init {
        viewModelScope.launch {
            com.uniandes.sport.repositories.EventCacheRepository.cachedEvents.collect { list ->
                _rawEvents.value = list
                applyCurrentStrategy()
                updateInProgressEvents()
                updateFinishedEvents()
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

    override fun fetchMyReviewsForEvents(eventIds: List<String>) {
        val uid = currentUserId ?: run {
            _myReviewsByEventId.value = emptyMap()
            return
        }

        val distinctIds = eventIds.distinct()
        if (distinctIds.isEmpty()) {
            _myReviewsByEventId.value = emptyMap()
            return
        }

        viewModelScope.launch {
            val result = mutableMapOf<String, OpenMatchReview>()
            distinctIds.forEach { eventId ->
                try {
                    val doc = db.collection("events")
                        .document(eventId)
                        .collection("reviews")
                        .document(uid)
                        .get()
                        .await()

                    if (doc.exists()) {
                        val review = doc.toObject(OpenMatchReview::class.java)
                        if (review != null) {
                            result[eventId] = review
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PlayVM", "Error loading review for event $eventId", e)
                }
            }
            _myReviewsByEventId.value = result
        }
    }

    override fun fetchEventMembersOnce(eventId: String, onSuccess: (List<MatchMember>) -> Unit, onError: (Exception) -> Unit) {
        db.collection("events")
            .document(eventId)
            .collection("members")
            .orderBy("joinedAt")
            .get()
            .addOnSuccessListener { snapshot ->
                onSuccess(snapshot.toObjects(MatchMember::class.java))
            }
            .addOnFailureListener { e ->
                onError(e as? Exception ?: Exception(e.message))
            }
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

    private fun updateInProgressEvents() {
        val now = com.google.firebase.Timestamp.now()
        val oneHourAgo = com.google.firebase.Timestamp((now.seconds - 3600).coerceAtLeast(0L), now.nanoseconds)
        _inProgressEvents.value = _rawEvents.value
            .filter {
                it.status == "active" &&
                    (it.scheduledAt ?: com.google.firebase.Timestamp(0, 0)) <= now &&
                    (it.scheduledAt ?: com.google.firebase.Timestamp(0, 0)) >= oneHourAgo
            }
            .sortedByDescending { it.scheduledAt }
    }

    private fun updateFinishedEvents() {
        val now = com.google.firebase.Timestamp.now()
        val oneHourAgo = com.google.firebase.Timestamp((now.seconds - 3600).coerceAtLeast(0L), now.nanoseconds)
        _finishedEvents.value = _rawEvents.value
            .filter {
                it.status == "active" &&
                    (it.scheduledAt ?: com.google.firebase.Timestamp(0, 0)) < oneHourAgo
            }
            .sortedByDescending { it.scheduledAt }
    }

    override fun submitReview(eventId: String, reviewText: String, rating: Int, attendanceByUserId: Map<String, Boolean>, source: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val text = reviewText.trim()
        if (text.isBlank()) {
            onError(IllegalArgumentException("Review text cannot be empty"))
            return
        }
        if (rating !in 1..5) {
            onError(IllegalArgumentException("Rating must be between 1 and 5"))
            return
        }

        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val uid = user?.uid
        if (uid.isNullOrBlank()) {
            onError(IllegalStateException("User not authenticated"))
            return
        }
        val payload = mapOf(
            "eventId" to eventId,
            "userId" to uid,
            "userEmail" to (user?.email ?: ""),
            "text" to text,
            "rating" to rating,
            "attendanceByUserId" to attendanceByUserId,
            "source" to source,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        val reviewRef = db.collection("events")
            .document(eventId)
            .collection("reviews")
            .document(uid)

        db.runTransaction { transaction ->
            val existing = transaction.get(reviewRef)
            val data = payload.toMutableMap()
            if (!existing.exists()) {
                data["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
            }
            transaction.set(reviewRef, data, com.google.firebase.firestore.SetOptions.merge())
        }
            .addOnSuccessListener {
                _myReviewsByEventId.value = _myReviewsByEventId.value + (
                    eventId to OpenMatchReview(
                        eventId = eventId,
                        userId = uid,
                        userEmail = user.email ?: "",
                        text = text,
                        rating = rating,
                        attendanceByUserId = attendanceByUserId,
                        source = source
                    )
                )
                onSuccess()
            }
            .addOnFailureListener { e -> onError(e as? Exception ?: Exception(e.message)) }
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

    override fun leaveEvent(eventId: String, userId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        // Semánticamente igual a kickMember pero para uno mismo
        kickMember(eventId, userId, onSuccess, onError)
    }

    override fun cancelEvent(eventId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        db.collection("events").document(eventId)
            .update("status", "cancelled")
            .addOnSuccessListener {
                com.uniandes.sport.repositories.EventCacheRepository.invalidateCache()
                com.uniandes.sport.repositories.EventCacheRepository.fetchEventsIfNeeded(forceRefresh = true)
                onSuccess()
            }
            .addOnFailureListener { onError(it as? Exception ?: Exception(it.message)) }
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
        shouldJoin: Boolean,
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
            val batch = db.batch()
            
            batch.set(eventDoc, event)
            
            if (shouldJoin) {
                val memberDoc = eventDoc.collection("members").document(uid)
                val organizer = com.uniandes.sport.models.MatchMember(
                    userId = uid,
                    displayName = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "Organizer",
                    joinedAt = System.currentTimeMillis(),
                    role = "organizer"
                )
                batch.set(memberDoc, organizer)
            }

            
            Log.d("PlayVM", "Committing batch for event creation...")
            batch.commit()
                .addOnSuccessListener { 
                    Log.d("PlayVM", "Batch commit SUCCESS: Event creation finished (shouldJoin=$shouldJoin).")
                    if (shouldJoin) {
                        _joinedEventIds.value = _joinedEventIds.value + event.id
                    }
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
