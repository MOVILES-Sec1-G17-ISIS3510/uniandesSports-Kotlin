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
    data class Success(
        val data: WeatherResponse,
        val lastUpdatedMillis: Long,
        val isFromCache: Boolean,
        val showOfflineMessage: Boolean = false
    ) : WeatherState()
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
            val cachedState = loadCachedWeather()
            if (cachedState != null) {
                _weatherState.value = cachedState
            } else {
                _weatherState.value = WeatherState.Loading
            }
            repository.getWeatherData(lat, lon)
                .onSuccess {
                    // Cache successful response
                    appContext?.let { ctx ->
                        val prefs = ctx.getSharedPreferences("weather_cache", android.content.Context.MODE_PRIVATE)
                        val fetchedAt = System.currentTimeMillis()
                        prefs.edit()
                            .putFloat("temp", it.currentWeather.temperature.toFloat())
                            .putFloat("windspeed", it.currentWeather.windspeed.toFloat())
                            .putInt("code", it.currentWeather.weatherCode)
                            .putString("time", it.currentWeather.time)
                            .putLong("last_updated_millis", fetchedAt)
                            .apply()
                        _weatherState.value = WeatherState.Success(
                            data = it,
                            lastUpdatedMillis = fetchedAt,
                            isFromCache = false,
                            showOfflineMessage = false
                        )
                    }
                    if (appContext == null) {
                        _weatherState.value = WeatherState.Success(
                            data = it,
                            lastUpdatedMillis = System.currentTimeMillis(),
                            isFromCache = false,
                            showOfflineMessage = false
                        )
                    }
                }
                .onFailure {
                    loadCachedWeather(showOfflineMessage = true)?.let { cached ->
                        _weatherState.value = cached
                        return@launch
                    }
                    
                    _weatherState.value = WeatherState.Error(it.message ?: "Unknown error")
                }
        }
    }

    private fun loadCachedWeather(showOfflineMessage: Boolean = false): WeatherState.Success? {
        val context = appContext ?: return null
        val prefs = context.getSharedPreferences("weather_cache", android.content.Context.MODE_PRIVATE)
        if (!prefs.contains("temp") || !prefs.contains("code")) return null

        val cachedTemp = prefs.getFloat("temp", 0f).toDouble()
        val cachedWind = prefs.getFloat("windspeed", 0f).toDouble()
        val cachedCode = prefs.getInt("code", 0)
        val cachedTime = prefs.getString("time", "") ?: ""
        val cachedUpdatedAt = prefs.getLong("last_updated_millis", 0L)

        val cachedWeather = com.uniandes.sport.models.CurrentWeather(
            temperature = cachedTemp,
            windspeed = cachedWind,
            weatherCode = cachedCode,
            time = cachedTime
        )
        val cachedResponse = WeatherResponse(
            currentWeather = cachedWeather,
            daily = null
        )

        return WeatherState.Success(
            data = cachedResponse,
            lastUpdatedMillis = cachedUpdatedAt,
            isFromCache = true,
            showOfflineMessage = showOfflineMessage
        )
    }
}
