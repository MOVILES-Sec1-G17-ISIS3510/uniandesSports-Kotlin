package com.uniandes.sport.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.storage.FirebaseStorage
import com.uniandes.sport.MainActivity
import com.uniandes.sport.R
import com.uniandes.sport.ai.OpenAiAnalyzerStrategy
import com.uniandes.sport.data.local.AiHistoryEntry
import com.uniandes.sport.data.local.AiHistoryStore
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

// worker que procesa analisis de poses pendientes cuando vuelve internet.
// se encola con workmanager y networtype.connected.
// lee la foto guardada localmente, la envia a openai, guarda el resultado
// en el historial y envia notificacion al usuario
class PoseAnalysisSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val analyzer = OpenAiAnalyzerStrategy()

    override suspend fun doWork(): Result {
        val pendingPoses = AiHistoryStore.getAll(applicationContext)
            .filter { it.type == "pose" && it.feedback.startsWith("Pending") && it.imagePath.isNotBlank() }

        if (pendingPoses.isEmpty()) {
            Log.d("PoseSyncWorker", "no hay poses pendientes")
            return Result.success()
        }

        var allSuccessful = true

        for (pending in pendingPoses) {
            try {
                val file = File(pending.imagePath)
                if (!file.exists()) {
                    Log.w("PoseSyncWorker", "foto no encontrada: ${pending.imagePath}")
                    continue
                }

                // leer foto y convertir a base64
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                val baos = ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, baos)
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                // enviar a openai para analisis
                val feedback = analyzer.analyzePose(base64)

                if (feedback != null && !feedback.startsWith("Error")) {
                    // subir foto a firebase storage para que coil la cargue con cache online
                    val uuid = UUID.randomUUID().toString()
                    val storageRef = FirebaseStorage.getInstance().reference.child("ai_poses/$uuid.jpg")
                    val uploadBaos = ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, uploadBaos)
                    try {
                        storageRef.putBytes(uploadBaos.toByteArray()).await()
                        val imageUrl = storageRef.downloadUrl.await().toString()

                        // reemplazar la entrada pending con el resultado real
                        AiHistoryStore.replacePendingForEvent(
                            applicationContext, pending.eventId, "pose", feedback
                        )
                        // actualizar imagen a url de storage
                        val allEntries = AiHistoryStore.getAll(applicationContext).toMutableList()
                        val idx = allEntries.indexOfFirst { it.eventId == pending.eventId && it.type == "pose" && it.feedback == feedback }
                        if (idx >= 0) {
                            allEntries[idx] = allEntries[idx].copy(imagePath = imageUrl)
                        }

                        Log.d("PoseSyncWorker", "pose analizada y sincronizada para ${pending.eventId}")
                    } catch (e: Exception) {
                        // si falla el upload, guardar con la foto local
                        AiHistoryStore.replacePendingForEvent(
                            applicationContext, pending.eventId, "pose", feedback
                        )
                        Log.w("PoseSyncWorker", "upload a storage fallo, usando foto local", e)
                    }

                    notifyAnalysisComplete()
                } else {
                    allSuccessful = false
                    Log.e("PoseSyncWorker", "analisis fallo: $feedback")
                }
            } catch (e: Exception) {
                allSuccessful = false
                Log.e("PoseSyncWorker", "error procesando pose pending", e)
            }
        }

        return if (allSuccessful) Result.success() else Result.retry()
    }

    private fun notifyAnalysisComplete() {
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

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Pose analysis complete")
            .setContentText("Your offline calisthenics pose has been analyzed by AI.")
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
        val channel = NotificationChannel(CHANNEL_ID, "Pose Analysis Sync", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Notifications when an offline pose analysis is completed"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "pose_analysis_sync"
    }
}
