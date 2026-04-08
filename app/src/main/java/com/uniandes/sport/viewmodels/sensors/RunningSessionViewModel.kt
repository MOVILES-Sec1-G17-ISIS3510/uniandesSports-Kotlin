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

            // Start AI Analysis and Save
            _isAnalyzing.value = true
            val tempSession = RunSession(
                distanceKm = finalDistance,
                pace = finalPace,
                elevationGain = finalElevation,
                cadence = finalCadence
            )
            _lastSessionSummary.value = tempSession

            viewModelScope.launch {
                val feedback = aiStrategy.analyzeRunSession(
                    finalDistance, finalPace, finalElevation, finalCadence
                ) ?: "Amazing run! You're getting stronger every day."
                
                val finalSession = tempSession.copy(aiFeedback = feedback)
                _lastSessionSummary.value = finalSession
                
                firestoreViewModel.saveRunSession(finalSession)
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
