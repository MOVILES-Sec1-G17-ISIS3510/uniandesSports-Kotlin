package com.uniandes.sport.viewmodels.booking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.uniandes.sport.data.local.PendingBookingPayload
import com.uniandes.sport.data.local.PendingBookingStore
import com.uniandes.sport.models.BookingRequest
import com.uniandes.sport.models.CoachInsight
import com.uniandes.sport.models.CoachingNotification
import com.uniandes.sport.models.InsightType
import com.uniandes.sport.models.Profesor
import com.uniandes.sport.workers.BookingSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await


class BookClassViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    
    private val appContext: Context?
        get() = try {
            FirebaseApp.getInstance().applicationContext
        } catch (_: Exception) {
            null
        }
    
    var selectedSport by mutableStateOf("Soccer")
    var selectedSkillLevel by mutableStateOf("Beginner")
    var preferredSchedule by mutableStateOf("")
    var notes by mutableStateOf("")
    var isSubmitting by mutableStateOf(false)

    private val _userBookings = MutableStateFlow<List<BookingRequest>>(emptyList())
    val userBookings: StateFlow<List<BookingRequest>> = _userBookings.asStateFlow()

    private val _smartCoachInsights = MutableStateFlow<List<CoachInsight>>(
        listOf(CoachInsight("Checking context for personalized tips...", InsightType.WELCOME))
    )
    val smartCoachInsights: StateFlow<List<CoachInsight>> = _smartCoachInsights.asStateFlow()

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

    private var mWeatherCode: Int? = null
    private var mCoaches: List<Profesor> = emptyList()

    fun updateWeatherContext(weatherCode: Int) {
        mWeatherCode = weatherCode
        generateSmartInsight(_userBookings.value)
    }

    fun updateCoachesContext(coaches: List<Profesor>) {
        mCoaches = coaches
        generateSmartInsight(_userBookings.value)
    }

    private fun generateSmartInsight(bookings: List<BookingRequest>) {
        val newInsights = mutableListOf<CoachInsight>()

        // 1. Context-Aware: Environmental Context (Weather)
        // ... (existing weather logic stays the same)
        mWeatherCode?.let { code ->
            val isRainy = code in 51..67 || code in 80..82 || code in 95..99
            val isSunny = code == 0 || code == 1
            val isCloudy = code in 2..3 || code in 45..48

            if (isRainy) {
                newInsights.add(CoachInsight(
                    "It's raining outside! ⛈️ Stay dry—book a session with a Swimming pro or a Gym coach.",
                    InsightType.ENVIRONMENTAL
                ))
            } else if (isSunny) {
                newInsights.add(CoachInsight(
                    "Perfect day for training! ☀️ Outdoor coaches for Tennis and Running are waiting for you.",
                    InsightType.ENVIRONMENTAL
                ))
            } else if (isCloudy) {
                newInsights.add(CoachInsight(
                    "It's a bit cloudy. ☁️ Good time for an indoor training session or a swimming class!",
                    InsightType.ENVIRONMENTAL
                ))
            }
        }

        // 2. Behavioral Context: Sport Habits (BQ3)
        if (bookings.isNotEmpty()) {
            val mostFrequentSport = bookings
                .groupBy { it.sport }
                .maxByOrNull { it.value.size }

            mostFrequentSport?.let { (sport, list) ->
                val count = list.size
                
                // Find a recommended coach for this sport
                val recommendedCoach = mCoaches
                    .filter { it.deporte.equals(sport, ignoreCase = true) }
                    .maxByOrNull { it.rating }

                if (recommendedCoach != null) {
                    newInsights.add(CoachInsight(
                        "You've played $sport $count times! We recommend training with ${recommendedCoach.nombre}, a top-rated pro in this sport. 🏆",
                        InsightType.BEHAVIORAL
                    ))
                } else {
                    if (count >= 2) {
                        newInsights.add(CoachInsight(
                            "You seem to love $sport! You've scheduled it $count times. Ready to reach the next level?",
                            InsightType.BEHAVIORAL
                        ))
                    } else if (newInsights.isEmpty()) {
                        newInsights.add(CoachInsight(
                            "Great start with $sport! Keep it up to build a consistent habit.",
                            InsightType.BEHAVIORAL
                        ))
                    }
                }
            }
        }

        if (newInsights.isEmpty()) {
            newInsights.add(CoachInsight(
                "Welcome! Book your first session to receive personalized coaching tips.",
                InsightType.WELCOME
            ))
        }

        _smartCoachInsights.value = newInsights
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

    private fun queuePendingBooking(
        profesorId: String,
        profesorName: String,
        studentId: String,
        studentName: String,
        onSuccess: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        val context = appContext
        if (context == null) {
            onError("No internet and local queue unavailable")
            return
        }

        try {
            val finalProfId = if (profesorId == "broadcast") "" else profesorId
            val finalProfName = if (profesorId == "broadcast") "" else profesorName

            val pending = PendingBookingPayload(
                userId = studentId,
                studentName = studentName,
                targetProfesorId = finalProfId,
                targetProfesorName = finalProfName,
                sport = selectedSport,
                skillLevel = selectedSkillLevel,
                schedule = preferredSchedule,
                notes = notes
            )

            PendingBookingStore.enqueue(context, pending)
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<BookingSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
            onSuccess(true) // true implies it's pending offline
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error when queuing")
        }
    }

    fun submitBooking(
        profesorId: String, 
        profesorName: String, 
        studentId: String, 
        studentName: String, 
        onSuccess: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isSubmitting) return
        if (!isNetworkConnected()) {
            queuePendingBooking(profesorId, profesorName, studentId, studentName, onSuccess, onError)
            return
        }

        isSubmitting = true
        
        viewModelScope.launch {
            try {
                Log.d("BookClassVM", "Starting booking submission for sport: $selectedSport with $profesorName")
                
                val requestDoc = db.collection("coach_requests").document()
                val finalProfId = if (profesorId == "broadcast") "" else profesorId
                val finalProfName = if (profesorId == "broadcast") "" else profesorName
                
                val request = BookingRequest(
                    id = requestDoc.id,
                    userId = studentId, 
                    studentName = studentName,
                    targetProfesorId = finalProfId,
                    targetProfesorName = finalProfName,
                    sport = selectedSport,
                    skillLevel = selectedSkillLevel,
                    schedule = preferredSchedule,
                    notes = notes,
                    status = "pending"
                )
                
                requestDoc.set(request).await()
                Log.d("BookClassVM", "Booking request saved successfully")
                
                isSubmitting = false
                onSuccess(false) // false implies it was successfully sent online
            } catch (e: Exception) {
                Log.e("BookClassVM", "Error submitting booking", e)
                isSubmitting = false
                if (!isNetworkConnected()) {
                    queuePendingBooking(profesorId, profesorName, studentId, studentName, onSuccess, onError)
                } else {
                    onError(e.message ?: "Unknown error")
                }
            }
        }
    }
}
