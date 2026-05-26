package com.uniandes.sport.viewmodels.stats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uniandes.sport.data.cache.BadgeArrayMapCache
import com.uniandes.sport.data.entities.BadgeEntity
import com.uniandes.sport.data.entities.UserStatsEntity
import com.uniandes.sport.data.repositories.MyStatsRepository
import com.uniandes.sport.models.ActiveChallengeData
import com.uniandes.sport.models.RunDataPoint
import com.uniandes.sport.models.SportBreakdownItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─── Interface ───────────────────────────────────────────────────────────────

interface MyStatsViewModelInterface {
    // Core stats
    val stats: StateFlow<UserStatsEntity>
    val badges: StateFlow<List<BadgeEntity>>
    val syncStatus: StateFlow<String>
    val isLoading: StateFlow<Boolean>

    // Enriched / chart data
    val recentRuns: StateFlow<List<RunDataPoint>>
    val activeChallenges: StateFlow<List<ActiveChallengeData>>
    val sportBreakdown: StateFlow<List<SportBreakdownItem>>

    fun refreshStats(forceSync: Boolean = false)
    fun markBadgesAsViewed()
}

// ─── Implementation ──────────────────────────────────────────────────────────

class MyStatsViewModel(
    private val repository: MyStatsRepository,
    private val userId: String
) : ViewModel(), MyStatsViewModelInterface {

    // ── Core stats ────────────────────────────────────────────────────────────

    private val _stats = MutableStateFlow(
        UserStatsEntity(
            userId        = userId,
            totalKm       = 0f,
            totalEvents   = 0,
            totalPosts    = 0,
            totalMessages = 0,
            level         = 1,
            points        = 0,
            streakDays    = 0,
            lastSyncAt    = 0L,
            syncStatus    = "IDLE",
            hasRealData   = false
        )
    )
    override val stats: StateFlow<UserStatsEntity> = _stats.asStateFlow()

    private val _badges = MutableStateFlow<List<BadgeEntity>>(emptyList())
    override val badges: StateFlow<List<BadgeEntity>> = _badges.asStateFlow()

    private val _syncStatus = MutableStateFlow("IDLE")
    override val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ── Enriched data ─────────────────────────────────────────────────────────

    private val _recentRuns = MutableStateFlow<List<RunDataPoint>>(emptyList())
    override val recentRuns: StateFlow<List<RunDataPoint>> = _recentRuns.asStateFlow()

    private val _activeChallenges = MutableStateFlow<List<ActiveChallengeData>>(emptyList())
    override val activeChallenges: StateFlow<List<ActiveChallengeData>> = _activeChallenges.asStateFlow()

    private val _sportBreakdown = MutableStateFlow<List<SportBreakdownItem>>(emptyList())
    override val sportBreakdown: StateFlow<List<SportBreakdownItem>> = _sportBreakdown.asStateFlow()

    // ── Init: start all data flows ────────────────────────────────────────────

    init {
        Log.d("📊 MYSTATS:", "🎯 MyStatsViewModel INIT userId=$userId")
        loadAllData()
    }

    private fun loadAllData() {
        // 1. Core stats: cache-first flow from repository (finite: emits 1-2 times then completes)
        viewModelScope.launch {
            _isLoading.value = true
            repository.getStats(userId).collect { freshStats ->
                Log.d("📊 MYSTATS:", "📥 stats: events=${freshStats.totalEvents} posts=${freshStats.totalPosts} km=${freshStats.totalKm} level=${freshStats.level}")
                _stats.value = freshStats
                _isLoading.value = false
            }
        }

        // 2. Badges: reactive Room flow (infinite — emits every time badges are inserted)
        viewModelScope.launch {
            repository.getBadges(userId).collect { badgeList ->
                Log.d("📊 MYSTATS:", "🏅 badges: ${badgeList.size}")
                _badges.value = badgeList
            }
        }

        // 3. Sync status propagation
        viewModelScope.launch {
            repository.getSyncStatus().collect { status ->
                Log.d("📊 MYSTATS:", "🔄 syncStatus: $status")
                _syncStatus.value = status
                if (status == "SYNCING") _isLoading.value = true
            }
        }

        // 4. Recent runs (for chart)
        viewModelScope.launch {
            repository.getRecentRuns(userId).collect { runs ->
                Log.d("📊 MYSTATS:", "🏃 recentRuns: ${runs.size}")
                _recentRuns.value = runs
            }
        }

        // 5. Active challenges + user progress
        viewModelScope.launch {
            repository.getActiveChallenges(userId).collect { challenges ->
                Log.d("📊 MYSTATS:", "🎯 activeChallenges: ${challenges.size}")
                _activeChallenges.value = challenges
            }
        }

        // 6. Sport breakdown
        viewModelScope.launch {
            repository.getSportBreakdown(userId).collect { breakdown ->
                Log.d("📊 MYSTATS:", "⚽ sportBreakdown: ${breakdown.size} sports")
                _sportBreakdown.value = breakdown
            }
        }
    }

    // ── Public actions ────────────────────────────────────────────────────────

    /**
     * Forces a full re-sync from Firestore, ignoring the 15-min TTL cache.
     * Also refreshes enriched data (runs, challenges, sport breakdown).
     */
    override fun refreshStats(forceSync: Boolean) {
        _isLoading.value = true
        viewModelScope.launch {
            repository.getStats(userId, forceRefresh = true).collect { freshStats ->
                _stats.value = freshStats
                _isLoading.value = false
            }
        }
        // Refresh enriched data in parallel
        viewModelScope.launch { repository.getRecentRuns(userId).collect { _recentRuns.value = it } }
        viewModelScope.launch { repository.getActiveChallenges(userId).collect { _activeChallenges.value = it } }
        viewModelScope.launch { repository.getSportBreakdown(userId).collect { _sportBreakdown.value = it } }
    }

    override fun markBadgesAsViewed() {
        viewModelScope.launch {
            Log.i("MyStatsViewModel", "Badges viewed by user: $userId")
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

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
