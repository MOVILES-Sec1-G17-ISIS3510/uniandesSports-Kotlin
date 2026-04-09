package com.uniandes.sport.models

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    @SerializedName("current_weather")
    val currentWeather: CurrentWeather,
    val daily: DailyForecast? = null
)

data class CurrentWeather(
    val temperature: Double,
    val windspeed: Double,
    @SerializedName("weathercode")
    val weatherCode: Int,
    val time: String
)

data class DailyForecast(
    val time: List<String>,
    @SerializedName("weathercode")
    val weatherCodes: List<Int>,
    @SerializedName("temperature_2m_max")
    val maxTemps: List<Double>,
    @SerializedName("temperature_2m_min")
    val minTemps: List<Double>
)

fun getWeatherDescription(code: Int): String {
    return when (code) {
        0 -> "Clear sky"
        1, 2, 3 -> "Mainly clear, partly cloudy, and overcast"
        45, 48 -> "Fog and depositing rime fog"
        51, 53, 55 -> "Drizzle: Light, moderate, and dense intensity"
        56, 57 -> "Freezing Drizzle: Light and dense intensity"
        61, 63, 65 -> "Rain: Slight, moderate and heavy intensity"
        66, 67 -> "Freezing Rain: Light and heavy intensity"
        71, 73, 75 -> "Snow fall: Slight, moderate, and heavy intensity"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers: Slight, moderate, and violent"
        85, 86 -> "Snow showers slight and heavy"
        95 -> "Thunderstorm: Slight or moderate"
        96, 99 -> "Thunderstorm with slight and heavy hail"
        else -> "Unknown weather"
    }
}
