package com.uniandes.sport.viewmodels.nutrition

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uniandes.sport.data.local.NutritionPlanFileStorage
import com.uniandes.sport.data.nutrition.NutritionAiService
import com.uniandes.sport.models.nutrition.NutritionPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// viewmodel para la asistente de nutricion.
// al iniciar carga el ultimo plan guardado en filesystem (vista protegida offline).
// generatenewplan corre en dispatchers.io porque hace red + disco;
// el resultado se devuelve al hilo main para actualizar los stateflow
class NutritionViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentPlan = MutableStateFlow<NutritionPlan?>(null)
    val currentPlan: StateFlow<NutritionPlan?> = _currentPlan.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadLocalPlan()
    }

    // carga el ultimo plan guardado en filesystem. corre en io para no bloquear ui
    private fun loadLocalPlan() {
        viewModelScope.launch(Dispatchers.IO) {
            val plan = NutritionPlanFileStorage.loadPlan(getApplication())
            withContext(Dispatchers.Main) {
                _currentPlan.value = plan
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // descarta el plan actual (en memoria y en filesystem) para que el usuario
    // pueda generar uno nuevo desde cero
    fun deletePlan() {
        viewModelScope.launch(Dispatchers.IO) {
            NutritionPlanFileStorage.deletePlan(getApplication())
            withContext(Dispatchers.Main) {
                _currentPlan.value = null
            }
        }
    }

    // genera un nuevo plan llamando a openai y sobrescribiendo el guardado.
    // si la red falla, mantiene el plan anterior visible y muestra el error
    fun generateNewPlan(
        age: Int,
        weightKg: Double,
        heightCm: Double,
        goal: String,
        dietaryRestrictions: String
    ) {
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val result = NutritionAiService.generateAndSavePlan(
                context = getApplication(),
                age = age,
                weightKg = weightKg,
                heightCm = heightCm,
                goal = goal,
                dietaryRestrictions = dietaryRestrictions
            )
            withContext(Dispatchers.Main) {
                _isLoading.value = false
                result.fold(
                    onSuccess = { _currentPlan.value = it },
                    onFailure = { _errorMessage.value = it.message ?: "Failed to generate plan" }
                )
            }
        }
    }
}
