package com.uniandes.sport.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniandes.sport.models.Profesor
import com.uniandes.sport.viewmodels.profesores.FirestoreProfesoresViewModel
import com.uniandes.sport.viewmodels.profesores.ProfesoresViewModelInterface
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachDashboardScreen(
    profesorId: String,
    profesoresViewModel: ProfesoresViewModelInterface = viewModel<FirestoreProfesoresViewModel>(),
    onNavigate: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val profesores by profesoresViewModel.profesores.collectAsState()
    val profesor = profesores.find { it.id == profesorId }

    LaunchedEffect(Unit) {
        if (profesores.isEmpty()) {
            profesoresViewModel.fetchProfesores()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Hola, ${profesor?.nombre ?: "Profesor"}", fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("Panel de ${profesor?.deporte ?: ""}", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    // Botón Mock de Editar Perfil
                    IconButton(onClick = { 
                        android.widget.Toast.makeText(context, "¡Editar Perfil Próximamente!", android.widget.Toast.LENGTH_SHORT).show() 
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar Perfil", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        if (profesor == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF9FAFB))
                .verticalScroll(rememberScrollState())
                .padding(top = paddingValues.calculateTopPadding() + 8.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // Balance Card
            val numPrecio = profesor.precio.replace(Regex("\\D"), "").toIntOrNull() ?: 0
            val ingresosEstimados = profesor.sessionsDelivered * numPrecio
            val formattedIngresos = if (ingresosEstimados > 0) "$$ingresosEstimados" else "$0"

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("INGRESOS ESTIMADOS", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(formattedIngresos, color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Clases", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                            Text(profesor.sessionsDelivered.toString(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(modifier = Modifier.height(40.dp).width(1.dp).background(Color.White.copy(alpha = 0.3f)))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Próximo Pago", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                            Text("Fin de Mes", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Quick Stats
            Text("RESUMEN", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray, letterSpacing = 1.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardStat(
                    title = "Calificación",
                    value = String.format(java.util.Locale.US, "%.1f", profesor.rating),
                    icon = Icons.Default.Star,
                    iconColor = Color(0xFFFBBF24),
                    modifier = Modifier.weight(1f)
                )
                DashboardStat(
                    title = "Alumnos",
                    value = if (profesor.sessionsDelivered == 0) "0" else (profesor.totalReviews).toString(), 
                    icon = Icons.Default.Groups,
                    iconColor = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardStat(
                    title = "Total Sesiones",
                    value = profesor.sessionsDelivered.toString(),
                    icon = Icons.Default.TrendingUp,
                    iconColor = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
                DashboardStat(
                    title = "Ranking",
                    value = "#${profesor.rankInSport}",
                    icon = Icons.Default.CalendarMonth,
                    iconColor = Color(0xFF8B5CF6),
                    modifier = Modifier.weight(1f)
                )
            }

            // Manage Schedule Action
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().clickable { 
                    android.widget.Toast.makeText(context, "¡Gestor de Horarios Próximamente!", android.widget.Toast.LENGTH_SHORT).show() 
                }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFE0E7FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF4F46E5))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Gestionar Horario", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Configura tu disponibilidad para alumnos", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.rotate(180f), tint = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun DashboardStat(title: String, value: String, icon: ImageVector, iconColor: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(iconColor.copy(alpha=0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.Black)
        }
    }
}
