package com.uniandes.sport.viewmodels.profesores

import androidx.lifecycle.ViewModel
import com.uniandes.sport.models.Profesor
import com.uniandes.sport.models.Review
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import android.util.Log

class FirestoreProfesoresViewModel : ViewModel(), ProfesoresViewModelInterface {

    private val db = FirebaseFirestore.getInstance()

    // RAM memory cache to avoid even disk cache lookups when navigating
    private var cachedProfesores: List<Profesor>? = null

    override fun fetchProfesores(
        onSuccess: (List<Profesor>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        // 1. TACTIC: Immediate RAM memory cache return
        cachedProfesores?.let {
            Log.d("FirestoreProfesores", "Returned ${it.size} coaches from RAM Cache Tactic")
            onSuccess(it)
            return
        }

        // 2. TACTIC: Disk Cache explicit request
        val docRef = db.collection("profesores")
        docRef.get(Source.CACHE)
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val profesores = mutableListOf<Profesor>()
                    for (document in result) {
                        try {
                            val profesor = document.toObject(Profesor::class.java)
                            profesor.id = document.id
                            profesores.add(profesor)
                        } catch (e: Exception) {
                            Log.e("FirestoreProfesores", "Error parsing cache ${document.id}", e)
                        }
                    }
                    cachedProfesores = profesores // Save to RAM for next time
                    Log.d("FirestoreProfesores", "Returned ${profesores.size} from DISK Cache Tactic")
                    onSuccess(profesores)
                }

                // 3. TACTIC: Always verify with Server in the background to keep Cache fresh
                fetchProfesoresFromServerBackground(onSuccess)
            }
            .addOnFailureListener {
                // If cache completely fails or requires index, default to server
                Log.d("FirestoreProfesores", "Cache empty/failed, fetching from Server Tactic")
                fetchProfesoresFromServerBackground(onSuccess, onFailure)
            }
    }

    private fun fetchProfesoresFromServerBackground(
        onSuccess: (List<Profesor>) -> Unit,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        db.collection("profesores").get(Source.SERVER)
            .addOnSuccessListener { result ->
                val profesores = mutableListOf<Profesor>()
                for (document in result) {
                    try {
                        val profesor = document.toObject(Profesor::class.java)
                        profesor.id = document.id
                        profesores.add(profesor)
                    } catch (e: Exception) {
                        Log.e("FirestoreProfesores", "Error parsing server doc ${document.id}", e)
                    }
                }
                
                // Only trigger re-render if memory changed size or content significantly 
                // (for simplicity in this tactic, we update RAM and notify UI again so it reacts)
                cachedProfesores = profesores
                Log.d("FirestoreProfesores", "Updated ${profesores.size} coaches from SERVER")
                onSuccess(profesores)
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreProfesores", "Error getting server documents.", exception)
                onFailure?.invoke(exception)
            }
    }

    override fun fetchReviews(
        profesorId: String,
        onSuccess: (List<Review>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("profesores").document(profesorId).collection("reviews").get()
            .addOnSuccessListener { result ->
                val reviews = mutableListOf<Review>()
                for (document in result) {
                    try {
                        val review = document.toObject(Review::class.java)
                        review.id = document.id
                        reviews.add(review)
                    } catch (e: Exception) {
                         Log.e("FirestoreProfesores", "Error parsing review ${document.id}", e)
                    }
                }
                onSuccess(reviews)
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreProfesores", "Error getting reviews.", exception)
                onFailure(exception)
            }
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
                // TACTIC: Invalidate cache so next fetch gets the new coach
                cachedProfesores = null 
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
