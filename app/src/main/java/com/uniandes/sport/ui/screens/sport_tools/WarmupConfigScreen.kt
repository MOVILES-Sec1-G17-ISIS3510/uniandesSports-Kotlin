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
import com.uniandes.sport.ui.components.rememberIsOnline
import com.uniandes.sport.ui.theme.ArchivoFamily
import com.uniandes.sport.viewmodels.warmup.WarmupRoutinesViewModel
import java.net.URLEncoder

// pantalla de configuracion de warm-up.
// el usuario escoge categoria + intensidad y dispara el fetch a firestore.
// requiere internet para descargar la primera vez (la cache l1 sirve si repite combinacion)
private val CATEGORIES = listOf("Core Activation", "Dynamic Stretching", "Joint Mobility")
private val INTENSITIES = listOf("Low", "High")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WarmupConfigScreen(
    onNavigateBack: () -> Unit,
    onNavigateToExercises: (category: String, intensity: String) -> Unit,
    viewModel: WarmupRoutinesViewModel = viewModel()
) {
    val context = LocalContext.current
    val isOnline = rememberIsOnline()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var selectedCategory by remember { mutableStateOf(CATEGORIES.first()) }
    var selectedIntensity by remember { mutableStateOf(INTENSITIES.first()) }

    // espejo de flutter: toast con el mensaje exacto cuando algo falla
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
                onSelect = { selectedCategory = it }
            )

            ChipsSection(
                title = "Intensity",
                options = INTENSITIES,
                selected = selectedIntensity,
                onSelect = { selectedIntensity = it }
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    // espejo flutter: validacion temprana de internet con mensaje explicito.
                    // (fase 2 reemplaza esto con lectura desde room para vista protegida)
                    if (!isOnline) {
                        Toast.makeText(
                            context,
                            "Internet connection is required to fetch routines",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }
                    viewModel.fetchExercises(selectedCategory, selectedIntensity) {
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
                    Text("Find routines", fontWeight = FontWeight.Black, fontSize = 16.sp)
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
