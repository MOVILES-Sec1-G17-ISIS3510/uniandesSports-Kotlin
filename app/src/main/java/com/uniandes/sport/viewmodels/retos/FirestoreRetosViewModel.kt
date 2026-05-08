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
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.uniandes.sport.data.local.PendingRetoActionPayload
import com.uniandes.sport.data.local.PendingRetoActionStore
import com.uniandes.sport.data.local.RetosFileStorage
import com.uniandes.sport.data.local.RetosKeyValueStore
import com.uniandes.sport.data.local.RetosLocalRepository
import com.uniandes.sport.workers.RetoActionSyncWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// viewmodel de retos con soporte de multithreading y local storage.
//
// --- multithreading (corrutinas + dispatchers) ---
// usamos dispatchers.io para operaciones costosas (firestore, room, archivos)
// y dispatchers.main para actualizar la ui, evitando gui lagging y anrs.
//
// --- local storage (estrategia online-first con cache local) ---
// antipatron resuelto: "missed caching opportunity".
// sin cache, cada apertura de la pantalla depende de la red y muestra pantalla vacia.
// con room como cache local, los retos se muestran al instante desde sqlite
// y en background el snapshotlistener sincroniza datos frescos del servidor.
// capas de almacenamiento local:
//   1. room (bd relacional) — cache persistente de retos en sqlite
//   2. sharedpreferences (llave-valor) — filtros de ui y drafts de creacion
//   3. archivos locales (json) — exportacion de snapshots para auditoria
class FirestoreRetosViewModel : ViewModel(), RetosViewModelInterface {
    private val db = FirebaseFirestore.getInstance()
    private var retosListener: com.google.firebase.firestore.ListenerRegistration? = null

    // acceso al contexto de la app para inicializar el repositorio local.
    // se obtiene via firebaseapp para no requerir context en el constructor del viewmodel
    private val appContext
        get() = try {
            FirebaseApp.getInstance().applicationContext
        } catch (_: Exception) {
            null
        }

    // repositorio local (facade sobre room dao).
    // patron facade: el viewmodel no conoce los detalles de room/sqlite,
    // solo habla con el repositorio que expone modelos de negocio (reto)
    private val localRepository: RetosLocalRepository?
        get() = appContext?.let { RetosLocalRepository.getInstance(it) }

