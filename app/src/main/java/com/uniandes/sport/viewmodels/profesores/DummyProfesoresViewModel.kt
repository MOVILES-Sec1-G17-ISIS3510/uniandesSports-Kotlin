package com.uniandes.sport.viewmodels.profesores

import androidx.lifecycle.ViewModel
import com.uniandes.sport.models.Profesor
import com.uniandes.sport.models.Review

class DummyProfesoresViewModel : ViewModel(), ProfesoresViewModelInterface {

    private val profesores = emptyList<Profesor>()

    private val reviews = emptyMap<String, List<Review>>()

    override fun fetchProfesores(
        onSuccess: (List<Profesor>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSuccess(profesores)
    }

    override fun fetchReviews(
        profesorId: String,
        onSuccess: (List<Review>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onSuccess(reviews[profesorId] ?: emptyList())
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
}
