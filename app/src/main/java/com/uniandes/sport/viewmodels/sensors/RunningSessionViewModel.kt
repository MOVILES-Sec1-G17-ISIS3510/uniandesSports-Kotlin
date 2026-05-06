package com.uniandes.sport.viewmodels.sensors

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.uniandes.sport.ai.OpenAiAnalyzerStrategy
import com.uniandes.sport.data.local.PendingRunAiPayload
import com.uniandes.sport.data.local.PendingRunAiStore
import com.uniandes.sport.models.RunSession
import com.uniandes.sport.sensors.BarometerManager
import com.uniandes.sport.sensors.LocationMetricsManager
import com.uniandes.sport.sensors.StepCounterManager
import com.uniandes.sport.viewmodels.running.FirestoreRunningViewModel
import com.uniandes.sport.workers.RunAiSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.DelicateCoroutinesApi

class RunningSessionViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val QUEUED_AI_MESSAGE =
            "AI feedback queued. It will be generated automatically when internet returns."
        private const val ANALYZING_AI_MESSAGE = "Analyzing performance..."
        private const val RETRYING_AI_MESSAGE =
            "AI feedback is being retried automatically. Check back in a moment."
        private const val AI_UNAVAILABLE_MESSAGE =
            "AI feedback is temporarily unavailable."
    }

    private val locationMgr = LocationMetricsManager(application)
    private val barometerMgr = BarometerManager(application)
    private val stepMgr = StepCounterManager(application)
    private val firestoreViewModel = FirestoreRunningViewModel()
    private val aiStrategy = OpenAiAnalyzerStrategy()

    val distanceKm = locationMgr.distanceKm
    val currentPace = locationMgr.currentPace
    val elevationGain = barometerMgr.elevationGain
    
    // El StepManager hace todo el trabajo pesado de analizar la racha de tiempo
    val cadence = stepMgr.cadence
    val currentSteps = stepMgr.currentSteps

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastSessionSummary = MutableStateFlow<RunSession?>(null)
    val lastSessionSummary: StateFlow<RunSession?> = _lastSessionSummary.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    fun startRunSession() {
        if (!_isRunning.value) {
            _isOfflineMode.value = !isNetworkConnected()
            locationMgr.startTracking()
            barometerMgr.startListening()
            stepMgr.startListening()
            _isRunning.value = true
        }
    }

    fun stopRunSession() {
        if (_isRunning.value) {
            val finalDistance = distanceKm.value
            val finalPace = currentPace.value
            val finalElevation = elevationGain.value
            val finalCadence = cadence.value

            locationMgr.stopTracking()
            barometerMgr.stopListening()
            stepMgr.stopListening() 
            _isRunning.value = false

            // Start AI Analysis and Save (BULLETPROOF - GlobalScope ensures it finishes even if VM is cleared)
            _isAnalyzing.value = true
            val tempSession = RunSession(
                distanceKm = finalDistance,
                pace = finalPace,
                elevationGain = finalElevation,
                cadence = finalCadence,
                aiFeedback = if (_isOfflineMode.value) {
                    QUEUED_AI_MESSAGE
                } else {
                    ""
                }
            )
            _lastSessionSummary.value = tempSession

            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch {
                // PRIMER PASO: Guardar los KM y datos básicos AL INSTANTE
                val docId = try {
                    val initialSession = tempSession.copy(
                        aiFeedback = if (_isOfflineMode.value) {
                            QUEUED_AI_MESSAGE
                        } else {
                            ANALYZING_AI_MESSAGE
                        }
                    )
                    firestoreViewModel.saveRunSession(initialSession)
                } catch (e: Exception) {
                    android.util.Log.e("RunningVM", "Error in first-stage save", e)
                    null
                }

                if (_isOfflineMode.value) {
                    if (docId != null) {
                        queuePendingAiFeedback(
                            runId = docId,
                            distanceKm = finalDistance,
                            pace = finalPace,
                            elevationGain = finalElevation,
                            cadence = finalCadence
                        )
                        _lastSessionSummary.value = tempSession.copy(id = docId)
                        observeRunFeedback(docId)
                    }
                    _isAnalyzing.value = false
                    return@launch
                }

                // SEGUNDO PASO: Obtener el feedback de la IA (esto puede tardar)
                val feedback = try {
                    aiStrategy.analyzeRunSession(
                        finalDistance, finalPace, finalElevation, finalCadence
                    )
                } catch (e: Exception) {
                    android.util.Log.e("RunningVM", "AI feedback failed", e)
                    null
                }

                if (feedback.isNullOrBlank()) {
                    if (docId != null) {
                        queuePendingAiFeedback(
                            runId = docId,
                            distanceKm = finalDistance,
                            pace = finalPace,
                            elevationGain = finalElevation,
                            cadence = finalCadence
                        )

                        val queuedSession = tempSession.copy(
                            id = docId,
                            aiFeedback = RETRYING_AI_MESSAGE
                        )
                        _lastSessionSummary.value = queuedSession
                        firestoreViewModel.saveRunSession(queuedSession)
                        observeRunFeedback(docId)
                    } else {
                        _lastSessionSummary.value = tempSession.copy(
                            aiFeedback = AI_UNAVAILABLE_MESSAGE
                        )
                    }

                    _isAnalyzing.value = false
                    return@launch
                }
                
                // TERCER PASO: Actualizar el documento con el feedback final
                val finalSession = tempSession.copy(id = docId ?: "", aiFeedback = feedback)
                _lastSessionSummary.value = finalSession

                if (docId != null) {
                    firestoreViewModel.saveRunSession(finalSession)
                }
                
                _isAnalyzing.value = false
            }
        }
    }

    fun clearSummary() {
        firestoreViewModel.clearObservedRun()
        _lastSessionSummary.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopRunSession()
    }

    private fun isNetworkConnected(): Boolean {
        val connectivityManager = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }

    private fun queuePendingAiFeedback(
        runId: String,
        distanceKm: Float,
        pace: String,
        elevationGain: Float,
        cadence: Int
    ) {
        val context = getApplication<Application>()
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return

        PendingRunAiStore.enqueue(
            context,
            PendingRunAiPayload(
                runId = runId,
                userId = userId,
                distanceKm = distanceKm,
                pace = pace,
                elevationGain = elevationGain,
                cadence = cadence
            )
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<RunAiSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    private fun observeRunFeedback(runId: String) {
        firestoreViewModel.observeRunSession(runId) { updatedRun ->
            val current = _lastSessionSummary.value ?: return@observeRunSession
            if (updatedRun.aiFeedback.isNotBlank() &&
                !updatedRun.aiFeedback.contains("queued", ignoreCase = true) &&
                !updatedRun.aiFeedback.contains("Analyzing", ignoreCase = true) &&
                !updatedRun.aiFeedback.contains("retried automatically", ignoreCase = true)
            ) {
                _lastSessionSummary.value = current.copy(
                    id = updatedRun.id,
                    userId = updatedRun.userId,
                    aiFeedback = updatedRun.aiFeedback
                )
            }
        }
    }
}
