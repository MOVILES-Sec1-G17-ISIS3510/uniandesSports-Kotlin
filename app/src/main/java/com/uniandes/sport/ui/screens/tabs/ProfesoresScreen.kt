package com.uniandes.sport.ui.screens.tabs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.uniandes.sport.ui.navigation.Screen
import androidx.compose.material.icons.filled.*
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.uniandes.sport.models.Profesor
import com.uniandes.sport.models.ProfesorBuilder
import com.uniandes.sport.models.Review
import com.uniandes.sport.viewmodels.profesores.ProfesoresViewModelInterface
import com.uniandes.sport.viewmodels.auth.FirebaseAuthViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import com.uniandes.sport.ui.components.FabMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState

@OptIn(ExperimentalMaterialApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfesoresScreen(
    profesoresViewModel: ProfesoresViewModelInterface,
    authViewModel: FirebaseAuthViewModel = viewModel(),
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val profesores by profesoresViewModel.profesores.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    var showBecomeCoachDialog by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }

    val deportes = listOf("All", "Soccer", "Tennis", "Basketball", "Swimming", "Running")

    var userUid by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { 
            isRefreshing = true
            profesoresViewModel.refreshProfesores {
                isRefreshing = false
            }
        }
    )

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        profesoresViewModel.fetchProfesores()
        authViewModel.getUser(
            onSuccess = { user -> userUid = user.uid },
            onFailure = { /* Not logged in or error */ }
        )
    }

    val isCurrentUserCoach = profesores.any { it.id == userUid }

    val filteredProfesores = if (selectedFilter == "All") {
        profesores
    } else {
        profesores.filter { it.deporte == selectedFilter }
    }

    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {

            // Sport Filter
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(deportes) { dep ->
                    val isSelected = selectedFilter == dep
                    
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = dep },
                        label = { Text(if (dep == "All") "All Coaches" else dep, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium) },
                        leadingIcon = {
                            if (dep == "All") {
                                Surface(
                                    modifier = Modifier.size(24.dp),
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Sports, 
                                            contentDescription = null, 
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            } else {
                                com.uniandes.sport.ui.components.SportIconBox(sport = dep, size = 24.dp)
                            }
                        },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color.Transparent,
                            selectedBorderColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            }

            // Coach Cards
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (filteredProfesores.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No coaches found for this sport", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(filteredProfesores) { prof ->
                        CoachCard(
                            profesor = prof,
                            onViewProfile = { onNavigate(Screen.CoachProfile.route.replace("{profesorId}", prof.id)) }
                        )
                    }
                }
            }
        }

        // FAB Menu Overlay
        if (isFabExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable { isFabExpanded = false },
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Column(
                        modifier = Modifier.padding(end = 20.dp, bottom = 100.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {

                        FabMenuItem(
                            text = "Book Class",
                            icon = Icons.Default.CalendarToday,
                            onClick = { 
                                isFabExpanded = false
                                val firstProfId = profesores.firstOrNull()?.id
                                if (firstProfId != null) {
                                    onNavigate("book_class/$firstProfId")
                                } else {
                                    android.widget.Toast.makeText(context, "No coaches available", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        if (isCurrentUserCoach) {
                            FabMenuItem(
                                text = "My Coach Dashboard",
                                icon = Icons.Default.Dashboard,
                                onClick = { 
                                    isFabExpanded = false
                                    val targetDash = Screen.CoachDashboard.route.replace("{profesorId}", userUid ?: "unknown")
                                    try {
                                        onNavigate(targetDash)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Nav Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                        } else {
                            FabMenuItem(
                                text = "Become a Coach",
                                icon = Icons.Default.PersonAdd,
                                onClick = { 
                                    isFabExpanded = false
                                    showBecomeCoachDialog = true 
                                }
                            )
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                contentColor = MaterialTheme.colorScheme.primary,
                backgroundColor = MaterialTheme.colorScheme.surface
            )

            // Standardized FAB
            FloatingActionButton(
                onClick = { isFabExpanded = !isFabExpanded },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp),
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                val rotation by animateFloatAsState(targetValue = if (isFabExpanded) 135f else 0f, label = "fabScale")
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Menu",
                    modifier = Modifier.rotate(rotation),
                    tint = Color.White
                )
            }
        }





    // Become Coach Modal
    if (showBecomeCoachDialog) {
        var userEmail by remember { mutableStateOf("") }
        var userName by remember { mutableStateOf("") }
        var userUid by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            authViewModel.getUser(
                onSuccess = { user -> 
                    userEmail = user.email
                    userName = user.fullName
                    userUid = user.uid
                 },
                 onFailure = {}
            )
        }

        BecomeCoachDialog(
            onDismiss = { showBecomeCoachDialog = false },
            onSubmit = { deporte, precio, experiencia, whatsapp, especialidad ->
                val newCoach = ProfesorBuilder(id = userUid)
                    .setBasicInfo(
                        nombre = userName.takeIf { it.isNotBlank() } ?: userEmail,
                        deporte = deporte
                    )
                    .setProfessionalProfile(
                        precio = precio,
                        experiencia = experiencia,
                        especialidad = especialidad
                    )
                    .setContactInfo(whatsapp = whatsapp)
                    .buildNewUnverifiedCoach()

                profesoresViewModel.createProfesor(newCoach,
                    onSuccess = {
                        showBecomeCoachDialog = false
                    },
                    onFailure = { /* Handle failure */ }
                )
            }
        )
    }
}

@Composable
fun CoachCard(profesor: Profesor, onViewProfile: () -> Unit) {
    val context = LocalContext.current
    val openWhatsApp = {
        val url = "https://wa.me/${profesor.whatsapp.replace(Regex("\\D"), "")}?text=Hi ${profesor.nombre}, I'm interested in your classes!"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(verticalAlignment = Alignment.Top) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    com.uniandes.sport.ui.components.SportIconBox(
                        sport = profesor.deporte,
                        size = 64.dp
                    )
                    if (profesor.verified) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            tint = Color(0xFF3B82F6), // Blue 500
                            modifier = Modifier
                                .size(24.dp)
                                .offset(x = 6.dp, y = 6.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = profesor.nombre,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${profesor.deporte} • ${profesor.precio}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (profesor.totalReviews > 0) {
                            Row(
                                Modifier
                                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, contentDescription = "Star", tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(text = String.format(Locale.US, "%.1f", profesor.rating), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        } else {
                            Row(
                                Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "New", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "${profesor.totalReviews} reviews", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Info Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(14.dp)
            ) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalance, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("${profesor.experiencia} exp.", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFF97316), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("#${profesor.rankInSport} in ${profesor.deporte}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    Text(text = "Specialty: ${profesor.especialidad}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onViewProfile,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("View Profile", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = openWhatsApp,
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}



@Composable
fun BecomeCoachDialog(onDismiss: () -> Unit, onSubmit: (String, String, String, String, String) -> Unit) {
    var deporte by remember { mutableStateOf("Soccer") }
    var precio by remember { mutableStateOf("") }
    var experiencia by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    var especialidad by remember { mutableStateOf("") }

    val deportes = listOf("Soccer", "Tennis", "Basketball", "Swimming", "Running")
    var expanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
        ) {
            LazyColumn(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("BECOME A COACH", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                }
                
                // Sport Dropdown
                item {
                    Text("Sport", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { expanded = true }.padding(16.dp)
                    ) {
                        Text(deporte)
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            deportes.forEach { option ->
                                DropdownMenuItem(text = { Text(option) }, onClick = { deporte = option; expanded = false })
                            }
                        }
                    }
                }

                // Price
                item {
                    Text("Price (e.g. $30/hour)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = precio, onValueChange = { precio = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                }

                // Experience
                item {
                     Text("Experience (e.g. 5 years)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                     OutlinedTextField(value = experiencia, onValueChange = { experiencia = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                }
                
                // Specialty
                item {
                     Text("Specialty (e.g. Tactics)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                     OutlinedTextField(value = especialidad, onValueChange = { especialidad = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                }

                // WhatsApp
                item {
                    Text("WhatsApp (include country code)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = whatsapp, onValueChange = { whatsapp = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Text("Used by students to contact you.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top=4.dp))
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { onSubmit(deporte, precio, experiencia, whatsapp, especialidad) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Register")
                        }
                    }
                }
            }
        }
    }
}
