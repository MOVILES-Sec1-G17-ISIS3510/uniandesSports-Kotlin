package com.uniandes.sport.viewmodels.stats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uniandes.sport.data.cache.BadgeArrayMapCache
import com.uniandes.sport.data.entities.BadgeEntity
import com.uniandes.sport.data.entities.UserStatsEntity
import com.uniandes.sport.data.repositories.MyStatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * MyStatsViewModelInterface — Contrato para ViewModel de estadísticas.
 * 
 * DOCUMENTACIÓN:
 * - stats: StateFlow<UserStatsEntity?> para UI
 * - badges: StateFlow<List<BadgeEntity>> para UI
 * - syncStatus: StateFlow<String> para indicadores de sync
 * - isLoading: StateFlow<Boolean> para mostrar spinners
 * - refreshStats(forceSync): Trigger manual de sincronización
 * 
 * PATRÓN: Interface-based ViewModel (como en el proyecto)
 * Permite multiple implementations (Firestore vs Dummy para testing)
 * 
 * @author Juan Felipe Hernández
 */
interface MyStatsViewModelInterface {
    val stats: StateFlow<UserStatsEntity>
    val badges: StateFlow<List<BadgeEntity>>
    val syncStatus: StateFlow<String>
    val isLoading: StateFlow<Boolean>
    
    fun refreshStats(forceSync: Boolean = false)
    fun markBadgesAsViewed()
}

/**
 * MyStatsViewModel — Implementación Firestore del ViewModel.
 * 
 * DOCUMENTACIÓN TÉCNICA:
 * 
 * FEATURE: Multi-threading (Requisito a)
 * - Línea 53-54: viewModelScope.launch para coroutines
 * - Todos los observables ocurren en Dispatchers.Main.immediate
 * - Repository maneja threading (Dispatchers.IO para DB)
 * 
 * FEATURE: Caching (Requisito c)
 * - Línea 56-61: Observar Repository flow con cache-first
 * - StateFlow reemite automáticamente a Compose
 * - Cambios en caché disparan recomposiciones
 * 
 * FEATURE: Eventual Connectivity (Requisito d)
 * - Línea 62-68: Observar syncStatus (IDLE, SYNCING, ERROR)
 * - UI muestra indicadores: 🟢 Conectado, 🔄 Sincronizando, ⚠️ Error
 * 
 * ESTRUCTURA:
 * - _stats: MutableStateFlow privado (write)
 * - stats: StateFlow público (read-only para UI)
 * - init {} → línea 52-72: Observar flujos al crear ViewModel
 * - refreshStats() → línea 74-80: Trigger manual de sync
 * 
 * @author Juan Felipe Hernández
 * @since 26-may-2026
 */
class MyStatsViewModel(
    private val repository: MyStatsRepository,
    private val userId: String
) : ViewModel(), MyStatsViewModelInterface {

    // FEATURE: Caching - StateFlow para reactive updates
    private val _stats = MutableStateFlow<UserStatsEntity>(
        UserStatsEntity(
            userId = userId,
            totalKm = 0f,
            totalEvents = 0,
            totalPosts = 0,
            totalMessages = 0,
            level = 1,
            points = 0,
            streakDays = 0,
            lastSyncAt = 0L,
            syncStatus = "IDLE",
            hasRealData = false
        )
    )
    override val stats: StateFlow<UserStatsEntity> = _stats.asStateFlow()

    private val _badges = MutableStateFlow<List<BadgeEntity>>(emptyList())
    override val badges: StateFlow<List<BadgeEntity>> = _badges.asStateFlow()

    private val _syncStatus = MutableStateFlow<String>("IDLE")
    override val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Línea 52-72: Inicializar observables
     * init {} se ejecuta cuando se crea el ViewModel
     * Cada launch es una coroutine que observa Repository
     */
    init {
        Log.d("📊 MYSTATS:", "🎯 MyStatsViewModel INIT for userId: $userId")
        
        viewModelScope.launch {
            repository.getStats(userId).collect { stats ->
                Log.d("📊 MYSTATS:", "📥 ViewModel received stats: events=${stats.totalEvents} posts=${stats.totalPosts} km=${stats.totalKm} level=${stats.level}")
                _stats.value = stats
                _isLoading.value = false
            }
        }

        viewModelScope.launch {
            repository.getBadges(userId).collect { badgeList ->
                Log.d("📊 MYSTATS:", "📥 ViewModel received ${badgeList.size} badges")
                _badges.value = badgeList
            }
        }

        viewModelScope.launch {
            repository.getSyncStatus().collect { status ->
                Log.d("📊 MYSTATS:", "🔄 ViewModel sync status: $status")
                _syncStatus.value = status
                _isLoading.value = status == "SYNCING"
            }
        }
    }

    /**
     * Línea 74-80: Refresh manual
     * Usuario clickea botón \"Sincronizar ahora\"
     */
    override fun refreshStats(forceSync: Boolean) {
        viewModelScope.launch {
            repository.getStats(userId).collect { stats ->
                _stats.value = stats
            }
        }
    }

    /**
     * Línea 82-86: Marcar badges como visto
     * Se llama cuando usuario abre la sección de badges
     * 
     * IMPLEMENTACIÓN: Log informativo + potencial hook para
     * analytics o actualización de UI
     */
    override fun markBadgesAsViewed() {
        viewModelScope.launch {
            // Registrar en log que badges fueron vistas
            android.util.Log.i(
                "MyStatsViewModel",
                "Badges viewed by user: $userId at ${System.currentTimeMillis()}"
            )
            
            // Potencial extensión futura:
            // - repository.markBadgesAsViewed(userId)
            // - Enviar evento a analytics
            // - Actualizar hasNewBadges flag
        }
    }

    companion object {
        fun provideFactory(
            repository: MyStatsRepository,
            userId: String
        ): androidx.lifecycle.ViewModelProvider.Factory = 
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return MyStatsViewModel(repository, userId) as T
                }
            }
    }
}
