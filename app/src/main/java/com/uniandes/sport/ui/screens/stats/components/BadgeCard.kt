package com.uniandes.sport.ui.screens.stats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniandes.sport.data.entities.BadgeEntity

/**
 * BadgeCard — Componente para mostrar un badge individual.
 * 
 * DOCUMENTACIÓN:
 * - Línea 27-45: Mostrar badge con icono, nombre y rarity color
 * - Tamaño: 80x80 dp (4 badges por fila en grid)
 * - Color por rarity: COMMON (gris), RARE (azul), EPIC (púrpura), LEGENDARY (oro)
 * 
 * FEATURE: Gamificación
 * Cada badge tiene un ícono (emoji), nombre y color según rarity.
 * Se muestra en grid en MyStatsScreen.
 * 
 * @author Juan Felipe Hernández
 * @since 26-may-2026
 */
@Composable
fun BadgeCard(badge: BadgeEntity) {
    Card(
        modifier = Modifier
            .size(80.dp)
            .padding(4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = getRarityColor(badge.rarity)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Línea 40: Icono del badge
            Text(
                text = badge.icon,
                style = TextStyle(fontSize = 28.sp)
            )
            
            // Línea 45: Nombre del badge
            Text(
                text = badge.name,
                style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = 2,
                color = Color.White
            )
        }
    }
}

/**
 * LockBadgeCard — Badge bloqueado (no desbloqueado aún).
 * 
 * DOCUMENTACIÓN:
 * - Línea 56-74: Mostrar slot vacío con candado
 * - Color gris oscuro para indicar no disponible
 * 
 * @author Juan Felipe Hernández
 */
@Composable
fun LockBadgeCard() {
    Card(
        modifier = Modifier
            .size(80.dp)
            .padding(4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF424242)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Locked",
                style = TextStyle(fontSize = 28.sp)
            )
        }
    }
}

/**
 * getRarityColor — Retorna color según rarity del badge.
 * 
 * DOCUMENTACIÓN:
 * - Línea 83-92: Mapeo rarity → Color
 * - COMMON: Gris
 * - RARE: Azul
 * - EPIC: Púrpura
 * - LEGENDARY: Naranja/Oro
 * 
 * @author Juan Felipe Hernández
 */
fun getRarityColor(rarity: String): Color = when (rarity) {
    \"COMMON\" -> Color(0xFFD3D3D3)      // Gris
    \"RARE\" -> Color(0xFF1E88E5)        // Azul
    \"EPIC\" -> Color(0xFF7B1FA2)        // Púrpura
    \"LEGENDARY\" -> Color(0xFFFFA500)   // Naranja
    else -> Color.Gray
}
