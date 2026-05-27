package com.uniandes.sport.ui.screens.sport_tools

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniandes.sport.data.preferences.WarmupPreferencesRepository
import com.uniandes.sport.ui.components.OfflineConnectivityBanner
import com.uniandes.sport.ui.components.rememberIsOnline
import com.uniandes.sport.ui.theme.ArchivoFamily
import com.uniandes.sport.viewmodels.log.FirebaseLogViewModel
import com.uniandes.sport.viewmodels.log.LogViewModelInterface
import com.uniandes.sport.viewmodels.warmup.WarmupRoutinesViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLEncoder

// pantalla de configuracion de warm-up.
// el usuario escoge categoria + intensidad. los filtros se restauran desde datastore
// al entrar y se guardan al cambiar (resuelve "missing state persistence").
// trabaja en modo online o offline: si no hay red, el viewmodel pide al service
// que lea de room/archivo en lugar de tirar un toast generico
private val CATEGORIES = listOf("Core Activation", "Dynamic Stretching", "Joint Mobility")
private val INTENSITIES = listOf("Low", "High")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WarmupConfigScreen(
    onNavigateBack: () -> Unit,
    onNavigateToExercises: (category: String, intensity: String) -> Unit,
    viewModel: WarmupRoutinesViewModel = viewModel(),
    logViewModel: LogViewModelInterface = viewModel<FirebaseLogViewModel>()
) {
    val context = LocalContext.current
    val isOnline = rememberIsOnline()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val scope = rememberCoroutineScope()
    val prefsRepo = remember { WarmupPreferencesRepository.getInstance(context) }

    var selectedCategory by remember { mutableStateOf(CATEGORIES.first()) }
    var selectedIntensity by remember { mutableStateOf(INTENSITIES.first()) }

    // restaurar ultima seleccion de datastore SOLO al entrar (first()).
    // si usaramos collect() la seleccion local se sobreescribiria cada vez que
    // el datastore emita despues de setlastcategory/setlastintensity
    LaunchedEffect(Unit) {
        val prefs = prefsRepo.preferencesFlow.first()
        if (prefs.lastCategory in CATEGORIES) selectedCategory = prefs.lastCategory
        if (prefs.lastIntensity in INTENSITIES) selectedIntensity = prefs.lastIntensity
    }

    // mostrar errores con toast (solo casos donde la cache tambien esta vacia)
    LaunchedEffect(error) {
        error?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Warm-Up Routines",
                        fontWeight = FontWeight.Black,
                        fontFamily = ArchivoFamily,
                        color = MaterialTheme.colorScheme.primary
                    )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // banner offline: indica que se mostraran rutinas cacheadas
            OfflineConnectivityBanner(
                offlineMessage = "No internet — showing cached routines from your last session."
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    "Pick a category and intensity. We will surface 4 random exercises from the matching routines.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ChipsSection(
                    title = "Category",
                    options = CATEGORIES,
                    selected = selectedCategory,
                    onSelect = {
                        selectedCategory = it
                        scope.launch { prefsRepo.setLastCategory(it) }
                    }
                )

                ChipsSection(
                    title = "Intensity",
                    options = INTENSITIES,
                    selected = selectedIntensity,
                    onSelect = {
                        selectedIntensity = it
                        scope.launch { prefsRepo.setLastIntensity(it) }
                    }
                )

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = {
                        // guardar seleccion atomica antes de navegar
                        scope.launch {
                            prefsRepo.setLastSelection(selectedCategory, selectedIntensity)
                        }
                        // viewmodel decide internamente: si online → firestore + cache,
                        // si offline → room/archivo. la ui no necesita logica adicional
                        viewModel.fetchExercises(
                            category = selectedCategory,
                            intensity = selectedIntensity,
                            isOnline = isOnline
                        ) {
                            // BQ Type 3: registrar la combinacion usada para medir demanda
                            // por (category, intensity) y poder decidir cuales invertir/deprecar
                            logViewModel.log(
                                screen = "WarmupConfigScreen",
                                action = "warmup_combination_used",
                                params = mapOf(
                                    "category" to selectedCategory,
                                    "intensity" to selectedIntensity,
                                    "result_count" to viewModel.exercisesPool.value.size.toString(),
                                    "is_online" to isOnline.toString()
                                )
                            )
                            val cat = URLEncoder.encode(selectedCategory, "UTF-8")
                            val int = URLEncoder.encode(selectedIntensity, "UTF-8")
                            onNavigateToExercises(cat, int)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isOnline) "Find routines" else "Find cached routines",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsSection(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { onSelect(option) }
                ) {
                    Text(
                        option,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
