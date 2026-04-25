package com.uniandes.sport.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.uniandes.sport.R
import com.uniandes.sport.data.local.PendingOpenMatchStore
import com.uniandes.sport.patterns.event.EventFactory
import kotlinx.coroutines.tasks.await
import java.util.Date

class OpenMatchSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = FirebaseFirestore.getInstance()

    override suspend fun doWork(): Result {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w("OpenMatchSyncWorker", "No authenticated user. Retrying pending open matches later.")
            return Result.retry()
        }

        val pendingItems = PendingOpenMatchStore.getAll(applicationContext)
        if (pendingItems.isEmpty()) {
            Log.d("OpenMatchSyncWorker", "No pending open matches to sync.")
            return Result.success()
        }

        var allSuccessful = true

        for (pending in pendingItems) {
            try {
                val event = EventFactory.createEvent(
                    title = pending.title,
                    description = pending.description,
                    location = pending.location,
                    createdBy = pending.createdBy,
                    sport = pending.sport,
                    modality = pending.modality,
                    maxParticipants = pending.maxParticipants,
                    scheduledAt = Date(pending.scheduledAtMillis),
                    finishedAt = pending.finishedAtMillis?.let { Date(it) },
                    metadata = mapOf("skillLevel" to pending.skillLevel)
                )

                val eventDoc = db.collection("events").document(pending.localId)
                event.id = eventDoc.id

                val batch = db.batch()
                batch.set(eventDoc, event)

                if (pending.shouldJoin) {
                    val memberDoc = eventDoc.collection("members").document(pending.createdBy)
                    val organizer = com.uniandes.sport.models.MatchMember(
                        userId = pending.createdBy,
                        displayName = currentUser.email ?: "Organizer",
                        joinedAt = System.currentTimeMillis(),
                        role = "organizer"
                    )
                    batch.set(memberDoc, organizer)
                }

                batch.commit().await()
                PendingOpenMatchStore.remove(applicationContext, pending.localId)
                notifyOpenMatchCreated(pending.title)

                Log.d("OpenMatchSyncWorker", "Pending open match synced successfully: ${pending.localId}")
            } catch (e: Exception) {
                allSuccessful = false
                Log.e("OpenMatchSyncWorker", "Failed to sync pending open match: ${pending.localId}", e)
            }
        }

        return if (allSuccessful) Result.success() else Result.retry()
    }

    private fun notifyOpenMatchCreated(title: String) {
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

        val notification = NotificationCompat.Builder(applicationContext, OPEN_MATCH_SYNC_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Open Match creado")
            .setContentText("\"$title\" se creo cuando volvio tu conexion.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(OPEN_MATCH_SYNC_CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            OPEN_MATCH_SYNC_CHANNEL_ID,
            "Open Match Sync",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notificaciones cuando un Open Match pendiente se sincroniza"
        }

        manager.createNotificationChannel(channel)
    }

    companion object {
        const val OPEN_MATCH_SYNC_CHANNEL_ID = "open_match_sync"
    }
}
