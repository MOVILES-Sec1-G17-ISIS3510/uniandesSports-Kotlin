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
import com.uniandes.sport.data.local.PendingRetoActionStore
import kotlinx.coroutines.tasks.await

// worker que sincroniza acciones pendientes (join/leave) de retos cuando vuelve internet.
// se encola con workmanager y la restriccion networtype.connected.
// resuelve el antipatron #9: el usuario es notificado cuando la accion se sincroniza.
// patron identico a reviewsyncworker en profesores
class RetoActionSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = FirebaseFirestore.getInstance()

    override suspend fun doWork(): Result {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w("RetoActionSync", "no hay usuario autenticado, reintentando luego")
            return Result.retry()
        }

        val pendingItems = PendingRetoActionStore.getAll(applicationContext)
        if (pendingItems.isEmpty()) {
            Log.d("RetoActionSync", "no hay acciones pendientes")
            return Result.success()
        }

        var allSuccessful = true

        for (pending in pendingItems) {
            try {
                val docRef = db.collection("challenges").document(pending.retoId)

                when (pending.action) {
                    "join" -> {
                        db.runTransaction { transaction ->
                            val snapshot = transaction.get(docRef)
                            if (!snapshot.exists()) return@runTransaction

                            val participants = (snapshot.get("participants") as? List<String>)
                                ?.toMutableList() ?: mutableListOf()
                            val progressMap = (snapshot.get("progressByUser") as? Map<String, Double>)
                                ?.toMutableMap() ?: mutableMapOf()

                            if (!participants.contains(pending.userId)) {
                                participants.add(pending.userId)
                                progressMap[pending.userId] = 0.0
                                transaction.update(docRef, "participants", participants)
                                transaction.update(docRef, "participantsCount", participants.size)
                                transaction.update(docRef, "progressByUser", progressMap)
                            }
                        }.await()
                    }
                    "leave" -> {
                        db.runTransaction { transaction ->
                            val snapshot = transaction.get(docRef)
                            if (!snapshot.exists()) return@runTransaction

                            val participants = (snapshot.get("participants") as? List<String>)
                                ?.toMutableList() ?: mutableListOf()
                            val progressMap = (snapshot.get("progressByUser") as? Map<String, Double>)
                                ?.toMutableMap() ?: mutableMapOf()

                            if (participants.contains(pending.userId)) {
                                participants.remove(pending.userId)
                                progressMap.remove(pending.userId)
                                transaction.update(docRef, "participants", participants)
                                transaction.update(docRef, "participantsCount", participants.size)
                                transaction.update(docRef, "progressByUser", progressMap)
                            }
                        }.await()
                    }
                    // create: firestore ya sincronizo el write automaticamente
                    // (set() encola offline), solo necesitamos limpiar el pending
                    "create" -> {
                        Log.d("RetoActionSync", "create ya sincronizado por firestore para ${pending.retoId}")
                    }
                }

                PendingRetoActionStore.remove(applicationContext, pending.localId)
                notifyActionSynced(pending.action)
                Log.d("RetoActionSync", "accion sincronizada: ${pending.action} en reto ${pending.retoId}")
            } catch (e: Exception) {
                allSuccessful = false
                Log.e("RetoActionSync", "fallo al sincronizar ${pending.localId}", e)
            }
        }

        return if (allSuccessful) Result.success() else Result.retry()
    }

    private fun notifyActionSynced(action: String) {
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

        val title = if (action == "join") "Joined challenge" else "Left challenge"
        val text = if (action == "join")
            "Your offline challenge join has been synced."
        else
            "Your offline challenge leave has been synced."

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
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
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Challenge Action Sync",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications when an offline challenge action is synchronized"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "reto_action_sync"
    }
}
