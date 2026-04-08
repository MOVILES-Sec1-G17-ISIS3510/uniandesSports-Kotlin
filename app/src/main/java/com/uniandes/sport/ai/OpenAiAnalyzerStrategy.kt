package com.uniandes.sport.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uniandes.sport.models.Reto
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class OpenAiAnalyzerStrategy : AiAnalyzerStrategy {

    private val api: OpenAiReviewApi by lazy {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        Retrofit.Builder()
            .baseUrl(AiConstants.OPENAI_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAiReviewApi::class.java)
    }

    override suspend fun analyzeReview(
        trackText: String,
        activeChallenges: List<Reto>
    ): AiReviewAnalysisResult {
        try {
            if (activeChallenges.isEmpty()) {
                return AiReviewAnalysisResult(success = true, progressByChallengeId = emptyMap(), errorMessage = null)
            }

            // Construimos el prompt igual que con Gemini
            val prompt = buildPrompt(trackText, activeChallenges)

            // Petición de OpenAI (usando gpt-4o-mini o gpt-3.5-turbo)
            val request = OpenAiRequest(
                model = "gpt-4o-mini",
                messages = listOf(
                    OpenAiMessage(role = "system", content = "You are a helpful AI assistant that analyzes sports activities and outputs JSON strictly."),
                    OpenAiMessage(role = "user", content = prompt)
                ),
                responseFormat = OpenAiResponseFormat(type = "json_object")
            )

            val authHeader = "Bearer ${AiConstants.OPENAI_API_KEY}"
            val response = api.analyzeReviewWithOpenAi(authHeader, request)

            if (response.isSuccessful) {
                val openAiResponse = response.body()
                
                // Extraer el String JSON de la respuesta de OpeanAI
                val jsonText = openAiResponse?.choices?.firstOrNull()?.message?.content?.toString() ?: "{}"
                Log.d("OpenAiStrategy", "Raw JSON string from AI: $jsonText")

                // Parsear el JSON
                val type = object : TypeToken<Map<String, Double>>() {}.type
                val progressMap: Map<String, Double> = Gson().fromJson(jsonText, type)

                return AiReviewAnalysisResult(
                    success = true,
                    progressByChallengeId = progressMap
                )
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("OpenAiStrategy", "API Error: $errorBody")
                return AiReviewAnalysisResult(success = false, errorMessage = "Error ${response.code()}: No autorizado o cuota excedida. Revisa tu API Key de OpenAI.")
            }
        } catch (e: Exception) {
            Log.e("OpenAiStrategy", "Exception in API", e)
            return AiReviewAnalysisResult(success = false, errorMessage = e.message)
        }
    }

    override suspend fun analyzePose(base64Image: String): String? {
        try {
            val request = OpenAiRequest(
                model = "gpt-4o",
                messages = listOf(
                    OpenAiMessage(
                        role = "user",
                        content = listOf(
                            OpenAiContent(type = "text", text = "You are a professional Calisthenics Coach. Analyze this image immediately and provide direct technical feedback on the athlete's form. Focus on the specific biomechanical details visible in the photo. Highlight key strengths and provide actionable points for improvement. DO NOT use introductory phrases like \"Certainly\" or \"In this image\". Start directly with the coaching feedback. All feedback must be in ENGLISH."),
                            OpenAiContent(
                                type = "image_url",
                                imageUrl = OpenAiImageUrl(
                                    url = "data:image/jpeg;base64,$base64Image",
                                    detail = "low"
                                )
                            )
                        )
                    )
                ),
                maxTokens = 500,
                responseFormat = null
            )

            val authHeader = "Bearer ${AiConstants.OPENAI_API_KEY}"
            val response = api.analyzeReviewWithOpenAi(authHeader, request)

            return if (response.isSuccessful) {
                response.body()?.choices?.firstOrNull()?.message?.content?.toString()
            } else {
                val error = response.errorBody()?.string()
                Log.e("OpenAiStrategy", "Pose Analysis Error: $error")
                "Error al analizar la pose. Revisa tu conexión o créditos de OpenAI."
            }
        } catch (e: Exception) {
            Log.e("OpenAiStrategy", "Exception in analyzePose", e)
            return "Error: ${e.message}"
        }
    }

    override suspend fun analyzeRunSession(
        distance: Float,
        pace: String,
        elevation: Float,
        cadence: Int
    ): String? {
        try {
            val prompt = """
                You are a professional, highly MOTIVATIONAL running coach. 
                Analyze the following data from a user's recent run session:
                - Distance: ${String.format("%.2f", distance)} km
                - Pace: $pace min/km
                - Elevation Gain: ${String.format("%.1f", elevation)} m
                - Cadence: $cadence steps per min (SPM)
                
                Provide a short, direct, and VERY MOTIVATIONAL analysis of their performance. 
                Focus on high energy and encouraging feedback. 
                Keep it under 3 short paragraphs.
                Use bullet points for 2-3 specific tips to improve next time.
                The response must be in ENGLISH.
                DO NOT start with "Certainly" or "Great job". Start with the coaching analysis directly.
            """.trimIndent()

            val request = OpenAiRequest(
                model = "gpt-4o-mini",
                messages = listOf(
                    OpenAiMessage(role = "system", content = "You are an elite, energetic, and highly motivational athletic coach."),
                    OpenAiMessage(role = "user", content = prompt)
                ),
                maxTokens = 400
            )

            val authHeader = "Bearer ${AiConstants.OPENAI_API_KEY}"
            val response = api.analyzeReviewWithOpenAi(authHeader, request)

            return if (response.isSuccessful) {
                response.body()?.choices?.firstOrNull()?.message?.content?.toString()
            } else {
                Log.e("OpenAiStrategy", "Run Session Error: ${response.errorBody()?.string()}")
                "You crushed it today! Keep pushing your limits and stay consistent. Your progress is amazing!"
            }
        } catch (e: Exception) {
            Log.e("OpenAiStrategy", "Exception in analyzeRunSession", e)
            return "Great workout! Keep at it and you'll see massive gains soon!"
        }
    }

    private fun buildPrompt(trackText: String, challenges: List<Reto>): String {
        val challengesDescriptions = challenges.joinToString(separator = "\n") {
            "- ID: ${it.id}, Sport: ${it.sport}, Title: ${it.title}, Goal: ${it.goalLabel}"
        }

        return """
            The user has just completed a sports event and provided the following activity log: 
            "$trackText"
            
            The user has the following active challenges:
            $challengesDescriptions
            
            Analyze the activity log and determine how much NEW progress percentage (0 to 100) the user made towards each challenge IN THIS SPECIFIC SESSION.
            
            CRITICAL INSTRUCTIONS:
            1. Look for numeric values in the text (e.g., "30 pushups", "5km", "10 reps").
            2. Match these values against the 'Goal' (goalLabel) of each challenge.
            3. Calculate the percentage: (Amount from Activity log) / (Total Goal) * 100.
            4. If the goal is "1000 reps" and the text says "300 reps", the progress is 30.
            5. If the goal is "10 km" and the text says "1 km", the progress is 10.
            6. If the text doesn't mention a specific number but mentions the activity, return a very small reasonable increment (e.g., 1) or 0.
            7. Return 0 if the activity doesn't match the challenge's sport or title.
            
            RETURN ONLY A VALID JSON OBJECT where the keys are the challenge IDs and the values are numbers (0 to 100) representing the INCREMENTAL percentage made.
            Do not wrap in markdown tags. Do not add explanations.
            Example: {"ch_1": 30, "ch_2": 5}
        """.trimIndent()
    }
}
