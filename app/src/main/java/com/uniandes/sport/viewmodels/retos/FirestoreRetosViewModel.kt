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
import kotlinx.coroutines.flow.*
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth

// viewmodel de firestore para retos de verdad
class FirestoreRetosViewModel : ViewModel(), RetosViewModelInterface {
    private val db = FirebaseFirestore.getInstance()
    private var retosListener: com.google.firebase.firestore.ListenerRegistration? = null


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

    private val _searchQuery = MutableStateFlow("")
    override val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val debouncedSearchQuery = _searchQuery
        .debounce(300L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    override val activeChallenges: StateFlow<List<Reto>> = combine(
        _retos,
        debouncedSearchQuery
    ) { list, query ->
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        list.filter { it.participants.contains(uid) && it.status == "active" }
            .filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    override val exploreChallenges: StateFlow<List<Reto>> = combine(
        _retos,
        debouncedSearchQuery,
        _selectedType,
        _selectedSport
    ) { list, query, type, sport ->
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        list.filter { !it.participants.contains(uid) }
            .filter { if (type == "All") true else it.type.equals(type, ignoreCase = true) }
            .filter { if (sport == "All Sports") true else it.sport.equals(sport, ignoreCase = true) }
            .filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

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

    override fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    override fun fetchRetos() {
        if (retosListener != null) return // Already listening
        
        _isLoading.value = true
        retosListener = db.collection("challenges")
            .addSnapshotListener { snapshot, e ->
                _isLoading.value = false
                if (e != null) {
                    Log.e("RetosVM", "fallo al oir los retos", e)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val list = snapshot.mapNotNull { doc ->
                        try {
                            doc.toObject(Reto::class.java)?.apply { id = doc.id }
                        } catch (ex: Exception) {
                            Log.e("RetosVM", "error parseando ${doc.id}: ${ex.message}")
                            null
                        }
                    }
                    _retos.value = list
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        retosListener?.remove()
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
            // fetchRetos() no hace falta pq tenemos snapshotListener
        }
    }

    override fun leaveReto(retoId: String, userId: String) {
        val docRef = db.collection("challenges").document(retoId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val participants = (snapshot.get("participants") as? List<String>)?.toMutableList() ?: mutableListOf()
            val progressMap = (snapshot.get("progressByUser") as? Map<String, Double>)?.toMutableMap() ?: mutableMapOf()
            
            if (participants.contains(userId)) {
                participants.remove(userId)
                progressMap.remove(userId)
                transaction.update(docRef, "participants", participants)
                transaction.update(docRef, "participantsCount", participants.size)
                transaction.update(docRef, "progressByUser", progressMap)
            }
        }.addOnSuccessListener {
            // fetchRetos() no hace falta pq tenemos snapshotListener
        }.addOnFailureListener { e ->
            Log.e("RetosVM", "Fallo al abandonar el reto $retoId", e)
        }
    }

    override fun addReto(reto: Reto) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val user = auth.currentUser
        
        if (user == null) {
            _creationStatus.value = "ERROR: You are not logged in"
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
            }
            .addOnFailureListener { e ->
                Log.e("RetosVM", "uy fallo feo al guardar en firestore", e)
                _creationStatus.value = "ERROR: ${e.message}"
            }
    }

    override fun syncChallengeProgress(
        retoId: String,
        oldProgress: Double,
        newProgress: Double,
        reviewText: String,
        eventId: String
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val docRef = db.collection("challenges").document(retoId)
        val delta = newProgress - oldProgress
        
        // Si el cambio es insignificante, no hacemos nada para ahorrar escrituras
        if (Math.abs(delta) < 0.01) return
        
        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            
            // Verificamos si el usuario es participante
            val rawParticipants = snapshot.get("participants") as? List<*> ?: emptyList<Any>()
            val participants = rawParticipants.map { it.toString() }
            
            if (participants.contains(uid)) {
                // Obtenemos el progreso actual del mapa global
                val progressByUser = snapshot.get("progressByUser") as? Map<*, *>
                val currentTotal = (progressByUser?.get(uid) as? Number)?.toDouble() ?: 0.0
                
                // Calculamos el nuevo total aplicando solo la diferencia
                // Esto permite que si editamos una review de 30% a 20%, el total baje 10%
                val newTotal = (currentTotal + delta).coerceIn(0.0, 100.0)
                
                // 1. Actualización atómica del mapa
                transaction.update(docRef, "progressByUser.$uid", newTotal)
                transaction.update(docRef, "updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp())
                
                // 2. Registrar el ajuste en el log (Audit Trail)
                val reviewLogRef = docRef.collection("reviews").document()
                val reviewLog = mapOf(
                    "userId" to uid,
                    "eventId" to eventId,
                    "delta" to delta,
                    "previousValue" to oldProgress,
                    "newValue" to newProgress,
                    "reviewText" to reviewText,
                    "type" to "SYNC_UPDATE",
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                transaction.set(reviewLogRef, reviewLog)
            }
        }.addOnSuccessListener {
            Log.i("RetosVM", "Sincronización exitosa: delta $delta aplicado a reto $retoId")
        }.addOnFailureListener { e ->
            Log.e("RetosVM", "Error en sincronización de reto $retoId", e)
        }
    }
}
