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

interface MyStatsViewModelInterface {
    val stats: StateFlow<UserStatsEntity>
    val badges: StateFlow<List<BadgeEntity>>
    val syncStatus: StateFlow<String>
    val isLoading: StateFlow<Boolean>

    fun refreshStats(forceSync: Boolean = false)
    fun markBadgesAsViewed()
}

class MyStatsViewModel(
    private val repository: MyStatsRepository,
    private val userId: String
) : ViewModel(), MyStatsViewModelInterface {

    private val _stats = MutableStateFlow(
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

    private val _syncStatus = MutableStateFlow("IDLE")
    override val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        Log.d("📊 MYSTATS:", "🎯 MyStatsViewModel INIT userId=$userId")

        // Observar stats: el flow del repositorio emite caché primero,
        // luego el valor fresco de Firestore, y después completa.
        viewModelScope.launch {
            _isLoading.value = true
            repository.getStats(userId).collect { freshStats ->
                Log.d("📊 MYSTATS:", "📥 stats recibidos: events=${freshStats.totalEvents} posts=${freshStats.totalPosts} km=${freshStats.totalKm} level=${freshStats.level}")
                _stats.value = freshStats
                _isLoading.value = false
            }
        }

        // Observar badges desde Room de forma reactiva.
        // Room emite automáticamente cada vez que computeAndSaveBadges() inserta badges,
        // por lo que la UI se actualiza sin necesidad de acción adicional.
        viewModelScope.launch {
            repository.getBadges(userId).collect { badgeList ->
                Log.d("📊 MYSTATS:", "🏅 badges recibidos: ${badgeList.size}")
                _badges.value = badgeList
            }
        }

        // Observar syncStatus del repositorio
        viewModelScope.launch {
            repository.getSyncStatus().collect { status ->
                Log.d("📊 MYSTATS:", "🔄 syncStatus: $status")
                _syncStatus.value = status
                if (status == "SYNCING") _isLoading.value = true
            }
        }
    }

    /**
     * Fuerza una re-sincronización desde Firestore ignorando el TTL de caché.
     *
     * BUG CORREGIDO: antes no pasaba forceSync=true al repositorio.
     */
    override fun refreshStats(forceSync: Boolean) {
        _isLoading.value = true
        viewModelScope.launch {
            repository.getStats(userId, forceRefresh = true).collect { freshStats ->
                _stats.value = freshStats
                _isLoading.value = false
            }
        }
    }

    override fun markBadgesAsViewed() {
        viewModelScope.launch {
            android.util.Log.i("MyStatsViewModel", "Badges viewed by user: $userId")
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
