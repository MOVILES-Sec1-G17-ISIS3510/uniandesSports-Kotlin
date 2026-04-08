package com.uniandes.sport.viewmodels.profesores

import androidx.lifecycle.ViewModel
import com.uniandes.sport.models.Profesor
import com.uniandes.sport.models.Review
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DummyProfesoresViewModel : ViewModel(), ProfesoresViewModelInterface {

    private val _profesoresList = emptyList<Profesor>()
    private val _reviewsMap = emptyMap<String, List<Review>>()

    private val _profesores = MutableStateFlow<List<Profesor>>(emptyList())
    override val profesores: StateFlow<List<Profesor>> = _profesores.asStateFlow()

    private val _reviews = MutableStateFlow<List<Review>>(emptyList())
    override val reviews: StateFlow<List<Review>> = _reviews.asStateFlow()

    private val _bookingRequests = MutableStateFlow<List<com.uniandes.sport.models.BookingRequest>>(emptyList())
    override val bookingRequests: StateFlow<List<com.uniandes.sport.models.BookingRequest>> = _bookingRequests.asStateFlow()

    override fun fetchProfesores(
        onSuccess: (List<Profesor>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        _profesores.value = _profesoresList
        onSuccess(_profesoresList)
    }

    override fun refreshProfesores(onComplete: () -> Unit) {
        onComplete()
    }

    override fun fetchReviews(profesorId: String) {
        _reviews.value = _reviewsMap[profesorId] ?: emptyList()
    }

    override fun fetchBookingRequestsBySport(sport: String) {
        // Dummy implementation
    }

    override fun syncCoachingLeadsTopic(sport: String) {
        // Dummy implementation
    }

    override fun createProfesor(
        profesor: Profesor,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        // En Dummy simplemente simulamos el éxito
        onSuccess()
    }

    override fun addReview(
        profesorId: String,
        review: Review,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSuccess()
    }

    override fun syncReviewsCount(
        profesorId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSuccess()
    }
}
