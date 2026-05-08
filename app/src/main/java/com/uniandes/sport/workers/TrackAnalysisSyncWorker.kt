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
import com.uniandes.sport.ai.OpenAiAnalyzerStrategy
import com.uniandes.sport.data.local.AiHistoryEntry
import com.uniandes.sport.data.local.AiHistoryStore
import com.uniandes.sport.models.Reto
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

// worker que procesa analisis de tracks pendientes cuando vuelve internet.
// lee el texto del track guardado, lo envia a openai, actualiza progreso
// de retos en firestore y envia notificacion
class TrackAnalysisSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val analyzer = OpenAiAnalyzerStrategy()
    private val db = FirebaseFirestore.getInstance()

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.w("TrackSyncWorker", "no hay usuario autenticado")
            return Result.retry()
        }

        val pendingTracks = AiHistoryStore.getPendingTracks(applicationContext)
        if (pendingTracks.isEmpty()) {
            Log.d("TrackSyncWorker", "no hay tracks pendientes")
            return Result.success()
        }

        var allSuccessful = true

        for (pending in pendingTracks) {
            try {
                // obtener retos activos del usuario
                val retosSnapshot = db.collection("challenges")
                    .get(com.google.firebase.firestore.Source.SERVER)
                    .await()
                val allRetos = retosSnapshot.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(Reto::class.java).apply { id = doc.id }
                    }.getOrNull()
                }
                val activeChallenges = allRetos.filter { it.participants.contains(uid) && it.status == "active" }

                if (activeChallenges.isEmpty()) {
                    AiHistoryStore.replacePendingForEvent(
                        applicationContext, pending.eventId, "track",
                        "No active challenges to analyze."
                    )
                    continue
                }

                // parsear oldanalysis
                val oldMap = mutableMapOf<String, Double>()
                try {
                    val json = JSONObject(pending.oldAnalysisJson)
                    json.keys().forEach { key -> oldMap[key] = json.getDouble(key) }
                } catch (_: Exception) { }

                // llamar a la ia
                val result = analyzer.analyzeReview(pending.trackText, activeChallenges)

                if (result.success) {
                    val newProgressMap = result.progressByChallengeId
                    var advancedCount = 0
                    val allRelevantIds = (newProgressMap.keys + oldMap.keys).toSet()

                    // sincronizar progreso en firestore
                    allRelevantIds.forEach { retoId ->
                        val newVal = newProgressMap[retoId] ?: 0.0
                        val oldVal = oldMap[retoId] ?: 0.0
                        val delta = newVal - oldVal

                        if (Math.abs(delta) > 0.01) {
                            if (newVal > 0) advancedCount++
                            val docRef = db.collection("challenges").document(retoId)
                            db.runTransaction { transaction ->
                                val snapshot = transaction.get(docRef)
                                if (!snapshot.exists()) return@runTransaction
                                val progressByUser = (snapshot.get("progressByUser") as? Map<String, Any>)?.toMutableMap() ?: mutableMapOf()
                                val current = (progressByUser[uid] as? Number)?.toDouble() ?: 0.0
                                progressByUser[uid] = (current + delta).coerceIn(0.0, 100.0)
                                transaction.update(docRef, "progressByUser", progressByUser)
                            }.await()
                        }
                    }

                    // construir feedback con nombres de retos
                    val advancedNames = allRelevantIds.filter { retoId ->
                        val newVal = newProgressMap[retoId] ?: 0.0
                        val oldVal = oldMap[retoId] ?: 0.0
                        newVal > 0 && Math.abs(newVal - oldVal) > 0.01
                    }.mapNotNull { retoId ->
                        allRetos.find { it.id == retoId }?.let { reto ->
                            "${reto.title}: +${String.format("%.0f", newProgressMap[retoId] ?: 0.0)}%"
                        }
                    }

                    val feedback = if (advancedNames.isNotEmpty())
                        "Progress updated in ${advancedNames.size} challenge(s):\n${advancedNames.joinToString("\n")}"
                    else
                        "Activity recorded. No challenge progress applied."

                    AiHistoryStore.replacePendingForEvent(applicationContext, pending.eventId, "track", feedback)
                    notifySynced(advancedCount)
                    Log.d("TrackSyncWorker", "track analizado para ${pending.eventId}: $advancedCount challenges")
                } else {
                    allSuccessful = false
                    Log.e("TrackSyncWorker", "ia fallo: ${result.errorMessage}")
                }
            } catch (e: Exception) {
                allSuccessful = false
                Log.e("TrackSyncWorker", "error procesando track pending", e)
            }
        }

        return if (allSuccessful) Result.success() else Result.retry()
    }

    private fun notifySynced(advancedCount: Int) {
        createChannelIfNeeded()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = if (advancedCount > 0) "Your offline track updated $advancedCount challenge(s)."
            else "Your offline track has been analyzed."

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Track analysis complete")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
        val channel = NotificationChannel(CHANNEL_ID, "Track Analysis Sync", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Notifications when an offline track analysis is completed"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "track_analysis_sync"
    }
}
