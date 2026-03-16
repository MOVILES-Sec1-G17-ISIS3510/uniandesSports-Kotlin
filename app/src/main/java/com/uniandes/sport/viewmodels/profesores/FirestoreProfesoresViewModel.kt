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

class FirestoreProfesoresViewModel : ViewModel(), ProfesoresViewModelInterface {

    private val db = FirebaseFirestore.getInstance()

    private val _profesores = MutableStateFlow<List<Profesor>>(emptyList())
    override val profesores: StateFlow<List<Profesor>> = _profesores.asStateFlow()

    private val _reviews = MutableStateFlow<List<Review>>(emptyList())
    override val reviews: StateFlow<List<Review>> = _reviews.asStateFlow()

    private var cachedProfesores: List<Profesor>? = null
    private var reviewsListener: ListenerRegistration? = null

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
                        doc.toObject(Profesor::class.java).apply { id = doc.id }
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

    override fun onCleared() {
        super.onCleared()
        reviewsListener?.remove()
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
            onSuccess()
        }.addOnFailureListener { e ->
            Log.e("FirestoreProfesores", "Error adding review", e)
            onFailure(e)
        }
    }
}
