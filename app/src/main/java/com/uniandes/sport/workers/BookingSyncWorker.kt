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
import com.google.firebase.firestore.FirebaseFirestore
import com.uniandes.sport.MainActivity
import com.uniandes.sport.R
import com.uniandes.sport.data.local.PendingBookingStore
import com.uniandes.sport.models.BookingRequest
import kotlinx.coroutines.tasks.await

class BookingSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = FirebaseFirestore.getInstance()

    override suspend fun doWork(): Result {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w("BookingSyncWorker", "No authenticated user. Retrying pending bookings later.")
            return Result.retry()
        }

        val pendingItems = PendingBookingStore.getAll(applicationContext)
        if (pendingItems.isEmpty()) {
            Log.d("BookingSyncWorker", "No pending bookings to sync.")
            return Result.success()
        }

        var allSuccessful = true

        for (pending in pendingItems) {
            try {
                val requestDoc = db.collection("coach_requests").document(pending.localId)
                
                val finalProfId = if (pending.targetProfesorId == "broadcast") "" else pending.targetProfesorId
                val finalProfName = if (pending.targetProfesorId == "broadcast") "" else pending.targetProfesorName
                
                val request = BookingRequest(
                    id = requestDoc.id,
                    userId = pending.userId, 
                    studentName = pending.studentName,
                    targetProfesorId = finalProfId,
                    targetProfesorName = finalProfName,
                    sport = pending.sport,
                    skillLevel = pending.skillLevel,
                    schedule = pending.schedule,
                    notes = pending.notes,
                    status = "pending"
                )
                
                requestDoc.set(request).await()

                PendingBookingStore.remove(applicationContext, pending.localId)
                notifyBookingCreated(
                    sport = pending.sport,
                    coachName = if (finalProfName.isNotEmpty()) finalProfName else "a coach"
                )

                Log.d("BookingSyncWorker", "Pending booking synced successfully: ${pending.localId}")
            } catch (e: Exception) {
                allSuccessful = false
                Log.e("BookingSyncWorker", "Failed to sync pending booking: ${pending.localId}", e)
            }
        }

        return if (allSuccessful) Result.success() else Result.retry()
    }

    private fun notifyBookingCreated(sport: String, coachName: String) {
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
            sport.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = "Booking submitted"
        val text = "Your offline request to book $sport with $coachName has been synced."

        val notification = NotificationCompat.Builder(applicationContext, BOOKING_SYNC_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(BOOKING_SYNC_CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            BOOKING_SYNC_CHANNEL_ID,
            "Booking Sync Notification",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications when a pending booking is synchronized"
        }

        manager.createNotificationChannel(channel)
    }

    companion object {
        const val BOOKING_SYNC_CHANNEL_ID = "booking_sync"
    }
}
