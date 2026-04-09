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

class FirestoreRunningViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val _pastRuns = MutableStateFlow<List<RunSession>>(emptyList())
    val pastRuns: StateFlow<List<RunSession>> = _pastRuns.asStateFlow()

    suspend fun saveRunSession(session: RunSession): Boolean {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e("FirestoreRunning", "Cannot save session: No user logged in!")
            return false
        }
        
        Log.d("FirestoreRunning", "Attempting to save session for user: $userId")
        val collection = db.collection("users").document(userId).collection("runs")
        
        return try {
            val docRef = collection.document()
            val sessionWithId = session.copy(id = docRef.id, userId = userId)
            docRef.set(sessionWithId).await()
            Log.d("FirestoreRunning", "Session saved successfully with ID: ${docRef.id}")
            true
        } catch (e: Exception) {
            Log.e("FirestoreRunning", "Error saving session to Firestore", e)
            false
        }
    }

    fun fetchPastRuns() {
        val userId = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            val collection = db.collection("users").document(userId).collection("runs")
            
            try {
                val snapshot = collection
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .await()
                
                val runs = snapshot.documents.mapNotNull { it.toObject(RunSession::class.java) }
                _pastRuns.value = runs
                Log.d("FirestoreRunning", "Fetched ${runs.size} past runs.")
            } catch (e: Exception) {
                Log.e("FirestoreRunning", "Error fetching past runs", e)
            }
        }
    }
}
