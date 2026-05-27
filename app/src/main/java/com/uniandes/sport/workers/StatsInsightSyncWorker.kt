package com.uniandes.sport.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uniandes.sport.MainActivity
import com.uniandes.sport.R
import com.uniandes.sport.ai.AiConstants
import com.uniandes.sport.ai.OpenAiMessage
import com.uniandes.sport.ai.OpenAiRequest
import com.uniandes.sport.ai.OpenAiReviewApi
import com.uniandes.sport.data.local.PendingStatsInsightStore
import com.uniandes.sport.data.local.StatsInsightResultStore
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that runs when connectivity is restored.
 * Reads the queued AI insight request, calls OpenAI gpt-4o-mini,
 * saves the result to [StatsInsightResultStore], and shows a local
 * notification that deep-links back to MyStats.
 */
class StatsInsightSyncWorker(
    context: Context,
    params:  WorkerParameters
) : CoroutineWorker(context, params) {

    private val api: OpenAiReviewApi by lazy {
        Retrofit.Builder()
            .baseUrl(AiConstants.OPENAI_BASE_URL)
            .client(
                okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAiReviewApi::class.java)
    }

    override suspend fun doWork(): Result {
        val pending = PendingStatsInsightStore.getAll(applicationContext)
        if (pending.isEmpty()) {
            Log.d(TAG, "No pending insight requests.")
            return Result.success()
        }

        val item = pending.first()
        Log.d(TAG, "Processing insight for userId=${item.userId}")

        return try {
            val response = api.analyzeReviewWithOpenAi(
                authHeader = "Bearer ${AiConstants.OPENAI_API_KEY}",
                request    = OpenAiRequest(
                    model    = "gpt-4o-mini",
                    messages = listOf(
                        OpenAiMessage(
                            role    = "system",
                            content = "You are an expert, motivational sports coach providing personalized progress feedback."
                        ),
                        OpenAiMessage(role = "user", content = item.promptText)
                    ),
                    maxTokens = 500
                )
            )

            val text = response.body()?.choices?.firstOrNull()?.message?.content?.toString()?.trim()
            if (text.isNullOrBlank()) {
                Log.e(TAG, "Empty response from OpenAI — retrying.")
                return Result.retry()
            }

            StatsInsightResultStore.save(applicationContext, text, System.currentTimeMillis())
            PendingStatsInsightStore.remove(applicationContext, item.localId)
            showNotification()
            Log.d(TAG, "Insight saved and notification sent.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI call failed", e)
            Result.retry()
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun showNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        createChannelIfNeeded()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_NOTIFICATION_TYPE, NOTIFICATION_TYPE)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Your AI coach has feedback 🏆")
            .setContentText("Tap to see how you're doing and how to improve.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Your personalized sports progress report is ready! Tap to read your AI coach's analysis.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "AI Coach Insights",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when your queued AI stats insight is ready"
            }
        )
    }

    companion object {
        private const val TAG              = "StatsInsightSyncWorker"
        const val CHANNEL_ID               = "stats_insight_sync"
        const val NOTIFICATION_TYPE        = "stats_insight"
    }
}
