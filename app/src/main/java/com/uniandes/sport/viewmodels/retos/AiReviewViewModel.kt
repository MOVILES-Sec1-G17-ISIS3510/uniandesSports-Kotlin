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

    fun analyzeReview(reviewText: String, eventId: String) {
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

            // Delegamos en la estrategia elegida
            val result = analyzerStrategy.analyzeReview(reviewText, activeChallenges)
            
            if (result.success) {
                var advancedCount = 0
                val progressMap = result.progressByChallengeId
                
                // Actualizamos directamente (silenciosamente) en la base de datos
                progressMap.forEach { (retoId, addedProgress) ->
                    if (addedProgress > 0) {
                        val retoTarget = activeChallenges.find { it.id == retoId }
                        if (retoTarget != null) {
                            advancedCount++
                            firestoreRetosViewModel.addProgressToChallenge(retoId, addedProgress, reviewText, eventId)
                        }
                    }
                }
                
                // 3. Actualizar la reseña original del evento con el análisis de la IA
                playViewModel?.updateReviewAiAnalysis(
                    eventId = eventId,
                    userId = uid,
                    analysis = progressMap
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
