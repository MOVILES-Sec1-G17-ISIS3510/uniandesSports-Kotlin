package com.uniandes.sport.viewmodels.booking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.uniandes.sport.models.BookingRequest
import com.uniandes.sport.models.CoachingNotification
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BookClassViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    
    var selectedSport by mutableStateOf("Soccer")
    var selectedSkillLevel by mutableStateOf("Beginner")
    var preferredSchedule by mutableStateOf("")
    var notes by mutableStateOf("")
    var isSubmitting by mutableStateOf(false)

    fun submitBooking(
        profesorId: String, 
        studentId: String, 
        studentName: String, 
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (isSubmitting) return
        isSubmitting = true
        
        viewModelScope.launch {
            try {
                Log.d("BookClassVM", "Starting booking submission for sport: $selectedSport")
                
                // Align with existing 'coach_requests' structure
                val requestDoc = db.collection("coach_requests").document()
                val request = BookingRequest(
                    id = requestDoc.id,
                    userId = studentId, 
                    studentName = studentName,
                    targetProfesorId = profesorId,
                    sport = selectedSport,
                    skillLevel = selectedSkillLevel,
                    schedule = preferredSchedule,
                    notes = notes,
                    status = "pending"
                )
                
                requestDoc.set(request).await()
                Log.d("BookClassVM", "Booking request saved successfully")
                
                isSubmitting = false
                onSuccess()
            } catch (e: Exception) {
                Log.e("BookClassVM", "Error submitting booking", e)
                isSubmitting = false
                onError(e.message ?: "Unknown error")
            }
        }
    }
}
