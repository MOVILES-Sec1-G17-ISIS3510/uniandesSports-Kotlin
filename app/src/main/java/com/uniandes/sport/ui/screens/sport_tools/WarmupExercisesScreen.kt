package com.uniandes.sport.ui.screens.sport_tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniandes.sport.models.warmup.WarmupExercise
import com.uniandes.sport.ui.theme.ArchivoFamily
import com.uniandes.sport.viewmodels.warmup.WarmupRoutinesViewModel
import kotlinx.coroutines.launch

// pantalla de ejercicios: carrusel horizontal con 4 cards aleatorias.
// el shuffle baraja el pool descargado sin pegarle a la red (reusa cache l1 del servicio).
// si el usuario llega aqui directamente (deep link) y la cache esta vacia,
// intenta refrescar haciendo fetch con los args recibidos
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarmupExercisesScreen(
    category: String,
    intensity: String,
    onNavigateBack: () -> Unit,
    viewModel: WarmupRoutinesViewModel = viewModel()
) {
    val exercises by viewModel.selectedExercises.collectAsState()
    val pool by viewModel.exercisesPool.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // al entrar: primero intenta usar la cache l1 del servicio sin red;
    // si esta vacia, hace fetch real (solo deberia pasar en deep link)
    LaunchedEffect(category, intensity) {
        viewModel.loadFromCache()
        if (viewModel.exercisesPool.value.isEmpty()) {
            viewModel.fetchExercises(category, intensity)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Warm-Up",
                            fontWeight = FontWeight.Black,
                            fontFamily = ArchivoFamily,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 18.sp
                        )
                        Text(
                            "$category · $intensity",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading && exercises.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                exercises.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No exercises available",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> ExercisesPager(
                    exercises = exercises,
                    poolSize = pool.size,
                    onShuffle = { viewModel.shuffle() }
                )
            }
        }
    }
}

@Composable
private fun ExercisesPager(
    exercises: List<WarmupExercise>,
    poolSize: Int,
    onShuffle: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { exercises.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp
        ) { page ->
            ExerciseCard(exercise = exercises[page], position = page + 1, total = exercises.size)
        }

        // indicador de pagina con dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(exercises.size) { i ->
                val active = i == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                )
            }
        }

        Button(
            onClick = {
                onShuffle()
                scope.launch { pagerState.scrollToPage(0) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(56.dp),
            shape = RoundedCornerShape(20.dp),
            enabled = poolSize > 4,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.Shuffle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Shuffle exercises", fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ExerciseCard(exercise: WarmupExercise, position: Int, total: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                1.5.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                RoundedCornerShape(28.dp)
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    "$position / $total",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    exercise.quantity.ifBlank { "—" },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Text(
            exercise.name,
            fontFamily = ArchivoFamily,
            fontWeight = FontWeight.Black,
            fontSize = 26.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 30.sp
        )

        Text(
            exercise.description,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
