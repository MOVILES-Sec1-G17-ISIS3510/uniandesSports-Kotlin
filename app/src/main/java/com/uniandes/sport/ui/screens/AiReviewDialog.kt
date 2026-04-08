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
    trackText: String,
    eventId: String,
    viewModel: AiReviewViewModel,
    oldAnalysis: Map<String, Double> = emptyMap(),
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState

    LaunchedEffect(trackText) {
        if (uiState is AiReviewState.Idle && trackText.isNotBlank()) {
            viewModel.analyzeTrack(trackText, eventId, oldAnalysis)
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
                // Header
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
                        text = "Track Analysis",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = {
                        if (uiState !is AiReviewState.Loading) {
                            viewModel.resetState()
                            onDismiss()
                        }
                    }) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                when (val state = uiState) {
                    is AiReviewState.Idle -> {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    is AiReviewState.Loading -> {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Analyzing your progress...",
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
                            Text("Got it!")
                        }
                    }
                    is AiReviewState.Error -> {
                        Text(
                            text = "AI analysis failed: ${state.error}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.analyzeTrack(trackText, eventId, oldAnalysis) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Try Again")
                        }
                    }
                    is AiReviewState.PoseFeedback -> {
                        // This dialog handles track text, pose feedback is handled in PoseAnalysisDialog.
                        // We add this to make the 'when' exhaustive.
                    }
                }
            }
        }
    }
}
