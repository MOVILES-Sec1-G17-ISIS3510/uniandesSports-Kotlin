package com.uniandes.sport.viewmodels.retos

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import com.google.firebase.FirebaseApp
import com.uniandes.sport.ai.AiAnalyzerStrategy
import com.uniandes.sport.data.cache.AiResultCache
import com.uniandes.sport.data.local.AiHistoryEntry
import com.uniandes.sport.data.local.AiHistoryStore
import com.uniandes.sport.models.Reto
import kotlinx.coroutines.launch

sealed class AiReviewState {
    object Idle : AiReviewState()
    object Loading : AiReviewState()
    data class Success(val advancedChallengesCount: Int, val message: String) : AiReviewState()
    data class PoseFeedback(val feedback: String) : AiReviewState()
    data class Error(val error: String) : AiReviewState()
}

class AiReviewViewModel(
    private val analyzerStrategy: AiAnalyzerStrategy,
    private val firestoreRetosViewModel: FirestoreRetosViewModel, // Para guardar directo en la BD
    private val playViewModel: com.uniandes.sport.viewmodels.play.PlayViewModelInterface? = null // Para actualizar el track del evento
) : ViewModel() {

    private val _uiState = mutableStateOf<AiReviewState>(AiReviewState.Idle)
    val uiState: State<AiReviewState> = _uiState

    private val appContext
        get() = try { FirebaseApp.getInstance().applicationContext } catch (_: Exception) { null }

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

    // analizar track/sesion con cache lru.
    // si ya existe un resultado cacheado para este evento y usuario, se retorna
    // sin llamar a la api. util cuando el usuario vuelve a abrir el detalle del evento
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

                // cachear el resultado del analisis para acceso futuro sin llamar a la api
                val cacheKey = AiResultCache.trackKey(eventId, uid)
                val resultSummary = if (advancedCount > 0)
                    "Advanced in $advancedCount challenges: ${newProgressMap.keys.joinToString()}"
                else
                    "Activity recorded. No challenge progress applied."
                AiResultCache.put(cacheKey, resultSummary)

                // guardar en historial local persistente
                val ctx = appContext
                if (ctx != null) {
                    AiHistoryStore.addEntry(ctx, AiHistoryEntry(
                        id = "track_${eventId}_${System.currentTimeMillis()}",
                        type = "track",
                        eventId = eventId,
                        feedback = resultSummary,
                        imagePath = ""
                    ))
                }

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
    
    // analizar pose de calistenia con cache lru.
    // si ya existe un resultado cacheado para este evento y usuario, se retorna
    // directamente sin llamar a la api (cache hit). si no, se llama a la api
    // y se guarda el resultado en el cache para futuras consultas (cache miss).
    // el eventid es opcional para asociar el resultado a un evento especifico
    // bitmap opcional para guardar la foto en el historial local (se carga despues con coil)
    fun analyzeCalisthenicsPose(base64Image: String, eventId: String = "standalone", userId: String = "", photoBitmap: Bitmap? = null) {
        val cacheKey = AiResultCache.poseKey(eventId, userId)

        // buscar en cache lru antes de llamar a la api (evitar llamadas repetidas)
        val cached = AiResultCache.get(cacheKey)
        if (cached != null && userId.isNotBlank()) {
            android.util.Log.d("AiReviewVM", "pose feedback desde cache lru: $cacheKey")
            _uiState.value = AiReviewState.PoseFeedback(cached)
            return
        }

        _uiState.value = AiReviewState.Loading
        viewModelScope.launch {
            try {
                val feedback = analyzerStrategy.analyzePose(base64Image)
                if (feedback != null) {
                    // guardar resultado en cache lru para acceso futuro
                    AiResultCache.put(cacheKey, feedback)

                    // guardar en historial local persistente para la seccion "your ai history".
                    // la foto se guarda como archivo jpg (para cargar con coil) y el
                    // feedback se guarda en sharedpreferences
                    val ctx = appContext
                    if (ctx != null) {
                        val entryId = "pose_${eventId}_${System.currentTimeMillis()}"
                        val imagePath = if (photoBitmap != null) {
                            AiHistoryStore.saveImage(ctx, photoBitmap, entryId)
                        } else ""
                        AiHistoryStore.addEntry(ctx, AiHistoryEntry(
                            id = entryId,
                            type = "pose",
                            eventId = eventId,
                            feedback = feedback,
                            imagePath = imagePath
                        ))
                    }

                    _uiState.value = AiReviewState.PoseFeedback(feedback)
                } else {
                    _uiState.value = AiReviewState.Error("No se pudo obtener feedback de la IA.")
                }
            } catch (e: Exception) {
                _uiState.value = AiReviewState.Error("Error analizando pose: ${e.message}")
            }
        }
    }

    fun resetState() {
        _uiState.value = AiReviewState.Idle
    }
}
