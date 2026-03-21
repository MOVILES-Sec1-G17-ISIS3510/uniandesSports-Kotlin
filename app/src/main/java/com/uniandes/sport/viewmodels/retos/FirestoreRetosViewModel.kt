package com.uniandes.sport.viewmodels.retos

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.uniandes.sport.models.Reto
import com.uniandes.sport.models.UserChallenge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log
import com.google.firebase.Timestamp

// viewmodel de firestore para retos de verdad
class FirestoreRetosViewModel : ViewModel(), RetosViewModelInterface {
    private val db = FirebaseFirestore.getInstance()

    private val _retos = MutableStateFlow<List<Reto>>(emptyList())
    override val retos: StateFlow<List<Reto>> = _retos.asStateFlow()

    private val _userChallenges = MutableStateFlow<Map<String, UserChallenge>>(emptyMap())
    override val userChallenges: StateFlow<Map<String, UserChallenge>> = _userChallenges.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedType = MutableStateFlow("All")
    override val selectedType: StateFlow<String> = _selectedType.asStateFlow()

    private val _selectedSport = MutableStateFlow("All Sports")
    override val selectedSport: StateFlow<String> = _selectedSport.asStateFlow()

    private val _creationStatus = MutableStateFlow("IDLE")
    override val creationStatus: StateFlow<String> = _creationStatus.asStateFlow()

    init {
        // bamos a traer los retos apenas carge
        fetchRetos()
    }

    override fun setTypeFilter(type: String) {
        _selectedType.value = type
    }

    override fun setSportFilter(sport: String) {
        _selectedSport.value = sport
    }

    override fun fetchRetos() {
        _isLoading.value = true
        // bamos a oir los cambios en tiempo real mejor
        db.collection("challenges")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("RetosVM", "fallo al oir los retos", e)
                    _isLoading.value = false
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val rawCount = snapshot.size()
                    val list = snapshot.mapNotNull { doc ->
                        try {
                            val r = doc.toObject(Reto::class.java)
                            if (r != null) {
                                Log.d("RetosVM", "leido ok: ${doc.id} - ${r.title}")
                                r.apply { id = doc.id }
                            } else {
                                Log.e("RetosVM", "doc ${doc.id} dio null al parsear")
                                null
                            }
                        } catch (ex: Exception) {
                            Log.e("RetosVM", "error parseando ${doc.id}: ${ex.message}")
                            null
                        }
                    }
                    Log.d("RetosVM", "Firestore dio $rawCount docs, pudimos leer ${list.size}")
                    _retos.value = list
                }
                _isLoading.value = false
            }
    }

    override fun joinReto(retoId: String, userId: String) {
        val docRef = db.collection("challenges").document(retoId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val participants = (snapshot.get("participants") as? List<String>)?.toMutableList() ?: mutableListOf()
            val progressMap = (snapshot.get("progressByUser") as? Map<String, Double>)?.toMutableMap() ?: mutableMapOf()
            
            if (!participants.contains(userId)) {
                participants.add(userId)
                progressMap[userId] = 0.0
                transaction.update(docRef, "participants", participants)
                transaction.update(docRef, "participantsCount", participants.size)
                transaction.update(docRef, "progressByUser", progressMap)
            }
        }.addOnSuccessListener {
            fetchRetos()
        }
    }

    override fun addReto(reto: Reto) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val user = auth.currentUser
        
        if (user == null) {
            _creationStatus.value = "ERROR: No estas logueado carnal"
            return
        }
        
        val uid = user.uid

        // bamos a aser el mapa a mano con el uid real
        val data = hashMapOf(
            "title" to reto.title,
            "sport" to reto.sport.lowercase(),
            "difficulty" to reto.difficulty.lowercase(),
            "type" to reto.type.lowercase(),
            "goalLabel" to reto.goalLabel,
            "createdBy" to uid,
            "status" to "active",
            "participants" to listOf(uid),
            "participantsCount" to 1L,
            "progress" to 0.0,
            "progressByUser" to mapOf(uid to 0.0),
            "createdAt" to Timestamp.now(),
            "startDate" to (reto.startDate ?: Timestamp.now()),
            "endDate" to (reto.endDate ?: Timestamp.now())
        )

        Log.d("RetosVM", "guardando en 'challenges' para el user $uid...")
        _creationStatus.value = "IDLE" 
        
        val docRef = db.collection("challenges").document()
        docRef.set(data)
            .addOnSuccessListener { 
                Log.d("RetosVM", "RETO CREADO PERRO!! id: ${docRef.id}")
                _creationStatus.value = "SUCCESS"
                fetchRetos()
            }
            .addOnFailureListener { e ->
                Log.e("RetosVM", "uy fallo feo al guardar en firestore", e)
                _creationStatus.value = "ERROR: ${e.message}"
            }
    }
}
