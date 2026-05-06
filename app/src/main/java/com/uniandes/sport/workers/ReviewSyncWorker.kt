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
import com.uniandes.sport.data.local.PendingReviewStore
import com.uniandes.sport.models.Review
import kotlinx.coroutines.tasks.await

class ReviewSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = FirebaseFirestore.getInstance()

    override suspend fun doWork(): Result {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w("ReviewSyncWorker", "No authenticated user. Retrying pending reviews later.")
            return Result.retry()
        }

        val pendingItems = PendingReviewStore.getAll(applicationContext)
        if (pendingItems.isEmpty()) {
            Log.d("ReviewSyncWorker", "No pending reviews to sync.")
            return Result.success()
        }

        var allSuccessful = true

        for (pending in pendingItems) {
            try {
                val reviewsCollection = db.collection("profesores")
                    .document(pending.profesorId)
                    .collection("reviews")

                val existing = reviewsCollection
                    .whereEqualTo("reviewerId", pending.reviewerId)
                    .limit(1)
                    .get()
                    .await()

                if (!existing.isEmpty) {
                    PendingReviewStore.remove(applicationContext, pending.localId)
                    continue
                }

                val reviewRef = reviewsCollection.document()
                val reviewToSave = Review(
                    id = reviewRef.id,
                    reviewerId = pending.reviewerId,
                    estudiante = pending.estudiante,
                    rating = pending.rating.coerceIn(1, 5),
                    comentario = pending.comentario,
                    fecha = pending.fecha
                )

                db.runTransaction { transaction ->
                    val profRef = db.collection("profesores").document(pending.profesorId)
                    val profSnapshot = transaction.get(profRef)
                    val currentTotal = profSnapshot.getLong("totalReviews") ?: 0
                    val currentRating = profSnapshot.getDouble("rating") ?: 0.0
                    val newTotal = currentTotal + 1
                    val newRating = ((currentRating * currentTotal) + reviewToSave.rating) / newTotal

                    transaction.set(reviewRef, reviewToSave)
                    transaction.update(profRef, "totalReviews", newTotal)
                    transaction.update(profRef, "rating", newRating)
                }.await()

                PendingReviewStore.remove(applicationContext, pending.localId)
                notifyReviewPublished()
                Log.d("ReviewSyncWorker", "Pending review synced successfully: ${pending.localId}")
            } catch (e: Exception) {
                allSuccessful = false
                Log.e("ReviewSyncWorker", "Failed to sync pending review: ${pending.localId}", e)
            }
        }

        return if (allSuccessful) Result.success() else Result.retry()
    }

    private fun notifyReviewPublished() {
        createNotificationChannelIfNeeded()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) return
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

        val notification = NotificationCompat.Builder(applicationContext, REVIEW_SYNC_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Review published")
            .setContentText("Your offline coach review has been synced.")
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
        if (manager.getNotificationChannel(REVIEW_SYNC_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            REVIEW_SYNC_CHANNEL_ID,
            "Review Sync Notification",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications when an offline review is synchronized"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val REVIEW_SYNC_CHANNEL_ID = "review_sync"
    }
}