    // job para la corrutina que observa cambios en la cache local de room.
    // se cancela en oncleared() para evitar memory leaks
    private var retosCacheJob: Job? = null

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
        // restaurar filtros guardados en sharedpreferences (antipatron "missing state persistence").
        // sin esto, los filtros se resetean a "all" cada vez que el usuario reentra a la pantalla
        restoreFiltersFromPreferences()
        // iniciar observacion de la cache local de room y luego conectar con firestore
        observeLocalRetos()
        fetchRetos()
    }

    // restaura los filtros de tipo y deporte desde sharedpreferences.
    // usa .apply() (asincrono) en vez de .commit() (sincrono) para no bloquear el main thread
    private fun restoreFiltersFromPreferences() {
        val context = appContext ?: return
        _selectedType.value = RetosKeyValueStore.getSelectedType(context)
        _selectedSport.value = RetosKeyValueStore.getSelectedSport(context)
        _searchQuery.value = RetosKeyValueStore.getSearchQuery(context)
    }

    // persiste los filtros actuales en sharedpreferences cuando el usuario los cambia
    override fun setTypeFilter(type: String) {
        _selectedType.value = type
        appContext?.let { RetosKeyValueStore.saveSelectedType(it, type) }
    }

    override fun setSportFilter(sport: String) {
        _selectedSport.value = sport
        appContext?.let { RetosKeyValueStore.saveSelectedSport(it, sport) }
    }

    override fun setSearchQuery(query: String) {
        _searchQuery.value = query
        appContext?.let { RetosKeyValueStore.saveSearchQuery(it, query) }
    }

    // observa la cache local de room reactivamente con flow.
    // si room tiene datos cacheados (de una sesion anterior), se muestran al instante
    // mientras el snapshotlistener se conecta con el servidor.
    // esto resuelve el antipatron "missed caching opportunity" en el cold start
    private fun observeLocalRetos() {
        if (retosCacheJob != null) return
        val repo = localRepository ?: return
        retosCacheJob = viewModelScope.launch {
            repo.observeRetos().collect { localList ->
                if (localList.isNotEmpty() || _retos.value.isEmpty()) {
                    _retos.value = localList
                }
            }
        }
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

                    // guardar en room (cache local) en background con dispatchers.io.
                    // la proxima vez que el usuario abra la app, estos datos se
                    // mostraran al instante desde sqlite sin esperar la red
                    viewModelScope.launch(Dispatchers.IO) {
                        localRepository?.replaceRetos(list)
                    }

                    // exportar snapshot a archivo local para auditoria/debugging.
                    // se guarda en context.filesdir/retos/ como json
                    viewModelScope.launch(Dispatchers.IO) {
                        appContext?.let { ctx ->
                            RetosFileStorage.exportRetosSnapshot(ctx, list)
                            Log.d("RetosVM", "snapshot de retos exportado a archivos locales")
                        }
                    }
                }
            }
    }

    // refresh forzado desde el servidor usando corrutina.
    // el bloque finally con withcontext(dispatchers.main) garantiza que el callback
    // siempre se ejecute en el hilo principal, incluso si hay una excepcion
    override fun refreshRetos(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                syncRetosFromServer()
            } finally {
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    // funcion suspendida que sincroniza los retos desde el servidor de firestore.
    // withcontext(dispatchers.io) mueve toda la ejecucion al pool de hilos de
    // entrada/salida, liberando el hilo principal para que la ui siga respondiendo.
    // tambien actualiza la cache local de room con los datos frescos
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
            // actualizar la cache local de room con datos frescos
            localRepository?.replaceRetos(list)
        } catch (e: Exception) {
            Log.e("RetosVM", "error sincronizando retos desde el servidor", e)
        }
    }

    // unirse a un reto con soporte offline (eventual connectivity).
    // si hay internet, ejecuta la transaccion directamente en firestore.
    // si no hay internet, encola la accion en pendingretoactionstore
    // y programa un retoactionsyncworker que se ejecutara cuando vuelva la red.
    // resuelve antipatrones #1 (blocked app) y #5 (non-existent result notification)
    override fun joinReto(
        retoId: String,
        userId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = appContext

                // verificar conectividad antes de intentar la operacion de red
                if (context != null && !isNetworkConnected(context)) {
                    // modo offline: encolar la accion para sincronizar despues
                    val payload = PendingRetoActionPayload(
                        localId = "join_${retoId}_${userId}_${System.currentTimeMillis()}",
                        retoId = retoId,
                        userId = userId,
                        action = "join"
                    )
                    PendingRetoActionStore.enqueue(context, payload)
                    enqueueRetoActionSync(context)
                    Log.d("RetosVM", "join encolado offline para reto $retoId")
                    withContext(Dispatchers.Main) { onSuccess() }
                    return@launch
                }

                // modo online: transaccion directa en firestore
                val docRef = db.collection("challenges").document(retoId)

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

                // corrutina anidada (fire and forget) en dispatchers.io
                launch(Dispatchers.IO) {
                    syncParticipantsCountInternal(retoId)
                }

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

    // abandonar un reto con soporte offline, misma estrategia que joinreto
    override fun leaveReto(
        retoId: String,
        userId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = appContext

                // modo offline: encolar la accion leave
                if (context != null && !isNetworkConnected(context)) {
                    val payload = PendingRetoActionPayload(
                        localId = "leave_${retoId}_${userId}_${System.currentTimeMillis()}",
                        retoId = retoId,
                        userId = userId,
                        action = "leave"
                    )
                    PendingRetoActionStore.enqueue(context, payload)
                    enqueueRetoActionSync(context)
                    Log.d("RetosVM", "leave encolado offline para reto $retoId")
                    withContext(Dispatchers.Main) { onSuccess() }
                    return@launch
                }

                // modo online: transaccion directa
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

    // crear un reto nuevo usando corrutinas
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

        // firestore.set() encola escrituras offline automaticamente.
        // usamos actualizacion optimista: notificamos exito inmediatamente
        // y firestore sincroniza cuando vuelva internet.
        // no usamos .await() porque no se resuelve offline
        val docRef = db.collection("challenges").document()

        docRef.set(data)
            .addOnFailureListener { e ->
                Log.e("RetosVM", "error al guardar reto en firestore", e)
            }

        Log.d("RetosVM", "reto encolado, id: ${docRef.id}")
        appContext?.let { RetosKeyValueStore.clearNewRetoDraft(it) }

        val context = appContext
        if (context != null && !isNetworkConnected(context)) {
            // guardar como accion pendiente para mostrar en la ui
            PendingRetoActionStore.enqueue(context, PendingRetoActionPayload(
                localId = "create_${docRef.id}_${System.currentTimeMillis()}",
                retoId = docRef.id,
                userId = uid,
                action = "create"
            ))
        }

        _creationStatus.value = "SUCCESS"
    }

    // sincronizar progreso de un reto usando multiples corrutinas con dispatchers
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

    // funcion suspendida que verifica el conteo real de participantes desde el servidor
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

    // funcion suspendida que verifica que un reto recien creado exista en el servidor
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

    // funcion suspendida que recalcula el progreso global promediando el de todos los participantes
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

    // verificar si hay conexion a internet.
    // usa connectivitymanager para revisar capacidades de red.
    // patron identico al de firestoreprofesoresviewmodel
    private fun isNetworkConnected(context: android.content.Context): Boolean {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    // encolar el worker de sincronizacion de acciones pendientes.
    // el worker solo se ejecuta cuando hay conexion (networktype.connected)
    private fun enqueueRetoActionSync(context: android.content.Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<RetoActionSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    // limpieza de recursos: remover listener de firestore y cancelar job de room.
    // viewmodelscope cancela automaticamente sus corrutinas hijas en ondestroy
    override fun onCleared() {
        super.onCleared()
        retosListener?.remove()
        retosCacheJob?.cancel()
    }
}
