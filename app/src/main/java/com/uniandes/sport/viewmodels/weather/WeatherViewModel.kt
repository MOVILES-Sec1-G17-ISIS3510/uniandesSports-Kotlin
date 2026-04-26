package com.uniandes.sport.viewmodels.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uniandes.sport.models.WeatherResponse
import com.uniandes.sport.repositories.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class WeatherState {
    object Loading : WeatherState()
    data class Success(val data: WeatherResponse) : WeatherState()
    data class Error(val message: String) : WeatherState()
}

class WeatherViewModel : ViewModel() {
    private val repository = WeatherRepository()

    private val _weatherState = MutableStateFlow<WeatherState>(WeatherState.Loading)
    val weatherState: StateFlow<WeatherState> = _weatherState.asStateFlow()

    private val appContext: android.content.Context?
        get() = try {
            com.google.firebase.FirebaseApp.getInstance().applicationContext
        } catch (_: Exception) {
            null
        }

    init {
        fetchWeather()
    }

    fun fetchWeather(lat: Double = 4.6097, lon: Double = -74.0817) {
        viewModelScope.launch {
            _weatherState.value = WeatherState.Loading
            repository.getWeatherData(lat, lon)
                .onSuccess {
                    // Cache successful response
                    appContext?.let { ctx ->
                        val prefs = ctx.getSharedPreferences("weather_cache", android.content.Context.MODE_PRIVATE)
                        prefs.edit()
                            .putFloat("temp", it.currentWeather.temperature.toFloat())
                            .putInt("code", it.currentWeather.weatherCode)
                            .apply()
                    }
                    _weatherState.value = WeatherState.Success(it)
                }
                .onFailure {
                    // Fallback to cache
                    val context = appContext
                    if (context != null) {
                        val prefs = context.getSharedPreferences("weather_cache", android.content.Context.MODE_PRIVATE)
                        if (prefs.contains("temp") && prefs.contains("code")) {
                            val cachedTemp = prefs.getFloat("temp", 0f).toDouble()
                            val cachedCode = prefs.getInt("code", 0)
                            
                            val cachedWeather = com.uniandes.sport.models.CurrentWeather(
                                temperature = cachedTemp,
                                windspeed = 0.0,
                                weatherCode = cachedCode,
                                time = ""
                            )
                            val cachedResponse = WeatherResponse(
                                currentWeather = cachedWeather,
                                daily = null
                            )
                            _weatherState.value = WeatherState.Success(cachedResponse)
                            return@launch
                        }
                    }
                    
                    _weatherState.value = WeatherState.Error(it.message ?: "Unknown error")
                }
        }
    }
}
