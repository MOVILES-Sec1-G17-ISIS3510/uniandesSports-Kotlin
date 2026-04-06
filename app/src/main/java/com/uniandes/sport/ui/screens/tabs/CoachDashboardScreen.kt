package com.uniandes.sport.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.AccountCircle
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
                        Text(
                            "Welcome, ${profesor?.nombre ?: "Coach"}",
                            fontWeight = FontWeight.Black, 
                            fontSize = 18.sp
                        )
                        Text(
                            "${(profesor?.deporte ?: "Sport").uppercase()} DASHBOARD",
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate(com.uniandes.sport.ui.navigation.Screen.Perfil.route) }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { 
                        android.widget.Toast.makeText(context, "¡Editar Perfil Próximamente!", android.widget.Toast.LENGTH_SHORT).show() 
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit profile", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (profesor == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // Balance Card
            val numPrecio = profesor.precio.replace(Regex("\\D"), "").toIntOrNull() ?: 0
            val ingresosEstimados = profesor.sessionsDelivered * numPrecio
            val formattedIngresos = if (ingresosEstimados > 0) "$$ingresosEstimados" else "$0"

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "INGRESOS ESTIMADOS", 
                        color = Color.White.copy(alpha = 0.7f), 
                        fontSize = 11.sp, 
                        fontWeight = FontWeight.Black, 
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        formattedIngresos, 
                        color = Color.White, 
                        fontSize = 42.sp, 
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SESIONES", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(profesor.sessionsDelivered.toString(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        }
                        Box(modifier = Modifier.height(32.dp).width(1.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PRÓXIMO PAGO", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("FIN DE MES", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // Quick Stats
            Text(
                "RESUMEN DE RENDIMIENTO", 
                fontWeight = FontWeight.Black, 
                fontSize = 13.sp, 
                color = MaterialTheme.colorScheme.onSurfaceVariant, 
                letterSpacing = 1.sp
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardStat(
                    title = "Rating",
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
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().clickable { 
                    android.widget.Toast.makeText(context, "¡Gestor de Horarios Próximamente!", android.widget.Toast.LENGTH_SHORT).show() 
                }
            ) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Gestionar Horario", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Configura tu disponibilidad", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(
                        Icons.Default.ArrowBack, 
                        contentDescription = null, 
                        modifier = Modifier.rotate(180f).size(20.dp), 
                        tint = Color.LightGray
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardStat(title: String, value: String, icon: ImageVector, iconColor: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(iconColor.copy(alpha=0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(title.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
