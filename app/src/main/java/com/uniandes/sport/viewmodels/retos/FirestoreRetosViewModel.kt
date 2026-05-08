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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// viewmodel de retos con soporte de multithreading usando corrutinas y dispatchers.
// usamos dispatchers.io para operaciones costosas (firestore, red) y dispatchers.main
// para actualizar la ui, evitando bloquear el hilo principal y prevenir anrs.
// segun la clase (isis-3510), las apps moviles son single-threaded por defecto,
// y las operaciones costosas en el main thread causan gui lagging y anrs (>5 seg).
// las corrutinas de kotlin resuelven esto con suspend functions y dispatchers
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

    // debounce: evita ejecutar filtros en cada tecla, espera 300ms de inactividad
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
        if (retosListener != null) return

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

    // refresh forzado desde el servidor usando corrutina.
    // el bloque finally con withcontext(dispatchers.main) garantiza que el callback
    // siempre se ejecute en el hilo principal, incluso si hay una excepcion
    override fun refreshRetos(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                // sincronizamos desde el servidor en el hilo de entrada/salida
                syncRetosFromServer()
            } finally {
                // withcontext(dispatchers.main) cambia al hilo principal para el callback.
                // esto es necesario porque solo el main thread puede tocar la ui
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    // funcion suspendida que sincroniza los retos desde el servidor de firestore.
    // withcontext(dispatchers.io) mueve toda la ejecucion al pool de hilos de
    // entrada/salida, liberando el hilo principal para que la ui siga respondiendo.
    // .await() convierte el callback de firebase en codigo secuencial suspendido
    private suspend fun syncRetosFromServer() = withContext(Dispatchers.IO) {
        try {
            val snapshot = db.collection("challenges")
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()

            val list = snapshot.mapNotNull { doc ->
                runCatching {
                    doc.toObject(Reto::class.java).apply { id = doc.id }
                }.getOrNull()
            }
            _retos.value = list
        } catch (e: Exception) {
            Log.e("RetosVM", "error sincronizando retos desde el servidor", e)
        }
    }

    // unirse a un reto usando corrutinas con dispatchers.
    // patron: viewmodelscope.launch(dispatchers.io) para la transaccion de firestore,
    // launch(dispatchers.io) anidado para verificacion en background (fire and forget),
    // y withcontext(dispatchers.main) para notificar a la ui
    override fun joinReto(
        retoId: String,
        userId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        // corrutina principal en dispatchers.io: las transacciones de firestore son
        // operaciones de red (entrada/salida) que bloquearian el main thread.
        // si esto corriera en el hilo principal, la app se congelaria (gui lagging)
        // y si tarda mas de 5 segundos android mostraria un anr
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docRef = db.collection("challenges").document(retoId)

                // .await() convierte la transaccion basada en callbacks a una
                // corrutina suspendida, permitiendo codigo asincrono secuencial
                db.runTransaction { transaction ->
                    val snapshot = transaction.get(docRef)
                    val participants = (snapshot.get("participants") as? List<String>)
                        ?.toMutableList() ?: mutableListOf()
                    val progressMap = (snapshot.get("progressByUser") as? Map<String, Double>)
                        ?.toMutableMap() ?: mutableMapOf()

                    if (!participants.contains(userId)) {
                        participants.add(userId)
                        progressMap[userId] = 0.0
                        transaction.update(docRef, "participants", participants)
                        transaction.update(docRef, "participantsCount", participants.size)
                        transaction.update(docRef, "progressByUser", progressMap)
                    }
                }.await()

                // corrutina anidada (fire and forget) en dispatchers.io:
                // lanzamos una segunda corrutina dentro de la primera para verificar
                // el conteo de participantes en background, sin esperar su resultado.
                // esto demuestra multiples corrutinas ejecutandose concurrentemente
                launch(Dispatchers.IO) {
                    syncParticipantsCountInternal(retoId)
                }

                // withcontext(dispatchers.main): cambiamos al hilo principal para
                // ejecutar el callback de exito. la ui solo se puede actualizar
                // desde el main thread, si intentamos hacerlo desde io crashearia
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e("RetosVM", "error al unirse al reto $retoId", e)
                withContext(Dispatchers.Main) {
                    onFailure(e)
                }
            }
        }
    }

    // abandonar un reto con la misma estrategia de multithreading que joinreto:
    // corrutina en io -> transaccion -> corrutina anidada en io -> callback en main
    override fun leaveReto(
        retoId: String,
        userId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docRef = db.collection("challenges").document(retoId)

                db.runTransaction { transaction ->
                    val snapshot = transaction.get(docRef)
                    val participants = (snapshot.get("participants") as? List<String>)
                        ?.toMutableList() ?: mutableListOf()
                    val progressMap = (snapshot.get("progressByUser") as? Map<String, Double>)
                        ?.toMutableMap() ?: mutableMapOf()

                    if (participants.contains(userId)) {
                        participants.remove(userId)
                        progressMap.remove(userId)
                        transaction.update(docRef, "participants", participants)
                        transaction.update(docRef, "participantsCount", participants.size)
                        transaction.update(docRef, "progressByUser", progressMap)
                    }
                }.await()

                // corrutina anidada para sincronizar conteo en background
                launch(Dispatchers.IO) {
                    syncParticipantsCountInternal(retoId)
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e("RetosVM", "error al abandonar el reto $retoId", e)
                withContext(Dispatchers.Main) {
                    onFailure(e)
                }
            }
        }
    }

    // crear un reto nuevo usando corrutinas.
    // flujo: validacion en main -> escritura en io -> verificacion anidada en io -> estado en main
    override fun addReto(reto: Reto) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null) {
            _creationStatus.value = "ERROR: You are not logged in"
            return
        }

        val uid = user.uid

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

        _creationStatus.value = "IDLE"

        // corrutina en dispatchers.io para la escritura en firestore.
        // la escritura en base de datos es una operacion costosa de entrada/salida
        // que no debe correr en el hilo principal
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docRef = db.collection("challenges").document()
                // .await() suspende la corrutina hasta que firestore confirme la escritura,
                // sin bloquear ningun hilo (a diferencia de un Thread.sleep o busy wait)
                docRef.set(data).await()

                Log.d("RetosVM", "reto creado con exito, id: ${docRef.id}")

                // corrutina anidada (fire and forget): verificamos en background que
                // el reto se creo correctamente en el servidor. esta corrutina hija
                // corre concurrentemente mientras el flujo principal continua
                launch(Dispatchers.IO) {
                    verifyRetoCreation(docRef.id)
                }

                // volvemos al hilo principal para actualizar el estado de la ui.
                // _creationstatus es un stateflow observado por compose
                withContext(Dispatchers.Main) {
                    _creationStatus.value = "SUCCESS"
                }
            } catch (e: Exception) {
                Log.e("RetosVM", "error al guardar reto en firestore", e)
                withContext(Dispatchers.Main) {
                    _creationStatus.value = "ERROR: ${e.message}"
                }
            }
        }
    }

    // sincronizar progreso de un reto usando multiples corrutinas con dispatchers.
    // demuestra: corrutina padre en io, transaccion con await, corrutina anidada
    // en io para recalcular progreso global, y notificacion en main
    override fun syncChallengeProgress(
        retoId: String,
        oldProgress: Double,
        newProgress: Double,
        trackText: String,
        eventId: String
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.e("RetosVM", "sync fallida: usuario no autenticado")
            return
        }
        val docRef = db.collection("challenges").document(retoId)
        val delta = newProgress - oldProgress

        if (Math.abs(delta) < 0.01) {
            Log.d("RetosVM", "sync omitida: delta muy pequeno ($delta)")
            return
        }

        // corrutina principal en dispatchers.io para la transaccion de firestore
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.runTransaction { transaction ->
                    val snapshot = transaction.get(docRef)

                    if (!snapshot.exists()) {
                        Log.e("RetosVM", "el reto $retoId no existe")
                        return@runTransaction
                    }

                    val rawParticipants = snapshot.get("participants") as? List<*> ?: emptyList<Any>()
                    val participants = rawParticipants.map { it.toString().trim() }
                    val cleanUid = uid.trim()

                    if (participants.contains(cleanUid)) {
                        val progressByUserRaw = snapshot.get("progressByUser") as? Map<String, Any>
                            ?: emptyMap()
                        val updatedMap = progressByUserRaw.toMutableMap()

                        val currentTotal = (updatedMap[cleanUid] as? Number)?.toDouble() ?: 0.0
                        val newTotal = (currentTotal + delta).coerceIn(0.0, 100.0)

                        updatedMap[cleanUid] = newTotal
                        transaction.update(docRef, "progressByUser", updatedMap)
                        transaction.update(
                            docRef,
                            "updatedAt",
                            com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )

                        val trackLogRef = docRef.collection("tracks").document()
                        val trackLog = mapOf(
                            "userId" to cleanUid,
                            "eventId" to eventId,
                            "delta" to delta,
                            "previousValue" to oldProgress,
                            "newValue" to newProgress,
                            "trackText" to trackText,
                            "type" to "SYNC_UPDATE",
                            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        transaction.set(trackLogRef, trackLog)
                    } else {
                        Log.w(
                            "RetosVM",
                            "usuario '$cleanUid' no encontrado en participantes de $retoId"
                        )
                    }
                }.await()

                // corrutina anidada en io (fire and forget): recalculamos el progreso
                // global del reto promediando el de todos los participantes.
                // esta corrutina corre en paralelo sin bloquear el flujo principal
                launch(Dispatchers.IO) {
                    recalculateGlobalProgress(retoId)
                }

                withContext(Dispatchers.Main) {
                    Log.i("RetosVM", "sincronizacion exitosa para $retoId")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("RetosVM", "error en transaccion para $retoId: ${e.message}", e)
                }
            }
        }
    }

    // funcion suspendida que verifica el conteo real de participantes desde el servidor.
    // se ejecuta como corrutina anidada en dispatchers.io despues de join/leave.
    // patron similar a syncreviewscountinternal en el viewmodel de profesores:
    // lee el documento del servidor y corrige el conteo si hay discrepancia
    private suspend fun syncParticipantsCountInternal(retoId: String) = withContext(Dispatchers.IO) {
        try {
            val docRef = db.collection("challenges").document(retoId)
            val snapshot = docRef.get(com.google.firebase.firestore.Source.SERVER).await()

            if (snapshot.exists()) {
                val participants = snapshot.get("participants") as? List<*> ?: emptyList<Any>()
                val realCount = participants.size
                val storedCount = snapshot.getLong("participantsCount") ?: 0L

                if (realCount.toLong() != storedCount) {
                    docRef.update("participantsCount", realCount).await()
                    Log.d("RetosVM", "conteo corregido para $retoId: $storedCount -> $realCount")
                }
            }
        } catch (e: Exception) {
            Log.e("RetosVM", "error sincronizando conteo de participantes", e)
        }
    }

    // funcion suspendida que verifica que un reto recien creado exista en el servidor.
    // se lanza como corrutina anidada (fire and forget) despues de la creacion,
    // corriendo en dispatchers.io de forma concurrente con la actualizacion de la ui
    private suspend fun verifyRetoCreation(retoId: String) = withContext(Dispatchers.IO) {
        try {
            val snapshot = db.collection("challenges").document(retoId)
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()

            if (snapshot.exists()) {
                Log.d("RetosVM", "verificacion ok: reto $retoId confirmado en servidor")
            } else {
                Log.w("RetosVM", "verificacion fallida: reto $retoId no encontrado en servidor")
            }
        } catch (e: Exception) {
            Log.e("RetosVM", "error verificando creacion del reto $retoId", e)
        }
    }

    // funcion suspendida que recalcula el progreso global promediando el progreso
    // de todos los participantes. corre en dispatchers.io como corrutina anidada
    // despues de cada sync de progreso individual
    private suspend fun recalculateGlobalProgress(retoId: String) = withContext(Dispatchers.IO) {
        try {
            val docRef = db.collection("challenges").document(retoId)
            val snapshot = docRef.get(com.google.firebase.firestore.Source.SERVER).await()

            if (snapshot.exists()) {
                val progressByUser = snapshot.get("progressByUser") as? Map<String, Any>
                    ?: return@withContext
                val values = progressByUser.values.mapNotNull { (it as? Number)?.toDouble() }

                if (values.isNotEmpty()) {
                    val globalProgress = values.average()
                    docRef.update("progress", globalProgress).await()
                    Log.d("RetosVM", "progreso global recalculado para $retoId: $globalProgress%")
                }
            }
        } catch (e: Exception) {
            Log.e("RetosVM", "error recalculando progreso global para $retoId", e)
        }
    }

    // limpieza de recursos cuando el viewmodel se destruye.
    // removemos el listener de firestore para evitar fugas de memoria (memory leaks).
    // viewmodelscope cancela automaticamente sus corrutinas hijas en ondestroy
    override fun onCleared() {
        super.onCleared()
        retosListener?.remove()
    }
}
