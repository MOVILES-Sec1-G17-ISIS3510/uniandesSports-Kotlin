package com.uniandes.sport.viewmodels.retos

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uniandes.sport.ai.AiAnalyzerStrategy
import com.uniandes.sport.models.Reto
import kotlinx.coroutines.launch

sealed class AiReviewState {
    object Idle : AiReviewState()
    object Loading : AiReviewState()
    data class Success(val advancedChallengesCount: Int, val message: String) : AiReviewState()
    data class Error(val error: String) : AiReviewState()
}

class AiReviewViewModel(
    private val analyzerStrategy: AiAnalyzerStrategy,
    private val firestoreRetosViewModel: FirestoreRetosViewModel, // Para guardar directo en la BD
    private val playViewModel: com.uniandes.sport.viewmodels.play.PlayViewModelInterface? = null // Para actualizar el review del evento
) : ViewModel() {

    private val _uiState = mutableStateOf<AiReviewState>(AiReviewState.Idle)
    val uiState: State<AiReviewState> = _uiState

    fun analyzeReview(reviewText: String, eventId: String, oldAnalysis: Map<String, Double> = emptyMap()) {
        _uiState.value = AiReviewState.Loading
        
        viewModelScope.launch {
            // Obtener los retos activos actuales del usuario filtrando manualmente la lista global
            // (evitamos .activeChallenges.value porque es un StateFlow WhileSubscribed y puede estar vacío si no hay UI escuchando)
            val allRetos = firestoreRetosViewModel.retos.value
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val activeChallenges = allRetos.filter { it.participants.contains(uid) && it.status == "active" }
            
            if (activeChallenges.isEmpty()) {
                _uiState.value = AiReviewState.Success(0, "No tienes retos activos a los cuales aplicar progreso.")
                return@launch
            }

            // Delegamos en la estrategia elegida para obtener el NUEVO análisis
            val result = analyzerStrategy.analyzeReview(reviewText, activeChallenges)
            
            if (result.success) {
                var advancedCount = 0
                val newProgressMap = result.progressByChallengeId
                
                // Calculamos todos los retos involucrados (los nuevos y los que estaban antes)
                val allRelevantChallengeIds = (newProgressMap.keys + oldAnalysis.keys).toSet()

                allRelevantChallengeIds.forEach { retoId ->
                    val newVal = newProgressMap[retoId] ?: 0.0
                    val oldVal = oldAnalysis[retoId] ?: 0.0
                    
                    if (Math.abs(newVal - oldVal) > 0.01) {
                        if (newVal > 0) advancedCount++ // Solo contamos los que suman progreso positivo real
                        
                        // Sincronizamos usando la lógica de deltas (newVal - oldVal)
                        firestoreRetosViewModel.syncChallengeProgress(
                            retoId = retoId,
                            oldProgress = oldVal,
                            newProgress = newVal,
                            reviewText = reviewText,
                            eventId = eventId
                        )
                    }
                }
                
                // 3. Actualizar la reseña original del evento con el NUEVO análisis de la IA
                playViewModel?.updateReviewAiAnalysis(
                    eventId = eventId,
                    userId = uid,
                    analysis = newProgressMap
                )
                
                if (advancedCount > 0) {
                    _uiState.value = AiReviewState.Success(
                        advancedCount, 
                        "¡Buen trabajo! Has avanzado en $advancedCount retos activos."
                    )
                } else {
                    _uiState.value = AiReviewState.Success(
                        0, 
                        "¡Registro completado! Tu reseña no aplicaba a los retos actuales."
                    )
                }
            } else {
                _uiState.value = AiReviewState.Error(result.errorMessage ?: "Ocurrió un error desconocido")
            }
        }
    }
    
    fun resetState() {
        _uiState.value = AiReviewState.Idle
    }
}
