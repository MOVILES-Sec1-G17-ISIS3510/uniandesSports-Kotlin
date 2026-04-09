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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BookClassViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    
    var selectedSport by mutableStateOf("Soccer")
    var selectedSkillLevel by mutableStateOf("Beginner")
    var preferredSchedule by mutableStateOf("")
    var notes by mutableStateOf("")
    var isSubmitting by mutableStateOf(false)

    private val _userBookings = MutableStateFlow<List<BookingRequest>>(emptyList())
    val userBookings: StateFlow<List<BookingRequest>> = _userBookings.asStateFlow()

    private val _smartCoachInsight = MutableStateFlow<String?>(null)
    val smartCoachInsight: StateFlow<String?> = _smartCoachInsight.asStateFlow()

    fun fetchUserBookings(userId: String) {
        if (userId.isBlank()) return
        db.collection("coach_requests")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("BookClassVM", "Error fetching bookings", e)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val bookings = snapshot.toObjects(BookingRequest::class.java)
                    _userBookings.value = bookings
                    generateSmartInsight(bookings)
                }
            }
    }

    private fun generateSmartInsight(bookings: List<BookingRequest>) {
        if (bookings.isEmpty()) {
            _smartCoachInsight.value = "Welcome! Book your first session to receive personalized coaching tips."
            return
        }

        // BQ3 Implementation: Analyze most frequent sport
        val mostFrequentSport = bookings
            .groupBy { it.sport }
            .maxByOrNull { it.value.size }

        mostFrequentSport?.let { (sport, list) ->
            val count = list.size
            if (count >= 2) {
                _smartCoachInsight.value = "You seem to love $sport! 🎾 You've scheduled it $count times. Consider a session with a pro to reach the next level."
            } else {
                _smartCoachInsight.value = "Great start with $sport! Keep it up to build a consistent habit."
            }
        }
    }

    fun submitBooking(
        profesorId: String, 
        profesorName: String, // New parameter
        studentId: String, 
        studentName: String, 
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (isSubmitting) return
        isSubmitting = true
        
        viewModelScope.launch {
            try {
                Log.d("BookClassVM", "Starting booking submission for sport: $selectedSport with $profesorName")
                
                // Align with existing 'coach_requests' structure
                val requestDoc = db.collection("coach_requests").document()
                // Handle Broadcast logic: If ID is 'broadcast', it's an open request for all coaches
                val finalProfId = if (profesorId == "broadcast") "" else profesorId
                val finalProfName = if (profesorId == "broadcast") "" else profesorName
                
                val request = BookingRequest(
                    id = requestDoc.id,
                    userId = studentId, 
                    studentName = studentName,
                    targetProfesorId = finalProfId,
                    targetProfesorName = finalProfName, // Mapping added
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
