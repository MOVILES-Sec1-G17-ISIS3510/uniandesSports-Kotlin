package com.uniandes.sport.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.uniandes.sport.viewmodels.retos.AiReviewState
import com.uniandes.sport.viewmodels.retos.AiReviewViewModel

@Composable
fun AiReviewDialog(
    reviewText: String,
    eventId: String,
    viewModel: AiReviewViewModel,
    oldAnalysis: Map<String, Double> = emptyMap(),
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState

    LaunchedEffect(reviewText) {
        if (uiState is AiReviewState.Idle && reviewText.isNotBlank()) {
            viewModel.analyzeReview(reviewText, eventId, oldAnalysis)
        }
    }

    Dialog(onDismissRequest = { 
        if (uiState !is AiReviewState.Loading) {
            viewModel.resetState()
            onDismiss()
        }
    }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Cabecera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Reseña de Actividad",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = {
                        if (uiState !is AiReviewState.Loading) {
                            viewModel.resetState()
                            onDismiss()
                        }
                    }) {
                        Icon(Icons.Default.Close, "Cerrar")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                when (val state = uiState) {
                    is AiReviewState.Idle -> {
                        // Oculto mientras se dispara el LaunchedEffect
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    is AiReviewState.Loading -> {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Analizando tu esfuerzo...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is AiReviewState.Success -> {
                        val color = if (state.advancedChallengesCount > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary
                        
                        Box(
                            modifier = Modifier
                                .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = color,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = { 
                                viewModel.resetState()
                                onDismiss() 
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Aceptar")
                        }
                    }
                    is AiReviewState.Error -> {
                        Text(
                            text = "Hubo un error al contactar la IA: ${state.error}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.resetState() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Reintentar")
                        }
                    }
                }
            }
        }
    }
}
