package com.uniandes.sport.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class LocationMetricsManager(context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    
    private val _distanceKm = MutableStateFlow(0f)
    val distanceKm: StateFlow<Float> = _distanceKm.asStateFlow()

    private val _currentPace = MutableStateFlow("0:00") // min/km
    val currentPace: StateFlow<String> = _currentPace.asStateFlow()

    private var lastLocation: Location? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                calculateMetrics(location)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateIntervalMillis(1000) // Every second update for running
            .build()
            
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        lastLocation = null
        _distanceKm.value = 0f
        _currentPace.value = "0:00"
    }

    private fun calculateMetrics(newLocation: Location) {
        lastLocation?.let { last ->
            val distanceMeters = last.distanceTo(newLocation)
            if (distanceMeters > 0.5f) { // filter micro GPS jitter
                val newTotalKm = _distanceKm.value + (distanceMeters / 1000f)
                _distanceKm.value = newTotalKm
            }
        }
        
        // Speed in m/s -> pace in min/km
        if (newLocation.hasSpeed() && newLocation.speed > 0.5f) { // moving faster than ~2km/h
            val speedKmH = newLocation.speed * 3.6f
            val paceDecimal = 60f / speedKmH
            val minutes = paceDecimal.toInt()
            val seconds = ((paceDecimal - minutes) * 60).roundToInt()
            _currentPace.value = String.format("%d:%02d", minutes, seconds)
        } else {
            _currentPace.value = "0:00" // Stopped or hovering
        }

        lastLocation = newLocation
    }
}
