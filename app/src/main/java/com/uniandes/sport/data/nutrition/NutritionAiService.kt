package com.uniandes.sport.data.nutrition

import android.content.Context
import android.util.Log
import com.uniandes.sport.ai.AiConstants
import com.uniandes.sport.ai.OpenAiMessage
import com.uniandes.sport.ai.OpenAiRequest
import com.uniandes.sport.ai.OpenAiReviewApi
import com.uniandes.sport.data.local.NutritionPlanFileStorage
import com.uniandes.sport.models.nutrition.NutritionPlan
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// servicio que genera planes de nutricion con ia y los persiste localmente.
// reusa el cliente retrofit/openai del resto del app (patron strategy)
// para mantener consistencia con poseanalysis, trackanalysis y runanalysis.
//
// el plan se guarda en filesystem (single-entry) como espejo del hive box
// que usa flutter — permite que la pantalla muestre el ultimo plan incluso
// sin internet (vista protegida)
object NutritionAiService {

    private val api: OpenAiReviewApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        Retrofit.Builder()
            .baseUrl(AiConstants.OPENAI_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAiReviewApi::class.java)
    }

    // genera un plan a partir del perfil del usuario y lo guarda en local.
    // el caller debe estar en dispatchers.io porque hace red + disco
    suspend fun generateAndSavePlan(
        context: Context,
        age: Int,
        weightKg: Double,
        heightCm: Double,
        goal: String,
        dietaryRestrictions: String
    ): Result<NutritionPlan> {
        if (AiConstants.OPENAI_API_KEY.isBlank()) {
            return Result.failure(IllegalStateException("OPENAI_API_KEY is not configured"))
        }

        return try {
            val prompt = buildPrompt(age, weightKg, heightCm, goal, dietaryRestrictions)
            val request = OpenAiRequest(
                model = "gpt-4o-mini",
                messages = listOf(
                    OpenAiMessage(
                        role = "system",
                        content = "You are an expert sports nutritionist that produces clear, well-structured Markdown plans."
                    ),
                    OpenAiMessage(role = "user", content = prompt)
                ),
                maxTokens = 1200
            )

            val authHeader = "Bearer ${AiConstants.OPENAI_API_KEY}"
            val response = api.analyzeReviewWithOpenAi(authHeader, request)

            if (!response.isSuccessful) {
                val err = response.errorBody()?.string()
                Log.e("NutritionAiService", "OpenAI error: $err")
                return Result.failure(Exception("AI service unavailable. Check your connection or quota."))
            }

            val markdown = response.body()
                ?.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?.toString()
                ?.trim()
                ?: ""

            if (markdown.isBlank()) {
                return Result.failure(Exception("AI returned an empty plan, please try again."))
            }

            val plan = NutritionPlan(
                planContent = markdown,
                createdAtMillis = System.currentTimeMillis(),
                age = age,
                weightKg = weightKg,
                heightCm = heightCm,
                goal = goal,
                dietaryRestrictions = dietaryRestrictions
            )
            NutritionPlanFileStorage.savePlan(context, plan)
            Result.success(plan)
        } catch (e: Exception) {
            Log.e("NutritionAiService", "Exception while generating plan", e)
            Result.failure(e)
        }
    }

    // espejo del prompt usado en flutter, con instrucciones identicas para que la salida
    // sea comparable entre plataformas en el viva voce
    private fun buildPrompt(
        age: Int,
        weightKg: Double,
        heightCm: Double,
        goal: String,
        dietaryRestrictions: String
    ): String {
        val restrictions = if (dietaryRestrictions.isBlank()) "None" else dietaryRestrictions
        return """
            You are an expert sports nutritionist.
            Create a daily nutrition plan for a person with the following profile:
            - Age: $age
            - Weight: ${"%.1f".format(weightKg)} kg
            - Height: ${"%.1f".format(heightCm)} cm
            - Goal: $goal
            - Dietary restrictions/preferences: $restrictions

            IMPORTANT INSTRUCTIONS:
            - Present the information structured in clear Markdown format.
            - For EACH meal of the day (Breakfast, Lunch, Dinner, Snacks), provide food options.
            - INCLUDE nutritional information for these options.
            - EXPLAIN WHY you chose these specific foods based on their goal and profile.
            - Write everything in English.
        """.trimIndent()
    }
}
