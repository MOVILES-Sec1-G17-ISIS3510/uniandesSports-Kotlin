package com.uniandes.sport.viewmodels.retos

import com.uniandes.sport.models.Reto
import com.uniandes.sport.models.UserChallenge
import kotlinx.coroutines.flow.StateFlow

// este es el contrato del viewmodel para retos
// aqui desimos que datos tiene y que puede aser
interface RetosViewModelInterface {
    val retos: StateFlow<List<Reto>>
    val activeChallenges: StateFlow<List<Reto>>
    val exploreChallenges: StateFlow<List<Reto>>
    val userChallenges: StateFlow<Map<String, UserChallenge>> // retoid -> userchallenge
    val isLoading: StateFlow<Boolean>
    
    // bamos a ber si se krea o no el reto
    val creationStatus: StateFlow<String> // "IDLE", "SUCCESS", or the error message

    // por si queremos fitrar por tipo (all, individual, team)
    val selectedType: StateFlow<String>
    // filtro por deporte (all, soccer, etc)
    val selectedSport: StateFlow<String>
    
    // TACTICA: Búsqueda con Debounce
    val searchQuery: StateFlow<String>
    fun setSearchQuery(query: String)

    fun setTypeFilter(type: String)
    fun setSportFilter(sport: String)
    
    fun fetchRetos()
    
    // para unirse a uno nuevo
    fun joinReto(retoId: String, userId: String)
    
    // para abandonar un reto activo
    fun leaveReto(retoId: String, userId: String)
    
    // para crear retos nuevos desde el celular
    fun addReto(reto: Reto)
    
    // para actualizar progreso (analizado por IA)
    // para actualizar progreso de forma sincronizada (IA calcula el nuevo, restamos el viejo)
    fun syncChallengeProgress(retoId: String, oldProgress: Double, newProgress: Double, trackText: String, eventId: String)
}
