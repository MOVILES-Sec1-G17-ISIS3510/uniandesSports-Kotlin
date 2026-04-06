package com.uniandes.sport.viewmodels.profesores

import com.uniandes.sport.models.Profesor
import com.uniandes.sport.models.Review
import kotlinx.coroutines.flow.StateFlow

interface ProfesoresViewModelInterface {
    val profesores: StateFlow<List<Profesor>>
    val reviews: StateFlow<List<Review>>

    fun fetchProfesores(
        onSuccess: (List<Profesor>) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    )
    
    fun refreshProfesores(onComplete: () -> Unit = {})
    
    fun fetchReviews(profesorId: String)

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
}
