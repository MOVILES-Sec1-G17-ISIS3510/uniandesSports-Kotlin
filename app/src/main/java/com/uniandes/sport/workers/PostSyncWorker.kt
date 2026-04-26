package com.uniandes.sport.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.uniandes.sport.data.local.CommunitiesCacheDatabase
import com.uniandes.sport.models.MessageStatus
import kotlinx.coroutines.tasks.await

class PostSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = FirebaseFirestore.getInstance()
    private val cacheDao = CommunitiesCacheDatabase.getInstance(applicationContext).cacheDao()

    override suspend fun doWork(): Result {
        Log.d("PostSyncWorker", "Starting pending posts synchronization...")

        val pendingPosts = cacheDao.getPendingPosts()
        if (pendingPosts.isEmpty()) {
            Log.d("PostSyncWorker", "No pending posts to sync.")
            return Result.success()
        }

        var allSuccessful = true

        for (pending in pendingPosts) {
            try {
                Log.d("PostSyncWorker", "Syncing post ${pending.localId} to community ${pending.communityId}")

                val postRef = db.collection("communities")
                    .document(pending.communityId)
                    .collection("posts")
                    .document(pending.localId)

                val postPayload = hashMapOf(
                    "author" to pending.authorName,
                    "role" to pending.role,
                    "content" to pending.content,
                    "time" to "Just now",
                    "pinned" to pending.pinned,
                    "likes" to 0,
                    "createdAt" to pending.createdAt
                )

                // Write the Firestore post using the local ID so the optimistic item can be updated in place.
                db.runTransaction { transaction ->
                    transaction.set(postRef, postPayload)
                }.await()

                // Update the cached post status to SENT
                cacheDao.updatePostStatus(
                    communityId = pending.communityId,
                    postId = pending.localId,
                    status = MessageStatus.SENT.name
                )

                cacheDao.deletePendingPost(pending.localId)
                Log.d("PostSyncWorker", "Successfully synced post ${pending.localId}")

            } catch (e: Exception) {
                Log.e("PostSyncWorker", "Failed to sync post ${pending.localId}", e)
                allSuccessful = false
                // Depending on error type, we might mark as ERROR or keep as SENDING for retry
            }
        }

        return if (allSuccessful) Result.success() else Result.retry()
    }
}