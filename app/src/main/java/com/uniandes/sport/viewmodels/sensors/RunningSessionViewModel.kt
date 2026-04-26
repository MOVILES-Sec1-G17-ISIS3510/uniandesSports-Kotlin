package com.uniandes.sport.viewmodels.sensors

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uniandes.sport.ai.OpenAiAnalyzerStrategy
import com.uniandes.sport.models.RunSession
import com.uniandes.sport.sensors.BarometerManager
import com.uniandes.sport.sensors.LocationMetricsManager
import com.uniandes.sport.sensors.StepCounterManager
import com.uniandes.sport.viewmodels.running.FirestoreRunningViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.DelicateCoroutinesApi

class RunningSessionViewModel(application: Application) : AndroidViewModel(application) {

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

    fun startRunSession() {
        if (!_isRunning.value) {
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
                cadence = finalCadence
            )
            _lastSessionSummary.value = tempSession

            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch {
                // PRIMER PASO: Guardar los KM y datos básicos AL INSTANTE
                val docId = try {
                    val initialSession = tempSession.copy(aiFeedback = "Analyzing performance...")
                    firestoreViewModel.saveRunSession(initialSession)
                } catch (e: Exception) {
                    android.util.Log.e("RunningVM", "Error in first-stage save", e)
                    null
                }

                // SECUNDO PASO: Obtener el feedback de la IA (esto puede tardar)
                val feedback = try {
                     aiStrategy.analyzeRunSession(
                        finalDistance, finalPace, finalElevation, finalCadence
                    )
                } catch (e: Exception) {
                    android.util.Log.e("RunningVM", "AI feedback failed", e)
                    null
                } ?: "Great run! Saved locally. 🌍 (Connect to the internet next time for deep AI Analysis)"
                
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
        _lastSessionSummary.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopRunSession()
    }
}
