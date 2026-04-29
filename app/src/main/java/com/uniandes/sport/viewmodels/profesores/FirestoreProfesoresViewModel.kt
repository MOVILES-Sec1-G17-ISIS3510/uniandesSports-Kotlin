package com.uniandes.sport.viewmodels.profesores

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.uniandes.sport.data.local.ProfesoresLocalRepository
import com.uniandes.sport.models.BookingRequest
import com.uniandes.sport.models.Profesor
import com.uniandes.sport.models.Review
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreProfesoresViewModel : ViewModel(), ProfesoresViewModelInterface {

    private val db = FirebaseFirestore.getInstance()

    private val appContext
        get() = try {
            FirebaseApp.getInstance().applicationContext
        } catch (_: Exception) {
            null
        }

    private val localRepository: ProfesoresLocalRepository?
        get() = appContext?.let { ProfesoresLocalRepository.getInstance(it) }

    private val _profesores = MutableStateFlow<List<Profesor>>(emptyList())
    override val profesores: StateFlow<List<Profesor>> = _profesores.asStateFlow()

    private val _reviews = MutableStateFlow<List<Review>>(emptyList())
    override val reviews: StateFlow<List<Review>> = _reviews.asStateFlow()

    private val _bookingRequests = MutableStateFlow<List<BookingRequest>>(emptyList())
    override val bookingRequests: StateFlow<List<BookingRequest>> = _bookingRequests.asStateFlow()

    private var cachedProfesores: List<Profesor>? = null
    private var reviewsListener: ListenerRegistration? = null
    private var requestsListener: ListenerRegistration? = null
    private var profesoresCacheJob: Job? = null
    private var reviewsCacheJob: Job? = null
    private var requestsCacheJob: Job? = null

    override fun fetchProfesores(
        onSuccess: (List<Profesor>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        observeLocalProfesores()

        cachedProfesores?.takeIf { it.isNotEmpty() }?.let { memoryCache ->
            _profesores.value = memoryCache
            onSuccess(memoryCache)
            return
        }

        viewModelScope.launch {
            try {
                val firestoreCache = withContext(Dispatchers.IO) {
                    db.collection("profesores")
                        .get(com.google.firebase.firestore.Source.CACHE)
                        .await()
                        .mapNotNull { doc ->
                            runCatching {
                                doc.toObject(Profesor::class.java).apply { id = doc.id }
                            }.getOrNull()
                        }
                }

                when {
                    firestoreCache.isNotEmpty() -> {
                        cachedProfesores = firestoreCache
                        _profesores.value = firestoreCache
                        withContext(Dispatchers.IO) {
                            localRepository?.replaceProfesores(firestoreCache)
                        }
                        onSuccess(firestoreCache)
                        launch(Dispatchers.IO) { syncFromServer() }
                    }

                    else -> {
                        val localCache = withContext(Dispatchers.IO) {
                            localRepository?.getCachedProfesores().orEmpty()
                        }
                        if (localCache.isNotEmpty()) {
                            cachedProfesores = localCache
                            _profesores.value = localCache
                            onSuccess(localCache)
                        }
                        syncFromServer()
                        onSuccess(_profesores.value)
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfesoresVM", "Error loading profesores", e)
                viewModelScope.launch(Dispatchers.IO) { syncFromServer() }
                onFailure(e)
            }
        }
    }

    private suspend fun syncFromServer() = withContext(Dispatchers.IO) {
        try {
            val snapshot = db.collection("profesores")
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()

            val list = snapshot.mapNotNull { doc ->
                runCatching {
                    val profesor = doc.toObject(Profesor::class.java).apply { id = doc.id }
                    if (profesor.disponibilidad == "A convenir") {
                        profesor.disponibilidad = "To be agreed"
                        doc.reference.update("disponibilidad", "To be agreed")
                    }
                    profesor
                }.getOrNull()
            }

            cachedProfesores = list
            _profesores.value = list
            localRepository?.replaceProfesores(list)
        } catch (e: Exception) {
            Log.e("ProfesoresVM", "Cloud sync failed", e)
        }
    }

    override fun refreshProfesores(onComplete: () -> Unit) {
        cachedProfesores = null
        viewModelScope.launch {
            syncFromServer()
            onComplete()
        }
    }

    override fun fetchReviews(profesorId: String) {
        observeLocalReviews(profesorId)
        reviewsListener?.remove()
        reviewsListener = db.collection("profesores").document(profesorId)
            .collection("reviews")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null) {
                    val list = snapshot.mapNotNull { doc ->
                        runCatching {
                            doc.toObject(Review::class.java).apply { id = doc.id }
                        }.getOrNull()
                    }
                    _reviews.value = list
                    viewModelScope.launch(Dispatchers.IO) {
                        localRepository?.replaceReviews(profesorId, list)
                    }
                }
            }
    }

    override fun fetchBookingRequestsBySport(sport: String) {
        observeLocalBookingRequests(sport)
        requestsListener?.remove()
        requestsListener = db.collection("coach_requests")
            .whereEqualTo("sport", sport)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null) {
                    val list = snapshot.map { doc ->
                        doc.toObject(BookingRequest::class.java).apply { id = doc.id }
                    }.sortedByDescending { it.createdAt }

                    _bookingRequests.value = list
                    viewModelScope.launch(Dispatchers.IO) {
                        localRepository?.replaceBookingRequestsBySport(sport, list)
                    }
                }
            }
    }

    override fun syncCoachingLeadsTopic(sport: String) {
        val topic = "sport_leads_${sport.lowercase().replace(Regex("[^a-z0-9]"), "_")}"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Firebase.messaging.subscribeToTopic(topic).await()
                Log.d("FCM", "Subscribed to $topic")
            } catch (e: Exception) {
                Log.e("FCM", "Subscribe failed", e)
            }
        }
    }

    override fun createProfesor(
        profesor: Profesor,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val documentRef = if (profesor.id.isNotEmpty()) {
                    db.collection("profesores").document(profesor.id)
                } else {
                    db.collection("profesores").document()
                }
                val profToSave = profesor.copy(id = documentRef.id)
                documentRef.set(profToSave).await()
                localRepository?.upsertProfesor(profToSave)

                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onFailure(e) }
            }
        }
    }

    override fun addReview(
        profesorId: String,
        review: Review,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val reviewsCollection = db.collection("profesores").document(profesorId).collection("reviews")
                val existing = reviewsCollection
                    .whereEqualTo("estudiante", review.estudiante)
                    .limit(1)
                    .get()
                    .await()

                if (!existing.isEmpty) {
                    withContext(Dispatchers.Main) { onFailure(Exception("Already reviewed")) }
                    return@launch
                }

                val reviewRef = reviewsCollection.document()
                val reviewToSave = review.copy(id = reviewRef.id, rating = review.rating.coerceIn(1, 5))

                db.runTransaction { transaction ->
                    val profRef = db.collection("profesores").document(profesorId)
                    val profSnapshot = transaction.get(profRef)
                    val currentTotal = profSnapshot.getLong("totalReviews") ?: 0
                    val currentRating = profSnapshot.getDouble("rating") ?: 0.0
                    val newTotal = currentTotal + 1
                    val newRating = ((currentRating * currentTotal) + reviewToSave.rating) / newTotal

                    transaction.set(reviewRef, reviewToSave)
                    transaction.update(profRef, "totalReviews", newTotal)
                    transaction.update(profRef, "rating", newRating)
                }.await()

                launch(Dispatchers.IO) { syncReviewsCountInternal(profesorId) }
                localRepository?.upsertReview(profesorId, reviewToSave)

                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onFailure(e) }
            }
        }
    }

    private suspend fun syncReviewsCountInternal(profesorId: String) = withContext(Dispatchers.IO) {
        try {
            val profRef = db.collection("profesores").document(profesorId)
            val snapshot = profRef.collection("reviews")
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()
            val realCount = snapshot.size()
            val totalRating = snapshot.sumOf { it.getDouble("rating") ?: 0.0 }
            val newRating = if (realCount > 0) totalRating / realCount else 0.0
            profRef.update(mapOf("totalReviews" to realCount, "rating" to newRating)).await()
        } catch (e: Exception) {
            Log.e("ProfesoresVM", "Review count sync failed", e)
        }
    }

    override fun syncReviewsCount(
        profesorId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profRef = db.collection("profesores").document(profesorId)
                val snapshot = profRef.collection("reviews")
                    .get(com.google.firebase.firestore.Source.SERVER)
                    .await()
                val realCount = snapshot.size()
                val totalRating = snapshot.sumOf { it.getDouble("rating") ?: 0.0 }
                val newRating = if (realCount > 0) totalRating / realCount else 0.0

                profRef.update(mapOf("totalReviews" to realCount, "rating" to newRating)).await()
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onFailure(e) }
            }
        }
    }

    override fun acceptBookingRequest(
        requestId: String,
        professorId: String,
        professorName: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.collection("coach_requests").document(requestId)
                    .update(
                        mapOf(
                            "status" to "accepted",
                            "targetProfesorId" to professorId,
                            "targetProfesorName" to professorName
                        )
                    ).await()

                val updated = _bookingRequests.value.map { request ->
                    if (request.id == requestId) {
                        request.copy(
                            targetProfesorId = professorId,
                            targetProfesorName = professorName,
                            status = "accepted"
                        )
                    } else {
                        request
                    }
                }
                _bookingRequests.value = updated
                localRepository?.upsertBookingRequests(updated)

                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onFailure(e) }
            }
        }
    }

    private fun observeLocalProfesores() {
        if (profesoresCacheJob != null) return
        val repo = localRepository ?: return
        profesoresCacheJob = viewModelScope.launch {
            repo.observeProfesores().collect { localList ->
                if (localList.isNotEmpty()) {
                    cachedProfesores = localList
                    _profesores.value = localList
                }
            }
        }
    }

    private fun observeLocalReviews(profesorId: String) {
        reviewsCacheJob?.cancel()
        val repo = localRepository ?: return
        reviewsCacheJob = viewModelScope.launch {
            repo.observeReviews(profesorId).collect { localList ->
                if (localList.isNotEmpty() || _reviews.value.isEmpty()) {
                    _reviews.value = localList
                }
            }
        }
    }

    private fun observeLocalBookingRequests(sport: String) {
        requestsCacheJob?.cancel()
        val repo = localRepository ?: return
        requestsCacheJob = viewModelScope.launch {
            repo.observeBookingRequestsBySport(sport).collect { localList ->
                if (localList.isNotEmpty() || _bookingRequests.value.isEmpty()) {
                    _bookingRequests.value = localList
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        reviewsListener?.remove()
        requestsListener?.remove()
        profesoresCacheJob?.cancel()
        reviewsCacheJob?.cancel()
        requestsCacheJob?.cancel()
    }
}
