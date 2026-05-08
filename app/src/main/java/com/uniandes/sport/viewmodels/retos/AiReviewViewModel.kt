package com.uniandes.sport.viewmodels.retos

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage
import com.uniandes.sport.ai.AiAnalyzerStrategy
import com.uniandes.sport.data.cache.AiResultCache
import com.uniandes.sport.data.local.AiHistoryEntry
import com.uniandes.sport.data.local.AiHistoryStore
import com.uniandes.sport.models.Reto
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID

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

                // solo guardar en historial los challenges que realmente avanzaron
                val advancedChallenges = allRelevantChallengeIds.filter { retoId ->
                    val newVal = newProgressMap[retoId] ?: 0.0
                    val oldVal = oldAnalysis[retoId] ?: 0.0
                    newVal > 0 && Math.abs(newVal - oldVal) > 0.01
                }

                // buscar los nombres de los challenges que avanzaron para el feedback
                val advancedNames = advancedChallenges.mapNotNull { retoId ->
                    allRetos.find { it.id == retoId }?.let { reto ->
                        val newVal = newProgressMap[retoId] ?: 0.0
                        "${reto.title}: +${String.format("%.0f", newVal)}%"
                    }
                }

                val cacheKey = AiResultCache.trackKey(eventId, uid)
                val resultSummary = if (advancedNames.isNotEmpty())
                    "Progress updated in ${advancedNames.size} challenge(s):\n${advancedNames.joinToString("\n")}"
                else
                    "Activity recorded. No challenge progress applied."
                AiResultCache.put(cacheKey, resultSummary)

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
        // cada analisis de pose es unico (foto diferente), no usamos cache aqui.
        // el cache lru se usa para consultar resultados anteriores desde el historial,
        // no para bloquear nuevos analisis

        _uiState.value = AiReviewState.Loading
        viewModelScope.launch {
            try {
                val feedback = analyzerStrategy.analyzePose(base64Image)
                if (feedback != null) {
                    // guardar en cache lru con timestamp unico para que el historial pueda consultarlo
                    val cacheKey = AiResultCache.poseKey(eventId, "${System.currentTimeMillis()}")
                    AiResultCache.put(cacheKey, feedback)

                    // guardar en historial local y subir foto a firebase storage.
                    // coil cargara la imagen desde la url (online-first):
                    // 1ra vez: descarga de la url y cachea en memoria + disco
                    // 2da vez: sirve desde memoria (cache hit, sin red ni disco)
                    // offline: sirve desde disco cache
                    val ctx = appContext
                    if (ctx != null) {
                        val entryId = "pose_${eventId}_${System.currentTimeMillis()}"
                        // guardar copia local como fallback
                        val localPath = if (photoBitmap != null) {
                            AiHistoryStore.saveImage(ctx, photoBitmap, entryId)
                        } else ""

                        // subir a firebase storage para obtener url publica
                        // coil usara esta url con su cache online-first
                        if (photoBitmap != null) {
                            uploadToStorage(photoBitmap) { url ->
                                AiHistoryStore.addEntry(ctx, AiHistoryEntry(
                                    id = entryId,
                                    type = "pose",
                                    eventId = eventId,
                                    feedback = feedback,
                                    imagePath = url
                                ))
                            }
                        } else {
                            AiHistoryStore.addEntry(ctx, AiHistoryEntry(
                                id = entryId,
                                type = "pose",
                                eventId = eventId,
                                feedback = feedback,
                                imagePath = localPath
                            ))
                        }
                    }

                    _uiState.value = AiReviewState.PoseFeedback(feedback)
                } else {
                    _uiState.value = AiReviewState.Error("Could not get AI feedback.")
                }
            } catch (e: Exception) {
                _uiState.value = AiReviewState.Error("Error analyzing pose: ${e.message}")
            }
        }
    }

    // subir imagen a firebase storage y retornar la url publica.
    // coil usa esta url para su estrategia online-first:
    // network -> disk cache -> memory cache
    private fun uploadToStorage(bitmap: Bitmap, onUrl: (String) -> Unit) {
        val uuid = UUID.randomUUID().toString()
        val ref = FirebaseStorage.getInstance().reference.child("ai_poses/$uuid.jpg")
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        ref.putBytes(baos.toByteArray())
            .continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                ref.downloadUrl
            }
            .addOnSuccessListener { uri -> onUrl(uri.toString()) }
            .addOnFailureListener { e ->
                android.util.Log.e("AiReviewVM", "error subiendo foto a storage", e)
            }
    }

    fun resetState() {
        _uiState.value = AiReviewState.Idle
    }
}
