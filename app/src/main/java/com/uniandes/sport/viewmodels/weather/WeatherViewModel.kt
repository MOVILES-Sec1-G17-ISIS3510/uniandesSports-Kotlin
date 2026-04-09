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

    init {
        fetchWeather()
    }

    fun fetchWeather(lat: Double = 4.6097, lon: Double = -74.0817) {
        viewModelScope.launch {
            _weatherState.value = WeatherState.Loading
            repository.getWeatherData(lat, lon)
                .onSuccess {
                    _weatherState.value = WeatherState.Success(it)
                }
                .onFailure {
                    _weatherState.value = WeatherState.Error(it.message ?: "Unknown error")
                }
        }
    }
}
