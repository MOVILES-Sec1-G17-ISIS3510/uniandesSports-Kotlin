package com.uniandes.sport.viewmodels.profesores

import androidx.lifecycle.ViewModel
import com.uniandes.sport.models.Profesor
import com.uniandes.sport.models.Review
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log

class FirestoreProfesoresViewModel : ViewModel(), ProfesoresViewModelInterface {

    private val db = FirebaseFirestore.getInstance()

    override fun fetchProfesores(
        onSuccess: (List<Profesor>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("profesores").get()
            .addOnSuccessListener { result ->
                val profesores = mutableListOf<Profesor>()
                for (document in result) {
                    try {
                        val profesor = document.toObject(Profesor::class.java)
                        // Ensure the document ID is mapped to the object's ID field
                        profesor.id = document.id
                        profesores.add(profesor)
                    } catch (e: Exception) {
                        Log.e("FirestoreProfesores", "Error parsing document ${document.id}", e)
                    }
                }
                onSuccess(profesores)
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreProfesores", "Error getting documents.", exception)
                onFailure(exception)
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
