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
import com.google.firebase.auth.FirebaseAuth
import com.uniandes.sport.MainActivity
import com.uniandes.sport.R
import com.uniandes.sport.ai.OpenAiAnalyzerStrategy
import com.uniandes.sport.data.local.PendingRunAiStore
import com.uniandes.sport.viewmodels.running.FirestoreRunningViewModel

class RunAiSyncWorker(
    context: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val firestoreViewModel = FirestoreRunningViewModel()
    private val aiStrategy = OpenAiAnalyzerStrategy()

    override suspend fun doWork(): Result {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w("RunAiSyncWorker", "No authenticated user. Retrying pending AI feedback later.")
            return Result.retry()
        }

        val pendingItems = PendingRunAiStore.getAll(applicationContext)
        if (pendingItems.isEmpty()) {
            Log.d("RunAiSyncWorker", "No pending run AI requests to sync.")
            return Result.success()
        }

        var allSuccessful = true

        for (pending in pendingItems) {
            try {
                val feedback = aiStrategy.analyzeRunSession(
                    pending.distanceKm,
                    pending.pace,
                    pending.elevationGain,
                    pending.cadence
                )

                if (feedback.isNullOrBlank()) {
                    allSuccessful = false
                    Log.e("RunAiSyncWorker", "AI feedback generation returned empty for pending run: ${pending.runId}")
                    continue
                }

                firestoreViewModel.updateRunFeedback(
                    runId = pending.runId,
                    userId = pending.userId,
                    aiFeedback = feedback
                )

                PendingRunAiStore.remove(applicationContext, pending.localId)
                notifyAiFeedbackReady()
                Log.d("RunAiSyncWorker", "Pending AI feedback synced successfully: ${pending.runId}")
            } catch (e: Exception) {
                allSuccessful = false
                Log.e("RunAiSyncWorker", "Failed to sync pending AI feedback: ${pending.runId}", e)
            }
        }

        return if (allSuccessful) Result.success() else Result.retry()
    }

    private fun notifyAiFeedbackReady() {
        createNotificationChannelIfNeeded()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                return
            }
        }

        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, RUN_AI_SYNC_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AI coach ready")
            .setContentText("Your running feedback has been generated and synced.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(RUN_AI_SYNC_CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            RUN_AI_SYNC_CHANNEL_ID,
            "Running AI Sync Notification",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications when queued running AI feedback is synchronized"
        }

        manager.createNotificationChannel(channel)
    }

    companion object {
        const val RUN_AI_SYNC_CHANNEL_ID = "run_ai_sync"
    }
}
