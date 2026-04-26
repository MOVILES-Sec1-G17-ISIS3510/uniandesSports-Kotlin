package com.uniandes.sport.viewmodels.running

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import com.google.firebase.firestore.Query
import com.uniandes.sport.models.RunSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.ListenerRegistration

class FirestoreRunningViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val _pastRuns = MutableStateFlow<List<RunSession>>(emptyList())
    val pastRuns: StateFlow<List<RunSession>> = _pastRuns.asStateFlow()
    
    private var activityListener: ListenerRegistration? = null

    suspend fun saveRunSession(session: RunSession): String? {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e("FirestoreRunning", "Cannot save session: No user logged in!")
            return null
        }
        
        Log.d("FirestoreRunning", "Attempting to save session for user: $userId")
        val collection = db.collection("users").document(userId).collection("runs")
        
        return try {
            val docId = session.id.takeIf { it.isNotBlank() } ?: collection.document().id
            val sessionWithId = session.copy(id = docId, userId = userId)
            collection.document(docId).set(sessionWithId) // EVC: Removed .await() to use Firestore native offline cache
            Log.d("FirestoreRunning", "Session saved (locally if offline) with ID: $docId")
            docId
        } catch (e: Exception) {
            Log.e("FirestoreRunning", "Error saving session to Firestore", e)
            null
        }
    }

    fun fetchPastRuns() {
        val userId = auth.currentUser?.uid ?: return
        
        // Remove existing listener if any
        activityListener?.remove()
        
        val collection = db.collection("users").document(userId).collection("runs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            
        activityListener = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("FirestoreRunning", "Listen failed.", error)
                return@addSnapshotListener
            }
            
            if (snapshot != null) {
                val runs = snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(RunSession::class.java)
                    } catch (e: Exception) {
                        Log.e("FirestoreRunning", "Error deserializing run ${doc.id}: ${e.message}")
                        null
                    }
                }
                _pastRuns.value = runs
                Log.d("FirestoreRunning", "Real-time sync: ${runs.size} runs updated.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activityListener?.remove()
    }
}
