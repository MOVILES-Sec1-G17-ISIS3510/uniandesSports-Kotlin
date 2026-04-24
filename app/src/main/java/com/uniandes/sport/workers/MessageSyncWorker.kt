package com.uniandes.sport.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.uniandes.sport.data.local.CommunitiesCacheDatabase
import com.uniandes.sport.models.MessageStatus
import kotlinx.coroutines.tasks.await

class MessageSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = FirebaseFirestore.getInstance()
    private val cacheDao = CommunitiesCacheDatabase.getInstance(applicationContext).cacheDao()

    override suspend fun doWork(): Result {
        Log.d("MessageSyncWorker", "Starting pending messages synchronization...")

        val pendingMessages = cacheDao.getPendingMessages()
        if (pendingMessages.isEmpty()) {
            Log.d("MessageSyncWorker", "No pending messages to sync.")
            return Result.success()
        }

        var allSuccessful = true

        for (pending in pendingMessages) {
            try {
                Log.d("MessageSyncWorker", "Syncing message ${pending.localId} to channel ${pending.channelId}")

                val channelRef = db.collection("communities")
                    .document(pending.communityId)
                    .collection("channels")
                    .document(pending.channelId)
                val messageRef = channelRef.collection("messages").document(pending.localId)

                val messagePayload = hashMapOf(
                    "authorId" to pending.authorId,
                    "authorName" to pending.authorName,
                    "content" to pending.content,
                    "createdAt" to pending.createdAt,
                    "reactions" to emptyMap<String, Long>()
                )

                // Write the Firestore message using the local ID so the optimistic item can be updated in place.
                db.runTransaction { transaction ->
                    val channelSnapshot = transaction.get(channelRef)

                    transaction.set(messageRef, messagePayload)
                    val currentCount = channelSnapshot.getLong("mensajes") ?: 0L
                    transaction.update(channelRef, "mensajes", currentCount + 1L)

                }.await()

                // Keep the same message ID and only mark it as sent.
                cacheDao.updateMessageStatus(
                    communityId = pending.communityId,
                    channelId = pending.channelId,
                    messageId = pending.localId,
                    status = MessageStatus.SENT.name
                )

                cacheDao.deletePendingMessage(pending.localId)
                Log.d("MessageSyncWorker", "Successfully synced message ${pending.localId}")

            } catch (e: Exception) {
                Log.e("MessageSyncWorker", "Failed to sync message ${pending.localId}", e)
                allSuccessful = false
                // Depending on error type, we might mark as ERROR or keep as SENDING for retry
            }
        }

        return if (allSuccessful) Result.success() else Result.retry()
    }
}
