package com.uniandes.sport.viewmodels.retos

import com.uniandes.sport.models.Reto
import com.uniandes.sport.models.UserChallenge
import kotlinx.coroutines.flow.StateFlow

// contrato del viewmodel para retos.
// define los datos observables (stateflow) y las operaciones disponibles.
// las operaciones de red usan callbacks opcionales (onsuccess/onfailure)
// que se ejecutan en el hilo principal gracias a dispatchers.main
interface RetosViewModelInterface {
    val retos: StateFlow<List<Reto>>
    val activeChallenges: StateFlow<List<Reto>>
    val exploreChallenges: StateFlow<List<Reto>>
    val userChallenges: StateFlow<Map<String, UserChallenge>>
    val isLoading: StateFlow<Boolean>

    // estado de creacion: "IDLE", "SUCCESS", o el mensaje de error
    val creationStatus: StateFlow<String>

    // filtros de tipo (all, individual, team) y deporte (all, soccer, etc)
    val selectedType: StateFlow<String>
    val selectedSport: StateFlow<String>

    // busqueda con debounce para no ejecutar filtros en cada tecla
    val searchQuery: StateFlow<String>
    fun setSearchQuery(query: String)

    fun setTypeFilter(type: String)
    fun setSportFilter(sport: String)

    fun fetchRetos()

    // refresh forzado desde el servidor, el callback corre en dispatchers.main
    fun refreshRetos(onComplete: () -> Unit = {})

    // unirse a un reto, usa corrutinas con dispatchers.io para la transaccion
    fun joinReto(retoId: String, userId: String, onSuccess: () -> Unit = {}, onFailure: (Exception) -> Unit = {})

    // abandonar un reto, misma estrategia de multithreading
    fun leaveReto(retoId: String, userId: String, onSuccess: () -> Unit = {}, onFailure: (Exception) -> Unit = {})

    // crear retos nuevos, la escritura corre en dispatchers.io
    fun addReto(reto: Reto)

    // sincronizar progreso con corrutinas anidadas en dispatchers.io
    fun syncChallengeProgress(retoId: String, oldProgress: Double, newProgress: Double, trackText: String, eventId: String)
}
