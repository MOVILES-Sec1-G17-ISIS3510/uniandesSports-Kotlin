package com.uniandes.sport.viewmodels.stats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uniandes.sport.data.cache.BadgeArrayMapCache
import com.uniandes.sport.data.entities.BadgeEntity
import com.uniandes.sport.data.entities.UserStatsEntity
import com.uniandes.sport.data.repositories.MyStatsRepository
import com.uniandes.sport.models.ChallengeStats
import com.uniandes.sport.models.EventSummary
import com.uniandes.sport.models.RunDataPoint
import com.uniandes.sport.models.SportBreakdownItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─── Interface ────────────────────────────────────────────────────────────────

interface MyStatsViewModelInterface {
    // Core stats
    val stats:          StateFlow<UserStatsEntity>
    val badges:         StateFlow<List<BadgeEntity>>
    val syncStatus:     StateFlow<String>
    val isLoading:      StateFlow<Boolean>

    // Enriched data
    val recentRuns:     StateFlow<List<RunDataPoint>>
    val challengeStats: StateFlow<ChallengeStats>
    val joinedEvents:   StateFlow<List<EventSummary>>
    /** Derived from joinedEvents — grouped and sorted by count. */
    val sportBreakdown: StateFlow<List<SportBreakdownItem>>

    fun refreshStats(forceSync: Boolean = false)
    fun markBadgesAsViewed()
}

// ─── Implementation ───────────────────────────────────────────────────────────

class MyStatsViewModel(
    private val repository: MyStatsRepository,
    private val userId: String
) : ViewModel(), MyStatsViewModelInterface {

    // ── Core ──────────────────────────────────────────────────────────────────

    private val _stats = MutableStateFlow(
        UserStatsEntity(userId = userId, totalKm = 0f, totalEvents = 0, totalPosts = 0,
            totalMessages = 0, level = 1, points = 0, streakDays = 0,
            lastSyncAt = 0L, syncStatus = "IDLE", hasRealData = false)
    )
    override val stats:      StateFlow<UserStatsEntity> = _stats.asStateFlow()

    private val _badges = MutableStateFlow<List<BadgeEntity>>(emptyList())
    override val badges: StateFlow<List<BadgeEntity>>  = _badges.asStateFlow()

    private val _syncStatus = MutableStateFlow("IDLE")
    override val syncStatus: StateFlow<String>         = _syncStatus.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading:  StateFlow<Boolean>        = _isLoading.asStateFlow()

    // ── Enriched ─────────────────────────────────────────────────────────────

    private val _recentRuns = MutableStateFlow<List<RunDataPoint>>(emptyList())
    override val recentRuns: StateFlow<List<RunDataPoint>> = _recentRuns.asStateFlow()

    private val _challengeStats = MutableStateFlow(ChallengeStats(0, 0, 0))
    override val challengeStats: StateFlow<ChallengeStats> = _challengeStats.asStateFlow()

    private val _joinedEvents = MutableStateFlow<List<EventSummary>>(emptyList())
    override val joinedEvents: StateFlow<List<EventSummary>> = _joinedEvents.asStateFlow()

    // Derived: sport breakdown computed reactively whenever joinedEvents changes
    private val _sportBreakdown = MutableStateFlow<List<SportBreakdownItem>>(emptyList())
    override val sportBreakdown: StateFlow<List<SportBreakdownItem>> = _sportBreakdown.asStateFlow()

    // ── Init ─────────────────────────────────────────────────────────────────

    init {
        Log.d("📊 MYSTATS:", "🎯 MyStatsViewModel INIT userId=$userId")
        loadAllData()
    }

    private fun loadAllData() {
        // 1. Core stats — finite flow (cache then Firestore, then completes)
        viewModelScope.launch {
            _isLoading.value = true
            repository.getStats(userId).collect { s ->
                Log.d("📊 MYSTATS:", "📥 stats: events=${s.totalEvents} posts=${s.totalPosts} km=${s.totalKm} level=${s.level}")
                _stats.value = s
                _isLoading.value = false
            }
        }

        // 2. Badges — infinite reactive Room Flow
        viewModelScope.launch {
            repository.getBadges(userId).collect { list ->
                Log.d("📊 MYSTATS:", "🏅 badges: ${list.size}")
                _badges.value = list
            }
        }

        // 3. Sync status
        viewModelScope.launch {
            repository.getSyncStatus().collect { status ->
                _syncStatus.value = status
                if (status == "SYNCING") _isLoading.value = true
            }
        }

        // 4. Recent runs (finite — emits once)
        viewModelScope.launch {
            repository.getRecentRuns(userId).collect {
                Log.d("📊 MYSTATS:", "🏃 recentRuns: ${it.size}")
                _recentRuns.value = it
            }
        }

        // 5. Challenge summary (finite — emits once)
        viewModelScope.launch {
            repository.getActiveChallenges(userId).collect {
                Log.d("📊 MYSTATS:", "🎯 challengeStats: total=${it.total}")
                _challengeStats.value = it
            }
        }

        // 6. Joined events (finite — emits once); derive sport breakdown reactively
        viewModelScope.launch {
            repository.getJoinedEvents(userId).collect { events ->
                Log.d("📊 MYSTATS:", "⚽ joinedEvents: ${events.size}")
                _joinedEvents.value = events
                _sportBreakdown.value = events
                    .groupBy { it.sport.ifBlank { "Other" } }
                    .map { (sport, list) -> SportBreakdownItem(sport, list.size) }
                    .sortedByDescending { it.count }
            }
        }
    }

    // ── Public actions ────────────────────────────────────────────────────────

    override fun refreshStats(forceSync: Boolean) {
        _isLoading.value = true
        viewModelScope.launch {
            repository.getStats(userId, forceRefresh = true).collect {
                _stats.value = it
                _isLoading.value = false
            }
        }
        viewModelScope.launch { repository.getRecentRuns(userId).collect   { _recentRuns.value     = it } }
        viewModelScope.launch { repository.getActiveChallenges(userId).collect { _challengeStats.value = it } }
        viewModelScope.launch {
            repository.getJoinedEvents(userId).collect { events ->
                _joinedEvents.value  = events
                _sportBreakdown.value = events
                    .groupBy { it.sport.ifBlank { "Other" } }
                    .map { (sport, list) -> SportBreakdownItem(sport, list.size) }
                    .sortedByDescending { it.count }
            }
        }
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
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    MyStatsViewModel(repository, userId) as T
            }
    }
}
