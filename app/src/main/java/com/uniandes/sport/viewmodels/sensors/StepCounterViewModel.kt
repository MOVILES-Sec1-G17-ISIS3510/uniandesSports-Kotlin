package com.uniandes.sport.viewmodels.sensors

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.uniandes.sport.sensors.StepCounterManager

class StepCounterViewModel(application: Application) : AndroidViewModel(application) {

    private val stepManager = StepCounterManager(application)

    val currentSteps = stepManager.currentSteps

    // Un objetivo fijo para el challenge principal (e.g. 10,000 steps)
    val dailyGoal = 10000

    fun startTracking() {
        stepManager.startListening()
    }

    fun stopTracking() {
        stepManager.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        stopTracking()
    }
}
