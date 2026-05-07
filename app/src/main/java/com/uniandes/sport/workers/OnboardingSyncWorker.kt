package com.uniandes.sport.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.uniandes.sport.MainActivity
import com.uniandes.sport.R
import com.uniandes.sport.data.local.PendingOnboardingStore
import com.uniandes.sport.models.User
import kotlinx.coroutines.tasks.await

class OnboardingSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override suspend fun doWork(): Result {
        val pending = PendingOnboardingStore.get(applicationContext) ?: return Result.success()

        return try {
            val currentUser = auth.currentUser ?: if (pending.password.isNotBlank()) {
                auth.createUserWithEmailAndPassword(pending.email, pending.password).await().user
            } else {
                null
            }

            if (currentUser == null) {
                return Result.retry()
            }

            val profile = User(
                uid = currentUser.uid,
                email = currentUser.email ?: pending.email,
                fullName = pending.fullName,
                program = pending.program,
                semester = pending.semester.toIntOrNull() ?: 0,
                mainSport = pending.mainSport,
                role = "athlete",
                createdAt = System.currentTimeMillis()
            )

            db.collection("users").document(currentUser.uid)
                .set(profile)
                .await()

            PendingOnboardingStore.clear(applicationContext)
            notifyOnboardingSynced(pending.fullName)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notifyOnboardingSynced(fullName: String) {
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
            fullName.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, ONBOARDING_SYNC_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Account created")
            .setContentText("Your account was created after the connection returned. You can sign in now.")
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
        if (manager.getNotificationChannel(ONBOARDING_SYNC_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            ONBOARDING_SYNC_CHANNEL_ID,
            "Onboarding Sync",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications when a pending onboarding is synchronized"
        }

        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ONBOARDING_SYNC_CHANNEL_ID = "onboarding_sync"
    }
}
