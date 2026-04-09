package com.uniandes.sport.viewmodels.profesores

import androidx.lifecycle.ViewModel
import com.uniandes.sport.models.Profesor
import com.uniandes.sport.models.Review
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging

class FirestoreProfesoresViewModel : ViewModel(), ProfesoresViewModelInterface {

    private val db = FirebaseFirestore.getInstance()

    private val _profesores = MutableStateFlow<List<Profesor>>(emptyList())
    override val profesores: StateFlow<List<Profesor>> = _profesores.asStateFlow()

    private val _reviews = MutableStateFlow<List<Review>>(emptyList())
    override val reviews: StateFlow<List<Review>> = _reviews.asStateFlow()

    private val _bookingRequests = MutableStateFlow<List<com.uniandes.sport.models.BookingRequest>>(emptyList())
    override val bookingRequests: StateFlow<List<com.uniandes.sport.models.BookingRequest>> = _bookingRequests.asStateFlow()

    private var cachedProfesores: List<Profesor>? = null
    private var reviewsListener: ListenerRegistration? = null
    private var requestsListener: ListenerRegistration? = null

    override fun fetchProfesores(
        onSuccess: (List<Profesor>) -> Unit, 
        onFailure: (Exception) -> Unit
    ) {
        // 1. RAM (L1)
        if (cachedProfesores != null && cachedProfesores!!.isNotEmpty()) {
            Log.d("FirestoreProfesores", "Fetching from L1 Cache (RAM)")
            _profesores.value = cachedProfesores!!
            onSuccess(cachedProfesores!!)
            return
        }

        // 2. Disco / Cache Local (L2)
        db.collection("profesores")
            .get(com.google.firebase.firestore.Source.CACHE)
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val list = snapshot.mapNotNull { doc ->
                        try {
                            doc.toObject(Profesor::class.java).apply { id = doc.id }
                        } catch (e: Exception) { null }
                    }
                    if (list.isNotEmpty()) {
                        Log.d("FirestoreProfesores", "Fetching from L2 Cache (Disk)")
                        cachedProfesores = list
                        _profesores.value = list
                        onSuccess(list)
                        
                        // L3 Background update to keep L1/L2 fresh without blocking UI
                        fetchProfesoresFromServerBackground({}, {})
                    } else {
                        fetchProfesoresFromServerBackground(onSuccess, onFailure)
                    }
                } else {
                    fetchProfesoresFromServerBackground(onSuccess, onFailure)
                }
            }
            .addOnFailureListener {
                fetchProfesoresFromServerBackground(onSuccess, onFailure)
            }
    }

    override fun refreshProfesores(onComplete: () -> Unit) {
        cachedProfesores = null
        fetchProfesoresFromServerBackground(
            onSuccess = { onComplete() },
            onFailure = { onComplete() }
        )
    }

    private fun fetchProfesoresFromServerBackground(
        onSuccess: (List<Profesor>) -> Unit, 
        onFailure: (Exception) -> Unit
    ) {
        Log.d("FirestoreProfesores", "Fetching from L3 (Cloud Server)")
        db.collection("profesores")
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { snapshot ->
                val list = snapshot.mapNotNull { doc ->
                    try {
                        val p = doc.toObject(Profesor::class.java).apply { id = doc.id }
                        // Automated Database Migration + RAM fix
                        if (p.disponibilidad == "A convenir") {
                            p.disponibilidad = "To be agreed"
                            doc.reference.update("disponibilidad", "To be agreed")
                        }
                        p
                    } catch (e: Exception) { null }
                }

                cachedProfesores = list
                _profesores.value = list
                onSuccess(list)
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreProfesores", "Error fetching from server", exception)
                onFailure(exception)
            }
    }

    override fun fetchReviews(profesorId: String) {
        // Remover el listener anterior si el usuario ve otro profesor
        reviewsListener?.remove()

        reviewsListener = db.collection("profesores").document(profesorId)
            .collection("reviews")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FirestoreProfesores", "Listen reviews failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val reviewsList = mutableListOf<Review>()
                    for (doc in snapshot) {
                        try {
                            val review = doc.toObject(Review::class.java)
                            review.id = doc.id
                            reviewsList.add(review)
                        } catch (parseError: Exception) {
                            Log.e("FirestoreProfesores", "Error parsing review ${doc.id}", parseError)
                        }
                    }
                    _reviews.value = reviewsList
                }
            }
    }

    override fun fetchBookingRequestsBySport(sport: String) {
        requestsListener?.remove()
        requestsListener = db.collection("coach_requests")
            .whereEqualTo("sport", sport)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FirestoreProfesores", "Listen booking requests failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val list = snapshot.map { doc ->
                        doc.toObject(com.uniandes.sport.models.BookingRequest::class.java).apply { id = doc.id }
                    }
                    _bookingRequests.value = list.sortedByDescending { it.createdAt }
                }
            }
    }

    override fun syncCoachingLeadsTopic(sport: String) {
        val topic = "sport_leads_${sport.lowercase().replace(Regex("[^a-z0-9]"), "_")}"
        try {
            Log.d("FirestoreProfesores", "Subscribing to topic: $topic")
            Firebase.messaging.subscribeToTopic(topic)
                .addOnSuccessListener { Log.d("FirestoreProfesores", "Successfully subscribed to $topic") }
                .addOnFailureListener { e -> Log.e("FirestoreProfesores", "Failed to subscribe to $topic", e) }
        } catch (e: Exception) {
            Log.e("FirestoreProfesores", "Error in syncCoachingLeadsTopic", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        reviewsListener?.remove()
        requestsListener?.remove()
    }

    override fun createProfesor(
        profesor: Profesor,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val documentRef = if (profesor.id.isNotEmpty()) {
            db.collection("profesores").document(profesor.id)
        } else {
            db.collection("profesores").document() // Auto-generate ID
        }

        // Update the ID in the object before saving if it was newly generated
        val profToSave = profesor.copy(id = documentRef.id)

        documentRef.set(profToSave)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreProfesores", "Error writing document", exception)
                onFailure(exception)
            }
    }

    override fun addReview(
        profesorId: String,
        review: Review,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val reviewRef = db.collection("profesores").document(profesorId)
            .collection("reviews").document()
            
        val reviewToSave = review.copy(id = reviewRef.id)

        db.runTransaction { transaction ->
            // 1. Read current prof to update ratings (Reads must come BEFORE writes)
            val profRef = db.collection("profesores").document(profesorId)
            val profSnapshot = transaction.get(profRef)
            
            val currentTotal = profSnapshot.getLong("totalReviews") ?: 0
            val currentRating = profSnapshot.getDouble("rating") ?: 0.0
            
            // Calculate new rating average
            val newTotal = currentTotal + 1
            val newRating = ((currentRating * currentTotal) + reviewToSave.rating) / newTotal
            
            // 2. Add review to subcollection (Write)
            transaction.set(reviewRef, reviewToSave)
            
            // 3. Update prof document (Write)
            transaction.update(profRef, "totalReviews", newTotal)
            transaction.update(profRef, "rating", newRating)
        }.addOnSuccessListener {
            val currentList = _profesores.value.toMutableList()
            val idx = currentList.indexOfFirst { it.id == profesorId }
            if (idx != -1) {
                // Calculate again to avoid another DB read just for UI, using the new values
                val p = currentList[idx]
                val currentTotal = p.totalReviews
                val currentRating = p.rating
                val updatedTotal = currentTotal + 1
                val updatedRating = ((currentRating * currentTotal) + reviewToSave.rating) / updatedTotal
                
                currentList[idx] = p.copy(totalReviews = updatedTotal, rating = updatedRating)
                _profesores.value = currentList
                cachedProfesores = currentList
            }
            onSuccess()
        }.addOnFailureListener { e ->
            Log.e("FirestoreProfesores", "Error adding review", e)
            onFailure(e)
        }
    }

    override fun syncReviewsCount(
        profesorId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val profRef = db.collection("profesores").document(profesorId)
        val reviewsRef = profRef.collection("reviews")

        reviewsRef.get(com.google.firebase.firestore.Source.SERVER).addOnSuccessListener { snapshot ->
            val realCount = snapshot.size()
            var totalRating = 0.0

            for (doc in snapshot) {
                totalRating += doc.getDouble("rating") ?: 0.0
            }

            val newRating = if (realCount > 0) totalRating / realCount else 0.0

            profRef.update(
                mapOf(
                    "totalReviews" to realCount,
                    "rating" to newRating
                )
            ).addOnSuccessListener {
                val currentList = _profesores.value.toMutableList()
                val idx = currentList.indexOfFirst { it.id == profesorId }
                if (idx != -1) {
                    val p = currentList[idx]
                    currentList[idx] = p.copy(totalReviews = realCount, rating = newRating)
                    _profesores.value = currentList
                    cachedProfesores = currentList
                }
                onSuccess()
            }.addOnFailureListener { e ->
                Log.e("FirestoreProfesores", "Error syncing reviews on profRef update", e)
                onFailure(e)
            }
        }.addOnFailureListener { e ->
            Log.e("FirestoreProfesores", "Error syncing reviews on get", e)
            onFailure(e)
        }
    }

    override fun acceptBookingRequest(
        requestId: String,
        professorId: String,
        professorName: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("coach_requests").document(requestId)
            .update(
                mapOf(
                    "status" to "accepted",
                    "targetProfesorId" to professorId,
                    "targetProfesorName" to professorName
                )
            )
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreProfesores", "Error accepting booking request", e)
                onFailure(e)
            }
    }
}
