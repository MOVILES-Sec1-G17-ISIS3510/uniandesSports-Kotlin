package com.uniandes.sport.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

class StepCounterManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val _currentSteps = MutableStateFlow(0)
    val currentSteps: StateFlow<Int> = _currentSteps.asStateFlow()

    private val _cadence = MutableStateFlow(0)
    val cadence: StateFlow<Int> = _cadence.asStateFlow()

    private val prefs = context.getSharedPreferences("StepCounterPrefs", Context.MODE_PRIVATE)
    
    // For calculating Cadence
    private val stepTimestamps = mutableListOf<Long>()

    fun startListening() {
        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            Log.e("StepCounter", "No step counter sensor found on this device!")
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsBoot = event.values[0].toInt()
            
            // Check day logic to reset counts
            val lastSavedDay = prefs.getInt("last_saved_day", -1)
            val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

            if (lastSavedDay != currentDay) {
                // It's a new day! Save this raw count as tomorrow's offset
                prefs.edit()
                    .putInt("daily_offset", totalStepsBoot)
                    .putInt("last_saved_day", currentDay)
                    .apply()
            }

            val offset = prefs.getInt("daily_offset", totalStepsBoot)
            val stepsToday = totalStepsBoot - offset
            
            _currentSteps.value = stepsToday.coerceAtLeast(0)

            // Cadence Calculation (Steps per Minute)
            val now = System.currentTimeMillis()
            stepTimestamps.add(now)
            
            // Keep only timestamps from the last 10 seconds
            val windowStart = now - 10000 
            stepTimestamps.removeAll { it < windowStart }
            
            if (stepTimestamps.size > 2) {
                // Number of steps in the window (excluding the very first one which is our t0)
                val stepsInWindow = stepTimestamps.size - 1
                val timeSpanMillis = stepTimestamps.last() - stepTimestamps.first()
                if (timeSpanMillis > 0) {
                    val spm = (stepsInWindow.toFloat() / timeSpanMillis.toFloat() * 60000f).toInt()
                    _cadence.value = spm
                }
            } else {
                _cadence.value = 0
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }
}
