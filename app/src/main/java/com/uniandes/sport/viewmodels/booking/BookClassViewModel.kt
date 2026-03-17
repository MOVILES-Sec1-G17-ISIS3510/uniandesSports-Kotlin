package com.uniandes.sport.viewmodels.booking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class BookClassViewModel : ViewModel() {
    var selectedSport by mutableStateOf("Soccer")
    var selectedSkillLevel by mutableStateOf("Beginner")
    var preferredSchedule by mutableStateOf("")
    var notes by mutableStateOf("")

    fun submitBooking(profesorId: String, onSuccess: () -> Unit) {
        // En una implementación real, aquí se llamaría al repositorio/Firestore
        // para guardar la reserva.
        onSuccess()
    }
}
