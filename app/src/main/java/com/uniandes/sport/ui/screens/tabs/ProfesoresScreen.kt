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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
import com.uniandes.sport.ui.components.OfflineConnectivityBanner
import com.uniandes.sport.ui.components.rememberIsOnline
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.uniandes.sport.models.InsightType
import com.uniandes.sport.models.CoachInsight

@OptIn(ExperimentalMaterialApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfesoresScreen(
    profesoresViewModel: ProfesoresViewModelInterface,
    authViewModel: FirebaseAuthViewModel = viewModel(),
    bookClassViewModel: com.uniandes.sport.viewmodels.booking.BookClassViewModel = viewModel(),
    weatherViewModel: com.uniandes.sport.viewmodels.weather.WeatherViewModel = viewModel(),
    logViewModel: com.uniandes.sport.viewmodels.log.LogViewModelInterface = viewModel<com.uniandes.sport.viewmodels.log.FirebaseLogViewModel>(),
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val profesores by profesoresViewModel.profesores.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    var searchText by remember { mutableStateOf("") }
    var showBecomeCoachDialog by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }

    val smartInsights by bookClassViewModel.smartCoachInsights.collectAsState()
    val userBookings by bookClassViewModel.userBookings.collectAsState()

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

    // EVC: Eventual Connectivity — estado reactivo de red para esta vista
    val isOnline = rememberIsOnline()

    LaunchedEffect(Unit) {
        profesoresViewModel.fetchProfesores()
        authViewModel.getUser(
            onSuccess = { user -> 
                userUid = user.uid 
                if (userUid != null) {
                    bookClassViewModel.fetchUserBookings(userUid!!)
                }
            },
            onFailure = { /* Not logged in or error */ }
        )
    }

    // Context-Aware Rubric: Sync weather code with BookClassViewModel
    val weatherState by weatherViewModel.weatherState.collectAsState()
    LaunchedEffect(weatherState) {
        if (weatherState is com.uniandes.sport.viewmodels.weather.WeatherState.Success) {
            val code = (weatherState as com.uniandes.sport.viewmodels.weather.WeatherState.Success).data.currentWeather.weatherCode
            bookClassViewModel.updateWeatherContext(code)
        }
    }

    // Connect Coaches context for personalized recommendation
    LaunchedEffect(profesores) {
        if (profesores.isNotEmpty()) {
            bookClassViewModel.updateCoachesContext(profesores)
        }
    }

    // Bug fix #4: guard against timing race where userUid is still null when profesores loads from cache
    val isCurrentUserCoach = userUid != null && profesores.any { it.id == userUid }

    val filteredProfesores = remember(profesores, selectedFilter, searchText) {
        profesores.filter { prof ->
            val matchesFilter = selectedFilter == "All" || prof.deporte == selectedFilter
            val matchesSearch = searchText.isBlank() || 
                                prof.nombre.contains(searchText, ignoreCase = true) || 
                                prof.deporte.contains(searchText, ignoreCase = true) ||
                                prof.especialidad.contains(searchText, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    // Effect to log search demand when the user stops typing (debounced log)
    LaunchedEffect(searchText) {
        if (searchText.length >= 3) {
            delay(1000) // 1 second debounce
            logViewModel.log(
                screen = "ProfesoresScreen",
                action = "SEARCH_PERFORMED",
                params = mapOf(
                    "query" to searchText,
                    "results_found" to filteredProfesores.size.toString(),
                    "category_filter" to selectedFilter
                )
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Bar (Demand Identification) — funciona offline para filtrar caché local
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(
                    if (isOnline) "Search sport or specialty (e.g. Yoga, Padel)"
                    else "Searching cached coaches...",
                    fontSize = 14.sp
                ) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            // EVC: Banner de conectividad específico para la vista de Profesores
            OfflineConnectivityBanner(
                offlineMessage = "Showing ${if (filteredProfesores.isNotEmpty()) filteredProfesores.size.toString() + " coaches" else "coaches"} from cache. Booking disabled."
            )

            // Sport Filter
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(deportes) { dep ->
                    val isSelected = selectedFilter == dep
                    
                    FilterChip(
                        selected = isSelected,
                        onClick = { 
                            selectedFilter = dep 
                            logViewModel.log(
                                screen = "ProfesoresScreen",
                                action = "FILTER_SELECTED",
                                params = mapOf("sport" to dep)
                            )
                        },
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
                // Smart Insight Carousel
                item {
                    if (smartInsights.isNotEmpty()) {
                        val pagerState = rememberPagerState(pageCount = { smartInsights.size })
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                pageSpacing = 12.dp
                            ) { page ->
                                SmartInsightCard(insight = smartInsights[page])
                            }
                            
                            if (smartInsights.size > 1) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    repeat(smartInsights.size) { iteration ->
                                        val color = if (pagerState.currentPage == iteration) 
                                            MaterialTheme.colorScheme.primary 
                                        else 
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // YOUR REQUESTS SECTION (G17 Rubric: User History)
                if (userBookings.isNotEmpty()) {
                    item {
                        Text(
                            "YOUR RECENT REQUESTS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(userBookings.sortedByDescending { it.createdAt }) { booking ->
                                BookingHistoryCard(booking = booking, allCoaches = profesores)
                            }
                        }
                        Divider(modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }

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
                                // EVC: La cola local maneja las solicitudes offline
                                // Pass 'broadcast' instead of a specific ID when using the main FAB
                                onNavigate("book_class/broadcast")
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
        // Bug fix #5: renamed from 'userUid' to avoid shadowing the outer-scope variable.
        // The outer userUid may be null; this local one is always a String and is set async.
        var dialogUserUid by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            authViewModel.getUser(
                onSuccess = { user -> 
                    userEmail = user.email
                    userName = user.fullName
                    dialogUserUid = user.uid
                 },
                 onFailure = {}
            )
        }

        BecomeCoachDialog(
            onDismiss = { showBecomeCoachDialog = false },
            onSubmit = { deporte, precio, experiencia, whatsapp, especialidad ->
                val newCoach = ProfesorBuilder(id = dialogUserUid)
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
                        profesoresViewModel.refreshProfesores {} // Bug fix #8: refresh so new coach appears immediately
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
        val cleanNumber = profesor.whatsapp.replace(Regex("\\D"), "")
        // Bug fix #7: guard against blank number and missing WhatsApp app
        if (cleanNumber.isBlank()) {
            android.widget.Toast.makeText(context, "No contact number available", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            val url = "https://wa.me/$cleanNumber?text=Hi ${profesor.nombre}, I'm interested in your classes!"
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: android.content.ActivityNotFoundException) {
                android.widget.Toast.makeText(context, "WhatsApp is not installed", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
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
                    val displayPrice = profesor.precio
                        .replace("$", "")
                        .replace("/hour", "")
                        .replace("COP", "")
                        .trim()
                        
                    Text(
                        text = profesor.nombre,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${profesor.deporte} • $${displayPrice}/hour",
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
                        val displayExp = profesor.experiencia
                            .replace("years", "")
                            .replace("year", "")
                            .replace("exp.", "")
                            .trim()

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalance, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("$displayExp years exp.", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
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

    // Validation States
    val isPriceValid = precio.isNotEmpty() && precio.all { it.isDigit() }
    val isExperienceValid = experiencia.isNotEmpty() && experiencia.all { it.isDigit() }
    val isWhatsAppValid = whatsapp.length == 10 && whatsapp.all { it.isDigit() }
    val isSpecialtyValid = especialidad.length >= 10
    
    val isFormValid = isPriceValid && isExperienceValid && isWhatsAppValid && isSpecialtyValid

    val deportes = listOf("Soccer", "Tennis", "Basketball", "Swimming", "Running")
    var expanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp)
        ) {
            LazyColumn(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Text("BECOME A COACH", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Fill in your professional details to start coaching.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                // Sport Dropdown
                item {
                    Text("Sport Category", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { expanded = true }
                            .padding(16.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(deporte, fontWeight = FontWeight.Medium)
                            Icon(Icons.Default.KeyboardArrowDown, null)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            deportes.forEach { option ->
                                DropdownMenuItem(text = { Text(option) }, onClick = { deporte = option; expanded = false })
                            }
                        }
                    }
                }

                // Price
                item {
                    Text("Hourly Price (USD)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = precio, 
                        onValueChange = { if (it.all { char -> char.isDigit() }) precio = it }, 
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. 15") },
                        prefix = { Text("$ ") },
                        isError = precio.isNotEmpty() && !isPriceValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Experience
                item {
                     Text("Years of Experience", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                     OutlinedTextField(
                        value = experiencia, 
                        onValueChange = { if (it.all { char -> char.isDigit() }) experiencia = it }, 
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. 3") },
                        suffix = { Text("years") },
                        isError = experiencia.isNotEmpty() && !isExperienceValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                     )
                }
                
                // Specialty
                item {
                     Text("Professional Specialty", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                     OutlinedTextField(
                        value = especialidad, 
                        onValueChange = { especialidad = it }, 
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Describe your main focus...") },
                        supportingText = { 
                            if (!isSpecialtyValid && especialidad.isNotEmpty()) {
                                Text("Min 10 characters needed", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        isError = especialidad.isNotEmpty() && !isSpecialtyValid,
                        shape = RoundedCornerShape(12.dp)
                     )
                }

                // WhatsApp
                item {
                    Text("WhatsApp Contact", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = whatsapp, 
                        onValueChange = { if (it.length <= 10 && it.all { char -> char.isDigit() }) whatsapp = it }, 
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("3001234567") },
                        leadingIcon = { Icon(Icons.Default.Call, null, Modifier.size(20.dp)) },
                        supportingText = {
                            if (whatsapp.isNotEmpty() && !isWhatsAppValid) {
                                Text("Exactly 10 digits required", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        isError = whatsapp.isNotEmpty() && !isWhatsAppValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(54.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { onSubmit(deporte, precio, experiencia, whatsapp, especialidad) },
                            enabled = isFormValid,
                            modifier = Modifier.weight(1f).height(54.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text("Register")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmartInsightCard(insight: CoachInsight) {
    val isWeather = insight.type == InsightType.ENVIRONMENTAL
    val isHabit = insight.type == InsightType.BEHAVIORAL
    
    val icon = when {
        insight.icon != null -> insight.icon
        insight.message.contains("raining", ignoreCase = true) -> Icons.Default.BeachAccess
        insight.message.contains("Perfect day", ignoreCase = true) -> Icons.Default.WbSunny
        insight.message.contains("cloudy", ignoreCase = true) -> Icons.Default.Cloud
        isHabit -> Icons.Default.AutoAwesome
        else -> Icons.Default.Lightbulb
    }

    val label = when (insight.type) {
        InsightType.ENVIRONMENTAL -> "ENVIRONMENTAL CONTEXT"
        InsightType.BEHAVIORAL -> "SPORT HABITS"
        InsightType.WELCOME -> "SMART COACH"
    }

    val iconColor = when (insight.type) {
        InsightType.ENVIRONMENTAL -> MaterialTheme.colorScheme.primary
        InsightType.BEHAVIORAL -> MaterialTheme.colorScheme.tertiary
        InsightType.WELCOME -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = iconColor.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, iconColor.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = "Context Icon",
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    color = iconColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = insight.message,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun BookingHistoryCard(
    booking: com.uniandes.sport.models.BookingRequest,
    allCoaches: List<com.uniandes.sport.models.Profesor> = emptyList()
) {
    val context = LocalContext.current
    val statusColor = when(booking.status.lowercase()) {
        "pending" -> Color(0xFFF59E0B) // Amber
        "accepted" -> Color(0xFF10B981) // Emerald
        "completed" -> Color(0xFF6B7280) // Gray
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier.width(240.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when(booking.sport.lowercase()) {
                                "tennis" -> Icons.Default.SportsTennis
                                "soccer" -> Icons.Default.SportsSoccer
                                else -> Icons.Default.FitnessCenter
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val resolvedCoachName = remember(booking, allCoaches) {
                        when {
                            booking.targetProfesorName.isNotBlank() -> booking.targetProfesorName
                            booking.targetProfesorId.isNotBlank() -> {
                                allCoaches.find { it.id == booking.targetProfesorId }?.nombre 
                                    ?: "Coach Assigned"
                            }
                            else -> "Broadcast Content"
                        }
                    }
                    Text(
                        text = when {
                            booking.targetProfesorName.isNotBlank() -> "COACH: ${booking.targetProfesorName.uppercase()}"
                            booking.targetProfesorId.isNotBlank() -> "COACH ASSIGNED"
                            else -> "SEARCHING FOR COACH..."
                        },
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        color = if (booking.targetProfesorId.isBlank()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                    )
                    if (booking.targetProfesorId.isBlank()) {
                        Text(
                            text = "Broadcast active for ${booking.sport}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Student: ${booking.studentName}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (booking.targetProfesorId.isBlank()) "Wait for a coach to accept your request" else "Sport: ${booking.sport}",
                        fontSize = 10.sp, 
                        lineHeight = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = booking.status.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = statusColor,
                        letterSpacing = 0.5.sp
                    )
                }
                
                Text(
                    // Bug fix #6: guard against null/missing createdAt field in older documents
                    text = try {
                        SimpleDateFormat("MMM d", Locale.getDefault()).format(booking.createdAt.toDate())
                    } catch (e: Exception) { "—" },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (booking.status.lowercase() == "accepted") {
                val assignedCoach = allCoaches.find { it.id == booking.targetProfesorId }
                if (assignedCoach != null) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val cleanNumber = assignedCoach.whatsapp.replace(Regex("\\D"), "")
                            // Bug fix #7: guard blank number + missing WhatsApp app
                            if (cleanNumber.isBlank()) {
                                android.widget.Toast.makeText(context, "No contact number available", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                try {
                                    val url = "https://wa.me/$cleanNumber?text=Hi coach ${assignedCoach.nombre}, you accepted my ${booking.sport} request on UniandesSports!"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (e: android.content.ActivityNotFoundException) {
                                    android.widget.Toast.makeText(context, "WhatsApp is not installed", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Default.Call, null, Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("WHATSAPP COACH", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
            }
        }
    }
}

