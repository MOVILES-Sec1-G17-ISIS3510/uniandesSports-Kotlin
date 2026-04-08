package com.uniandes.sport.ai

import com.uniandes.sport.models.Reto

/**
 * Patrón Strategy para la Inteligencia Artificial.
 * Permite intercambiar fácilmente entre Gemini, OpenAI, o un Mock para Testing,
 * sin afectar el ViewModel o el resto de la App.
 */
interface AiAnalyzerStrategy {
    /**
     * Analiza una review (texto) y un listado de retos activos.
     * @return AiReviewAnalysisResult que contiene el mapeo de retoId -> porcentajeAvanzado (double 0.0 a 1.0 o etc)
     */
    suspend fun analyzeReview(reviewText: String, activeChallenges: List<Reto>): AiReviewAnalysisResult

    suspend fun analyzePose(base64Image: String): String?

    suspend fun analyzeRunSession(distance: Float, pace: String, elevation: Float, cadence: Int): String?
}

/**
 * Resultado final de nuestro análisis de IA.
 * progressByChallengeId contiene el mapeo de retoId -> porcentaje avanzado (0-100).
 */
data class AiReviewAnalysisResult(
    val success: Boolean,
    val progressByChallengeId: Map<String, Double> = emptyMap(),
    val errorMessage: String? = null
)
