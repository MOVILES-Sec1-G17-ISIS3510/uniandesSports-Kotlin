package com.uniandes.sport.ai

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface AiReviewApi {
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun analyzeReviewWithGemini(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}
