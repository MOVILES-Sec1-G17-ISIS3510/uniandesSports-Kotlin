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

// viewmodel de warm-up routines con soporte multinivel del service.
//
// --- multithreading ---
// 1) dispatchers.io para la llamada al service (lectura y escritura paralela en service)
// 2) withcontext(main) para actualizar los stateflow que la ui observa
// 3) corrutinas anidadas dentro del service (room write + file write en paralelo)
//
// --- eventual connectivity ---
// fetchexercises acepta isonline; el service se encarga de saltar firestore
// y leer directo de room/archivo cuando isonline=false. el viewmodel solo
// expone el resultado a la ui sin distinguir el origen
class WarmupRoutinesViewModel : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _exercisesPool = MutableStateFlow<List<WarmupExercise>>(emptyList())
    val exercisesPool: StateFlow<List<WarmupExercise>> = _exercisesPool.asStateFlow()

    private val _selectedExercises = MutableStateFlow<List<WarmupExercise>>(emptyList())
    val selectedExercises: StateFlow<List<WarmupExercise>> = _selectedExercises.asStateFlow()

    // ultima combinacion consultada (para que loadfromcache pueda reutilizarla)
    @Volatile private var lastCategory: String = ""
    @Volatile private var lastIntensity: String = ""

    // descarga el pool. si isonline=true intenta firestore con write-through,
    // si isonline=false va directo a room/archivo. en ambos casos cae en cascada
    fun fetchExercises(
        category: String,
        intensity: String,
        isOnline: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        _isLoading.value = true
        _error.value = null
        lastCategory = category
        lastIntensity = intensity

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pool = WarmupRoutinesService.getExercisesPool(
                    category = category,
                    intensity = intensity,
                    allowNetwork = isOnline
                )
                withContext(Dispatchers.Main) {
                    _exercisesPool.value = pool
                    _selectedExercises.value = pickFour(pool)
                    _isLoading.value = false
                    when {
                        pool.isNotEmpty() -> onSuccess()
                        !isOnline -> _error.value =
                            "No cached routines available offline for this combination"
                        else -> _error.value =
                            "No routines found for the selected combination"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _error.value = "Failed to load routines: ${e.message}"
                    _isLoading.value = false
                }
            }
        }
    }

    // carga desde el lrucache del service. si esta vacio (proceso recien iniciado),
    // intenta delegar al service que cae a room/archivo
    fun loadFromCache(category: String, intensity: String) {
        lastCategory = category
        lastIntensity = intensity
        val cached = WarmupRoutinesService.currentPool(category, intensity)
        if (cached.isNotEmpty()) {
            _exercisesPool.value = cached
            _selectedExercises.value = pickFour(cached)
        }
        // si esta vacio, la pantalla de ejercicios llamara a fetchexercises explicito
    }

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
