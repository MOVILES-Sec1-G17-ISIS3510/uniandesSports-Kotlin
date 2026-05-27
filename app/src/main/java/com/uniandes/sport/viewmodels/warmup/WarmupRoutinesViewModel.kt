package com.uniandes.sport.viewmodels.warmup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uniandes.sport.data.warmup.WarmupRoutinesService
import com.uniandes.sport.models.warmup.WarmupExercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// viewmodel de warm-up routines.
//
// --- multithreading ---
// la llamada a firestore corre en dispatchers.io para no bloquear el hilo principal,
// y los stateflow se actualizan en dispatchers.main para que la ui (compose) reaccione.
// la operacion expone callbacks onsuccess/onerror para acoplar la navegacion sin
// forzar a la ui a observar otro stateflow auxiliar.
class WarmupRoutinesViewModel : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // pool completo descargado para la combinacion actual
    private val _exercisesPool = MutableStateFlow<List<WarmupExercise>>(emptyList())
    val exercisesPool: StateFlow<List<WarmupExercise>> = _exercisesPool.asStateFlow()

    // seleccion actual de 4 ejercicios mostrados en el carrusel.
    // se reemplaza al hacer shuffle sin tocar el pool
    private val _selectedExercises = MutableStateFlow<List<WarmupExercise>>(emptyList())
    val selectedExercises: StateFlow<List<WarmupExercise>> = _selectedExercises.asStateFlow()

    // descarga el pool de firestore (o lo trae de la cache l1 del servicio).
    // ui responsabilidad: validar conectividad antes de llamar este metodo
    fun fetchExercises(
        category: String,
        intensity: String,
        onSuccess: () -> Unit = {}
    ) {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pool = WarmupRoutinesService.getExercisesPool(category, intensity)
                withContext(Dispatchers.Main) {
                    _exercisesPool.value = pool
                    _selectedExercises.value = pickFour(pool)
                    _isLoading.value = false
                    if (pool.isNotEmpty()) onSuccess()
                    else _error.value = "No routines found for the selected combination"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _error.value = "Failed to load routines: ${e.message}"
                    _isLoading.value = false
                }
            }
        }
    }

    // carga el pool desde la cache l1 del servicio sin hacer red.
    // util al entrar a la pantalla de ejercicios despues de una busqueda exitosa
    fun loadFromCache() {
        val pool = WarmupRoutinesService.currentPool()
        _exercisesPool.value = pool
        _selectedExercises.value = pickFour(pool)
    }

    // baraja el pool descargado y elige 4 ejercicios distintos sin consumir red
    fun shuffle() {
        _selectedExercises.value = pickFour(_exercisesPool.value)
    }

    fun clearError() {
        _error.value = null
    }

    private fun pickFour(pool: List<WarmupExercise>): List<WarmupExercise> {
        if (pool.isEmpty()) return emptyList()
        return pool.shuffled().take(4)
    }
}
