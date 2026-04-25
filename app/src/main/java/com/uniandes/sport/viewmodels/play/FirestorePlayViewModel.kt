package com.uniandes.sport.viewmodels.play

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.uniandes.sport.viewmodels.log.LogViewModelInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.uniandes.sport.data.local.PendingOpenMatchPayload
import com.uniandes.sport.data.local.PendingOpenMatchStore
import com.uniandes.sport.models.Event
import com.uniandes.sport.models.MatchMember
import com.uniandes.sport.models.Track
import com.uniandes.sport.patterns.event.AllActiveEventsStrategy
import com.uniandes.sport.patterns.event.EventFilterStrategy
import com.uniandes.sport.patterns.event.MultiSportFilterStrategy
import com.uniandes.sport.workers.OpenMatchSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestorePlayViewModel(
    private val logViewModel: LogViewModelInterface? = null
) : ViewModel(), PlayViewModelInterface {
    private val db = FirebaseFirestore.getInstance()
    private val appContext: Context?
        get() = try {
            FirebaseApp.getInstance().applicationContext
        } catch (_: Exception) {
            null
        }

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

    private val _selectedSports = MutableStateFlow<Set<String>>(emptySet())
    override val selectedSports: StateFlow<Set<String>> = _selectedSports.asStateFlow()

    private val _joinedEventIds = MutableStateFlow<Set<String>>(emptySet())
    override val joinedEventIds: StateFlow<Set<String>> = _joinedEventIds.asStateFlow()

    private val _myTracksByEventId = MutableStateFlow<Map<String, Track>>(emptyMap())
    override val myTracksByEventId: StateFlow<Map<String, Track>> = _myTracksByEventId.asStateFlow()

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

    override fun fetchMyTracksForEvents(eventIds: List<String>) {
        val uid = currentUserId ?: run {
            _myTracksByEventId.value = emptyMap()
            return
        }

        val distinctIds = eventIds.distinct()
        if (distinctIds.isEmpty()) {
            _myTracksByEventId.value = emptyMap()
            return
        }

        viewModelScope.launch {
            val result = mutableMapOf<String, Track>()
            distinctIds.forEach { eventId ->
                try {
                    val doc = db.collection("events")
                        .document(eventId)
                        .collection("tracks")
                        .document(uid)
                        .get()
                        .await()

                    if (doc.exists()) {
                        val track = doc.toObject(Track::class.java)
                        if (track != null) {
                            result[eventId] = track
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PlayVM", "Error loading track for event $eventId", e)
                }
            }
            _myTracksByEventId.value = result
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

    override fun toggleSportFilter(sport: String) {
        val current = _selectedSports.value
        val normalized = sport.lowercase()
        val isSelecting = !current.contains(normalized)
        
        val next = if (current.contains(normalized)) {
            current - normalized
        } else {
            current + normalized
        }
        _selectedSports.value = next
        
        // Log interaction if selecting a new sport
        if (isSelecting) {
            logViewModel?.log(
                screen = "PlayScreen",
                action = "SPORT_FILTER_APPLIED",
                params = mapOf("sport_category" to normalized)
            )
        }

        currentFilterStrategy = if (next.isNotEmpty()) {
            MultiSportFilterStrategy(next)
        } else {
            AllActiveEventsStrategy()
        }
        applyCurrentStrategy()
        updateInProgressEvents()
        updateFinishedEvents()
    }

    override fun clearSportFilters() {
        _selectedSports.value = emptySet()
        currentFilterStrategy = AllActiveEventsStrategy()
        applyCurrentStrategy()
        updateInProgressEvents()
        updateFinishedEvents()
    }

    private fun applyCurrentStrategy() {
        _events.value = currentFilterStrategy.filter(_rawEvents.value)
    }

    private fun updateInProgressEvents() {
        val now = com.google.firebase.Timestamp.now()
        val oneHourAgo = com.google.firebase.Timestamp((now.seconds - 3600).coerceAtLeast(0L), now.nanoseconds)
        val sports = _selectedSports.value
        _inProgressEvents.value = _rawEvents.value
            .filter {
                val scheduledAt = it.scheduledAt ?: com.google.firebase.Timestamp(0, 0)
                val isSportMatch = sports.isEmpty() || sports.contains(it.sport.lowercase())
                
                val isInProgress = it.status == "active" &&
                    scheduledAt <= now &&
                    (
                        (it.finishedAt != null && it.finishedAt!! > now) ||
                        (it.finishedAt == null && scheduledAt >= oneHourAgo)
                    )
                
                isInProgress && isSportMatch
            }
            .sortedByDescending { it.scheduledAt }
    }

    private fun updateFinishedEvents() {
        val now = com.google.firebase.Timestamp.now()
        val oneHourAgo = com.google.firebase.Timestamp((now.seconds - 3600).coerceAtLeast(0L), now.nanoseconds)
        val sports = _selectedSports.value
        _finishedEvents.value = _rawEvents.value
            .filter {
                val isSportMatch = sports.isEmpty() || sports.contains(it.sport.lowercase())
                val isFinished = it.status == "finished" || 
                                (it.finishedAt != null && it.finishedAt!! <= now) ||
                                (it.finishedAt == null && (it.scheduledAt ?: com.google.firebase.Timestamp(0, 0)) < oneHourAgo)
                
                it.status != "cancelled" && isFinished && isSportMatch
            }
            .sortedByDescending { it.scheduledAt }
    }

    override fun submitTrack(
        eventId: String, 
        text: String, 
        rating: Int, 
        participated: Boolean, 
        source: String, 
        onSuccess: () -> Unit, 
        onError: (Exception) -> Unit
    ) {
        val cleanText = text.trim()
        
        // Si participó, el texto y rating son obligatorios para la IA
        if (participated) {
            if (cleanText.isBlank()) {
                onError(IllegalArgumentException("Please describe your activity to track progress"))
                return
            }
            if (rating !in 1..5) {
                onError(IllegalArgumentException("Rating must be between 1 and 5"))
                return
            }
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
            "text" to cleanText,
            "rating" to rating,
            "participated" to participated,
            "source" to source,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        val trackRef = db.collection("events")
            .document(eventId)
            .collection("tracks")
            .document(uid)

        db.runTransaction { transaction ->
            val existing = transaction.get(trackRef)
            val data = payload.toMutableMap()
            if (!existing.exists()) {
                data["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
            }
            transaction.set(trackRef, data, com.google.firebase.firestore.SetOptions.merge())
        }
            .addOnSuccessListener {
                val existingAnalysis = _myTracksByEventId.value[eventId]?.aiAnalysis ?: emptyMap()
                _myTracksByEventId.value = _myTracksByEventId.value + (
                    eventId to Track(
                        eventId = eventId,
                        userId = uid,
                        userEmail = user.email ?: "",
                        text = cleanText,
                        rating = rating,
                        participated = participated,
                        source = source,
                        aiAnalysis = existingAnalysis // Preservamos el pasado para el Sistema de Deltas
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

    override fun joinEvent(eventId: String, userId: String, sport: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
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
            
            // Log telemetry
            logViewModel?.log(
                screen = "PlayScreen",
                action = "join_sport_event",
                params = mapOf("sport_category" to sport)
            )

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
        finishedAt: java.util.Date?,
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

        if (!isNetworkConnected()) {
            queuePendingOpenMatch(
                uid = uid,
                title = title,
                description = description,
                location = location,
                sport = sport,
                modality = modality,
                scheduledAt = scheduledAt,
                finishedAt = finishedAt,
                skillLevel = skillLevel,
                maxParticipants = maxParticipants,
                shouldJoin = shouldJoin,
                onSuccess = onSuccess,
                onError = onError
            )
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
                finishedAt = finishedAt,
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
                    
                    // Log telemetry
                    if (shouldJoin) {
                        logViewModel?.log(
                            screen = "PlayScreen",
                            action = "join_sport_event",
                            params = mapOf(
                                "sport_category" to sport,
                                "modality" to modality,
                                "max_participants" to maxParticipants.toString()
                            )
                        )
                        _joinedEventIds.value = _joinedEventIds.value + event.id
                    }
                    com.uniandes.sport.repositories.EventCacheRepository.invalidateCache()
                    com.uniandes.sport.repositories.EventCacheRepository.fetchEventsIfNeeded(forceRefresh = true)
                    onSuccess() 
                }
                .addOnFailureListener { e ->
                    Log.e("PlayVM", "Batch commit FAILED: ${e.message}")

                    if (!isNetworkConnected()) {
                        queuePendingOpenMatch(
                            uid = uid,
                            title = title,
                            description = description,
                            location = location,
                            sport = sport,
                            modality = modality,
                            scheduledAt = scheduledAt,
                            finishedAt = finishedAt,
                            skillLevel = skillLevel,
                            maxParticipants = maxParticipants,
                            shouldJoin = shouldJoin,
                            onSuccess = onSuccess,
                            onError = onError
                        )
                    } else {
                        onError(e)
                    }
                }
        } catch (e: Exception) {
            Log.e("PlayVM", "EXCEPTION in createEvent: ${e.message}")
            onError(e)
        }
    }

    override fun updateEvent(
        eventId: String,
        title: String,
        description: String,
        location: String,
        sport: String,
        scheduledAt: java.util.Date,
        finishedAt: java.util.Date?,
        skillLevel: String,
        maxParticipants: Long,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val updates = mutableMapOf<String, Any>(
            "title" to title,
            "description" to description,
            "location" to location,
            "sport" to sport.lowercase(),
            "scheduledAt" to com.google.firebase.Timestamp(scheduledAt),
            "maxParticipants" to maxParticipants,
            "metadata.skillLevel" to skillLevel,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )
        
        if (finishedAt != null) {
            updates["finishedAt"] = com.google.firebase.Timestamp(finishedAt)
        } else {
            updates["finishedAt"] = com.google.firebase.firestore.FieldValue.delete()
        }

        db.collection("events").document(eventId)
            .update(updates)
            .addOnSuccessListener {
                com.uniandes.sport.repositories.EventCacheRepository.invalidateCache()
                com.uniandes.sport.repositories.EventCacheRepository.fetchEventsIfNeeded(forceRefresh = true)
                onSuccess()
            }
            .addOnFailureListener { onError(it as? Exception ?: Exception(it.message)) }
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

    override fun updateTrackAiAnalysis(eventId: String, userId: String, analysis: Map<String, Double>) {
        if (eventId.isBlank() || userId.isBlank()) return
        
        val trackRef = db.collection("events")
            .document(eventId)
            .collection("tracks")
            .document(userId)

        trackRef.update("aiAnalysis", analysis)
            .addOnSuccessListener {
                Log.d("PlayVM", "AI Analysis updated for event $eventId and user $userId")
                
                // Actualizar el estado local si es necesario
                val currentTracks = _myTracksByEventId.value.toMutableMap()
                val existing = currentTracks[eventId]
                if (existing != null) {
                    currentTracks[eventId] = existing.copy(aiAnalysis = analysis)
                    _myTracksByEventId.value = currentTracks
                }
            }
            .addOnFailureListener { e ->
                Log.e("PlayVM", "Error updating AI Analysis for event $eventId", e)
            }
    }

    override fun onCleared() {
        super.onCleared()
        joinedEventsListener?.remove()
    }

    private fun queuePendingOpenMatch(
        uid: String,
        title: String,
        description: String,
        location: String,
        sport: String,
        modality: String,
        scheduledAt: java.util.Date,
        finishedAt: java.util.Date?,
        skillLevel: String,
        maxParticipants: Long,
        shouldJoin: Boolean,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val context = appContext
        if (context == null) {
            onError(Exception("No internet and local queue unavailable"))
            return
        }

        try {
            val pending = PendingOpenMatchPayload(
                localId = UUID.randomUUID().toString(),
                title = title,
                description = description,
                location = location,
                sport = sport,
                modality = modality,
                scheduledAtMillis = scheduledAt.time,
                finishedAtMillis = finishedAt?.time,
                skillLevel = skillLevel,
                maxParticipants = maxParticipants,
                shouldJoin = shouldJoin,
                createdBy = uid
            )

            PendingOpenMatchStore.enqueue(context, pending)
            schedulePendingOpenMatchSync(context)
            onSuccess()
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun schedulePendingOpenMatchSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<OpenMatchSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    private fun isNetworkConnected(): Boolean {
        val context = appContext ?: return false
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }

    companion object {
        fun provideFactory(logViewModel: LogViewModelInterface): androidx.lifecycle.ViewModelProvider.Factory = 
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return FirestorePlayViewModel(logViewModel) as T
                }
            }
    }
}
