package com.uniandes.sport.ui.screens.sport_tools

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.ui.components.OfflineConnectivityBanner
import com.uniandes.sport.ui.theme.ArchivoFamily

// vista principal de sport tools: 4 cards pastel (calisthenics, distance tracker,
// warm-up, ai nutrition). solo warm-up es funcional en esta fase; las otras
// muestran un toast "coming soon" como en flutter
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportToolsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToWarmup: () -> Unit,
    onNavigateToNutrition: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Sport Tools",
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
            // banner offline: warm-up sigue funcional con cache; las demas
            // herramientas (calisthenics, distance, nutrition) muestran su propio aviso
            OfflineConnectivityBanner(
                offlineMessage = "You can still open Warm-Up Routines with cached data."
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            ToolCard(
                title = "AI Calisthenics Assistant",
                icon = Icons.Default.FitnessCenter,
                pastel = Color(0xFFFDE6E8),
                accent = Color(0xFFAA4452),
                onClick = {
                    Toast.makeText(context, "AI Calisthenics — coming soon", Toast.LENGTH_SHORT).show()
                }
            )
            ToolCard(
                title = "Distance Tracker",
                icon = Icons.Default.Map,
                pastel = Color(0xFFE2F1DC),
                accent = Color(0xFF4F7A40),
                onClick = {
                    Toast.makeText(context, "Distance Tracker — coming soon", Toast.LENGTH_SHORT).show()
                }
            )
            ToolCard(
                title = "Warm-Up Routines",
                icon = Icons.Default.SelfImprovement,
                pastel = Color(0xFFDAE3F0),
                accent = Color(0xFF1F3C66),
                onClick = onNavigateToWarmup
            )
            ToolCard(
                title = "AI Nutrition Assistant",
                icon = Icons.Default.Restaurant,
                pastel = Color(0xFFFAF6D6),
                accent = Color(0xFF77692E),
                onClick = onNavigateToNutrition
            )
            }
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    icon: ImageVector,
    pastel: Color,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(pastel)
            .border(1.5.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = accent
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = accent)
    }
}
