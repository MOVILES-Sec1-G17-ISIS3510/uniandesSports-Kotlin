package com.uniandes.sport.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

class BarometerManager(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private val _elevationGain = MutableStateFlow(0f)
    val elevationGain: StateFlow<Float> = _elevationGain.asStateFlow()

    private var previousAltitude: Float? = null

    fun startListening() {
        pressureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        previousAltitude = null
        _elevationGain.value = 0f
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PRESSURE) {
            val pressure = event.values[0]
            val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
            
            if (previousAltitude == null) {
                previousAltitude = altitude
                return
            }
            
            val delta = altitude - previousAltitude!!
            if (delta > 0.5f) { // threshold para evitar ruido del sensor
                _elevationGain.value += delta
                previousAltitude = altitude
            } else if (delta < -0.5f) {
                // Si bajamos, actualizamos el punto de referencia pero no sumamos a "Gain"
                previousAltitude = altitude
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
