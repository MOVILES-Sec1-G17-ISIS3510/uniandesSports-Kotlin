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
import androidx.compose.material.icons.filled.*
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

@Composable
fun ProfesoresScreen(
    profesoresViewModel: ProfesoresViewModelInterface,
    authViewModel: FirebaseAuthViewModel = viewModel(),
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    var profesores by remember { mutableStateOf<List<Profesor>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedProfesor by remember { mutableStateOf<Profesor?>(null) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var showBecomeCoachDialog by remember { mutableStateOf(false) }
    var reviewsRefreshKey by remember { mutableStateOf(0) }
    var isFabExpanded by remember { mutableStateOf(false) }

    val deportes = listOf("All", "Soccer", "Tennis", "Basketball", "Swimming", "Running")

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        profesoresViewModel.fetchProfesores(
            onSuccess = { profesores = it },
            onFailure = { /* Handle error */ }
        )
    }

    val filteredProfesores = if (selectedFilter == "All") {
        profesores
    } else {
        profesores.filter { it.deporte == selectedFilter }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isFabExpanded = !isFabExpanded },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                val rotation by animateFloatAsState(targetValue = if (isFabExpanded) 135f else 0f, label = "fabScale")
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Menu",
                    modifier = Modifier.rotate(rotation)
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFFF9FAFB)) // Light gray background
            ) {
            // Sport Filter
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(deportes) { dep ->
                    val isSelected = selectedFilter == dep
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                        border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF3F4F6)) else null,
                        shadowElevation = if (isSelected) 4.dp else 1.dp,
                        modifier = Modifier.clickable { selectedFilter = dep }
                    ) {
                        Text(
                            text = if (dep == "All") "All Coaches" else dep,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = if (isSelected) Color.White else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
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
                            Text("No coaches found for this sport", color = Color.Gray)
                        }
                    }
                } else {
                    items(filteredProfesores) { prof ->
                        CoachCard(
                            profesor = prof,
                            onViewProfile = { selectedProfesor = prof }
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
                        .padding(innerPadding)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { isFabExpanded = false },
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Column(
                        modifier = Modifier.padding(end = 16.dp, bottom = 80.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FabMenuItem(
                            text = "Agendar Clase",
                            icon = Icons.Default.CalendarToday,
                            onClick = { 
                                isFabExpanded = false
                            }
                        )
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
    }

    // Coach Detail Modal
    selectedProfesor?.let { prof ->
        CoachDetailDialog(
            profesor = prof,
            profesoresViewModel = profesoresViewModel,
            refreshKey = reviewsRefreshKey,
            onDismiss = { selectedProfesor = null },
            onAddReview = { showReviewDialog = true }
        )
    }

    // Add Review Modal
    if (showReviewDialog && selectedProfesor != null) {
        val profId = selectedProfesor!!.id
        var userEmail by remember { mutableStateOf("Anonymous") }
        
        LaunchedEffect(Unit) {
            authViewModel.getUser(
                onSuccess = { user -> userEmail = user.fullName.takeIf { it.isNotBlank() } ?: user.email },
                onFailure = {}
            )
        }

        AddReviewDialog(
            profesor = selectedProfesor!!,
            onDismiss = { showReviewDialog = false },
            onSubmit = { rating, comment -> 
                val newReview = Review(
                    estudiante = userEmail,
                    rating = rating,
                    comentario = comment,
                    fecha = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date())
                )
                profesoresViewModel.addReview(profId, newReview, 
                    onSuccess = {
                        showReviewDialog = false
                        // Opcional: recargar profesores para mostrar el nuevo rating
                        profesoresViewModel.fetchProfesores({ 
                            profesores = it 
                            selectedProfesor = it.find { p -> p.id == profId }
                        }, {})
                        reviewsRefreshKey++
                    },
                    onFailure = { e -> 
                        android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
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
                        profesoresViewModel.fetchProfesores({ profesores = it }, {})
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(verticalAlignment = Alignment.Top) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profesor.nombre.split(" ").joinToString("") { it.take(1) },
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                    }
                    if (profesor.verified) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            tint = Color(0xFF3B82F6), // Blue 500
                            modifier = Modifier
                                .size(24.dp)
                                .offset(x = 6.dp, y = 6.dp)
                                .background(Color.White, CircleShape)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = profesor.nombre,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = Color.Black
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
                                    .background(Color(0xFFFEF3C7), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, contentDescription = "Star", tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(text = String.format(Locale.US, "%.1f", profesor.rating), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                            }
                        } else {
                            Row(
                                Modifier
                                    .background(Color(0xFFE5E7EB), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "New", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "${profesor.totalReviews} reviews", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Info Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF9FAFB))
                    .padding(14.dp)
            ) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalance, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("${profesor.experiencia} exp.", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFF97316), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("#${profesor.rankInSport} in ${profesor.deporte}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray)
                        }
                    }
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE5E7EB))
                    Text(text = "Specialty: ${profesor.especialidad}", fontSize = 12.sp, color = Color.Gray)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE6F4EA), contentColor = Color(0xFF25D366))
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun CoachDetailDialog(
    profesor: Profesor,
    profesoresViewModel: ProfesoresViewModelInterface,
    refreshKey: Int = 0,
    onDismiss: () -> Unit,
    onAddReview: () -> Unit
) {
    var reviews by remember { mutableStateOf<List<Review>>(emptyList()) }

    LaunchedEffect(profesor.id, refreshKey) {
        profesoresViewModel.fetchReviews(profesor.id,
            onSuccess = { reviews = it },
            onFailure = { /* Handle */ }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
        ) {
            LazyColumn(modifier = Modifier.padding(20.dp)) {
                // Header
                item {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(profesor.nombre, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            if (profesor.totalReviews > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                    Icon(Icons.Default.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                                    Text(" ${String.format(Locale.US, "%.1f", profesor.rating)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                    Text("New Coach", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 14.sp)
                                }
                            }
                            Text("${profesor.totalReviews} reviews", fontSize = 12.sp, color = Color.Gray)
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.background(Color(0xFFF3F4F6), CircleShape).size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Stats Grid
                item {
                    val stats = listOf(
                        "Sport" to profesor.deporte,
                        "Price" to profesor.precio,
                        "Experience" to profesor.experiencia,
                        "Availability" to profesor.disponibilidad
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBox(stats[0].first, stats[0].second, Modifier.weight(1f))
                            StatBox(stats[1].first, stats[1].second, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBox(stats[2].first, stats[2].second, Modifier.weight(1f))
                            StatBox(stats[3].first, stats[3].second, Modifier.weight(1f))
                        }
                        StatBox("Specialty", profesor.especialidad, Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Performance
                item {
                    Text(
                        text = "PERFORMANCE",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBox("Sessions", profesor.sessionsDelivered.toString(), Modifier.weight(1f), isNumber = true)
                        StatBox("Wins", profesor.tournamentWins.toString(), Modifier.weight(1f), isNumber = true)
                        StatBox("Rank", "#" + profesor.rankInSport.toString(), Modifier.weight(1f), isNumber = true)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Reviews
                item {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("REVIEWS", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onAddReview) {
                            Text("Add Review")
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        reviews.take(3).forEach { review ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF3F4F6))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(review.estudiante, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.Default.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(12.dp))
                                    }
                                    Text(review.fecha, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp))
                                    Text(review.comentario, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Actions
                item {
                    val context = LocalContext.current
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val url = "https://wa.me/${profesor.whatsapp.replace(Regex("\\D"), "")}"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            modifier = Modifier.weight(1f).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Contact")
                        }
                        Button(
                            onClick = { /* TODO Schedule */ },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Book Class")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatBox(label: String, value: String, modifier: Modifier = Modifier, isNumber: Boolean = false) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF3F4F6))
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = if (isNumber) Alignment.CenterHorizontally else Alignment.Start) {
            if (isNumber) {
                Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                Text(label.uppercase(), fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            } else {
                Text(label.uppercase(), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun AddReviewDialog(profesor: Profesor, onDismiss: () -> Unit, onSubmit: (Int, String) -> Unit) {
    var rating by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("ADD REVIEW", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                
                Text("Rating", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { i ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(if (i <= rating) Color(0xFFFEF3C7) else Color(0xFFF3F4F6), RoundedCornerShape(12.dp))
                                .clickable { rating = i },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Star, null, tint = if (i <= rating) Color(0xFFFBBF24) else Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                
                Text("Comment", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text("Share your experience...") },
                    modifier = Modifier.fillMaxWidth().height(100.dp).padding(top = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6), contentColor = Color.Black),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onSubmit(rating, comment) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Submit")
                    }
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
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
        ) {
            LazyColumn(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("BECOME A COACH", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                }
                
                // Sport Dropdown
                item {
                    Text("Sport", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3F4F6)).clickable { expanded = true }.padding(16.dp)
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
                    Text("Price (e.g. $30/hour)", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = precio, onValueChange = { precio = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                }

                // Experience
                item {
                     Text("Experience (e.g. 5 years)", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                     OutlinedTextField(value = experiencia, onValueChange = { experiencia = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                }
                
                // Specialty
                item {
                     Text("Specialty (e.g. Tactics)", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                     OutlinedTextField(value = especialidad, onValueChange = { especialidad = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                }

                // WhatsApp
                item {
                    Text("WhatsApp (include country code)", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = whatsapp, onValueChange = { whatsapp = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Text("Used by students to contact you.", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top=4.dp))
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6), contentColor = Color.Black),
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
