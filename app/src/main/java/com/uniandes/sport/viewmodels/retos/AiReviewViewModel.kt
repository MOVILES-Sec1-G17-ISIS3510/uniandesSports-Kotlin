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
    private val playViewModel: com.uniandes.sport.viewmodels.play.PlayViewModelInterface? = null // Para actualizar el track del evento
) : ViewModel() {

    private val _uiState = mutableStateOf<AiReviewState>(AiReviewState.Idle)
    val uiState: State<AiReviewState> = _uiState

    /**
     * Resetea el progreso de todos los retos asociados a un evento específico.
     * Se usa cuando un usuario marca un evento como "no asistido" después de haber tenido progreso.
     */
    fun resetProgressForEvent(eventId: String, oldAnalysis: Map<String, Double>) {
        if (oldAnalysis.isEmpty()) return
        
        _uiState.value = AiReviewState.Loading
        viewModelScope.launch {
            try {
                oldAnalysis.forEach { (retoId, oldVal) ->
                    // Sincronizamos a 0.0 (newProgress = 0.0)
                    firestoreRetosViewModel.syncChallengeProgress(
                        retoId = retoId,
                        oldProgress = oldVal,
                        newProgress = 0.0,
                        trackText = "Participation removed / Track reset",
                        eventId = eventId
                    )
                }
                _uiState.value = AiReviewState.Success(0, "Progress has been reset for this session.")
            } catch (e: Exception) {
                _uiState.value = AiReviewState.Error("Error resetting progress: ${e.message}")
            }
        }
    }

    fun analyzeTrack(trackText: String, eventId: String, oldAnalysis: Map<String, Double> = emptyMap()) {
        _uiState.value = AiReviewState.Loading
        
        viewModelScope.launch {
            val allRetos = firestoreRetosViewModel.retos.value
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val activeChallenges = allRetos.filter { it.participants.contains(uid) && it.status == "active" }
            
            android.util.Log.d("AiReviewVM", "Starting analyzeTrack. Found ${activeChallenges.size} active challenges.")

            if (activeChallenges.isEmpty()) {
                android.util.Log.w("AiReviewVM", "No active challenges found. Logic skipped.")
                _uiState.value = AiReviewState.Success(0, "You don't have active challenges to track progress for.")
                return@launch
            }

            val result = analyzerStrategy.analyzeReview(trackText, activeChallenges)
            android.util.Log.d("AiReviewVM", "AI Analysis Success: ${result.success}. Map size: ${result.progressByChallengeId.size}")
            
            if (result.success) {
                var advancedCount = 0
                val newProgressMap = result.progressByChallengeId
                val allRelevantChallengeIds = (newProgressMap.keys + oldAnalysis.keys).toSet()

                android.util.Log.d("AiReviewVM", "Syncing progress for ${allRelevantChallengeIds.size} challenges...")

                allRelevantChallengeIds.forEach { retoId ->
                    val newVal = newProgressMap[retoId] ?: 0.0
                    val oldVal = oldAnalysis[retoId] ?: 0.0
                    
                    android.util.Log.d("AiReviewVM", "Ch: $retoId | Old: $oldVal | New: $newVal")

                    if (Math.abs(newVal - oldVal) > 0.01) {
                        if (newVal > 0) advancedCount++
                        
                        firestoreRetosViewModel.syncChallengeProgress(
                            retoId = retoId,
                            oldProgress = oldVal,
                            newProgress = newVal,
                            trackText = trackText,
                            eventId = eventId
                        )
                    } else {
                        android.util.Log.d("AiReviewVM", "Skipping sync for $retoId: delta is zero.")
                    }
                }
                
                playViewModel?.updateTrackAiAnalysis(
                    eventId = eventId,
                    userId = uid,
                    analysis = newProgressMap
                )
                
                if (advancedCount > 0) {
                    _uiState.value = AiReviewState.Success(
                        advancedCount, 
                        "Great work! You advanced in $advancedCount active challenges."
                    )
                } else {
                    _uiState.value = AiReviewState.Success(
                        0, 
                        "Activity recorded! This track didn't apply to your current challenges goals."
                    )
                }
            } else {
                android.util.Log.e("AiReviewVM", "AI Strategy Error: ${result.errorMessage}")
                _uiState.value = AiReviewState.Error(result.errorMessage ?: "Unknown error occurred")
            }
        }
    }
    
    fun resetState() {
        _uiState.value = AiReviewState.Idle
    }
}
