package com.uniandes.sport.viewmodels.profesores

import com.uniandes.sport.models.Profesor
import com.uniandes.sport.models.Review

interface ProfesoresViewModelInterface {
    fun fetchProfesores(
        onSuccess: (List<Profesor>) -> Unit,
        onFailure: (Exception) -> Unit
    )

    fun fetchReviews(
        profesorId: String,
        onSuccess: (List<Review>) -> Unit,
        onFailure: (Exception) -> Unit
    )

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
}
