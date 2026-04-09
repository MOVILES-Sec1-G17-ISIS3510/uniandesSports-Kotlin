package com.uniandes.sport.viewmodels.profesores

import com.uniandes.sport.models.Profesor
import com.uniandes.sport.models.Review
import kotlinx.coroutines.flow.StateFlow

interface ProfesoresViewModelInterface {
    val profesores: StateFlow<List<Profesor>>
    val reviews: StateFlow<List<Review>>
    val bookingRequests: StateFlow<List<com.uniandes.sport.models.BookingRequest>>

    fun fetchProfesores(
        onSuccess: (List<Profesor>) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    )
    
    fun refreshProfesores(onComplete: () -> Unit = {})
    
    fun fetchReviews(profesorId: String)

    fun fetchBookingRequestsBySport(sport: String)

    fun syncCoachingLeadsTopic(sport: String)

    fun createProfesor(
        profesor: Profesor,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    )

    fun addReview(
        profesorId: String,
        review: Review,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    )

    fun syncReviewsCount(
        profesorId: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    )

    fun acceptBookingRequest(
        requestId: String,
        professorId: String,
        professorName: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    )
}
