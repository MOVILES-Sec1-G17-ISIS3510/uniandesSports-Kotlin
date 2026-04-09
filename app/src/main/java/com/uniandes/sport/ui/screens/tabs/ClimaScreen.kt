package com.uniandes.sport.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniandes.sport.models.CurrentWeather
import com.uniandes.sport.models.DailyForecast
import com.uniandes.sport.models.getWeatherDescription
import com.uniandes.sport.viewmodels.weather.WeatherState
import com.uniandes.sport.viewmodels.weather.WeatherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClimaScreen(
    onNavigate: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    viewModel: WeatherViewModel = viewModel()
) {
    val state by viewModel.weatherState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CLIMATE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val s = state) {
                is WeatherState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is WeatherState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
                is WeatherState.Success -> {
                    WeatherContent(s.data.currentWeather, s.data.daily)
                }
            }
        }
    }
}

@Composable
fun WeatherContent(current: CurrentWeather, daily: DailyForecast?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("BOGOTÁ, CO", fontWeight = FontWeight.Black, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
        Text("Today's Condition", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(32.dp))

        // Main Temp Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = getWeatherIcon(current.weatherCode),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${current.temperature.toInt()}°",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = getWeatherDescription(current.weatherCode).uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Details Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WeatherDetailCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Air,
                label = "WIND",
                value = "${current.windspeed} km/h"
            )
            WeatherDetailCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.DeviceThermostat,
                label = "FEELS LIKE",
                value = "${current.temperature.toInt()}°"
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Forecast Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("NEXT DAYS", fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text("7-Day Forecast", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Forecast Row
        daily?.let {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(it.time) { index, date ->
                    ForecastCard(
                        date = date.substring(8, 10) + "/" + date.substring(5, 7),
                        temp = "${it.maxTemps[index].toInt()}°",
                        icon = getWeatherIcon(it.weatherCodes[index])
                    )
                }
            }
        }
    }
}

@Composable
fun WeatherDetailCard(modifier: Modifier, icon: ImageVector, label: String, value: String) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun ForecastCard(date: String, temp: String, icon: ImageVector) {
    Surface(
        modifier = Modifier.width(80.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(date, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(temp, fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

fun getWeatherIcon(code: Int): ImageVector {
    return when (code) {
        0 -> Icons.Default.WbSunny
        1, 2, 3 -> Icons.Default.Cloud
        in 45..48 -> Icons.Default.Cloud
        in 51..67 -> Icons.Default.BeachAccess // Umbrella/Rain substitute if missing
        in 71..77 -> Icons.Default.AcUnit
        in 80..82 -> Icons.Default.Opacity // Rain substitute
        in 85..86 -> Icons.Default.AcUnit
        in 95..99 -> Icons.Default.FlashOn // Thunderstorm substitute
        else -> Icons.Default.Cloud
    }
}
