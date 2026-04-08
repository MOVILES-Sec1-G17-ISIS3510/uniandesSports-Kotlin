package com.uniandes.sport.ai

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface OpenAiReviewApi {
    @Headers("Content-Type: application/json")
    @POST("v1/chat/completions")
    suspend fun analyzeReviewWithOpenAi(
        @Header("Authorization") authHeader: String,
        @Body request: OpenAiRequest
    ): Response<OpenAiResponse>
}
