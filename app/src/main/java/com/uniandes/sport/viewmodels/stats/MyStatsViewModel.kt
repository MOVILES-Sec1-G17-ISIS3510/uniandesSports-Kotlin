package com.uniandes.sport.viewmodels.stats

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.uniandes.sport.ai.AiConstants
import com.uniandes.sport.ai.OpenAiMessage
import com.uniandes.sport.ai.OpenAiRequest
import com.uniandes.sport.ai.OpenAiReviewApi
import com.uniandes.sport.data.cache.BadgeArrayMapCache
import com.uniandes.sport.data.entities.BadgeEntity
import com.uniandes.sport.data.entities.UserStatsEntity
import com.uniandes.sport.data.local.PendingStatsInsightPayload
import com.uniandes.sport.data.local.PendingStatsInsightStore
import com.uniandes.sport.data.local.StatsInsightResultStore
import com.uniandes.sport.data.repositories.MyStatsRepository
import com.uniandes.sport.models.ChallengeStats
import com.uniandes.sport.models.EventSummary
import com.uniandes.sport.models.RunDataPoint
import com.uniandes.sport.models.SportBreakdownItem
import com.uniandes.sport.workers.StatsInsightSyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// ─── InsightState ─────────────────────────────────────────────────────────────

sealed class InsightState {
    object Idle    : InsightState()                              // button ready to press
    object Loading : InsightState()                              // online, waiting for OpenAI
    object Queued  : InsightState()                              // offline, request stored
    /** [generatedAt] is the Unix timestamp (ms) when the result was produced. */
    data class Ready(val text: String, val generatedAt: Long) : InsightState()
    data class Error(val message: String) : InsightState()
}

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

    val isOnline:     StateFlow<Boolean>
    val insightState: StateFlow<InsightState>

    fun refreshStats(forceSync: Boolean = false)
    fun requestInsight()
    fun dismissInsight()
    /** Called by the screen every time it enters composition to pick up any persisted result. */
    fun loadStoredInsight()
    fun markBadgesAsViewed()
}

// ─── Implementation ───────────────────────────────────────────────────────────

class MyStatsViewModel(
    private val repository: MyStatsRepository,
    private val userId: String,
    private val connectivityFlow: Flow<Boolean> = flowOf(true)
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

    private val _isOnline = MutableStateFlow(true)
    override val isOnline: StateFlow<Boolean>          = _isOnline.asStateFlow()

    private val _insightState = MutableStateFlow<InsightState>(InsightState.Idle)
    override val insightState: StateFlow<InsightState> = _insightState.asStateFlow()

    // Application context — safe to hold; same pattern as BookClassViewModel
    private val appContext: Context?
        get() = try { FirebaseApp.getInstance().applicationContext } catch (_: Exception) { null }

    // Lazy Retrofit for insight calls (shares pattern with OpenAiAnalyzerStrategy)
    private val openAiApi: OpenAiReviewApi by lazy {
        Retrofit.Builder()
            .baseUrl(AiConstants.OPENAI_BASE_URL)
            .client(
                okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAiReviewApi::class.java)
    }

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
        checkForStoredInsightResult()
    }

    /**
     * Loads any persisted insight on VM init.
     * Result is NOT cleared — it stays until the user explicitly regenerates.
     */
    private fun checkForStoredInsightResult() {
        val ctx    = appContext ?: return
        val result = StatsInsightResultStore.get(ctx) ?: return
        if (result.isNotBlank()) {
            val ts = StatsInsightResultStore.getGeneratedAt(ctx)
            Log.d("📊 MYSTATS:", "🤖 Stored insight found (generated ${ts}) — restoring")
            _insightState.value = InsightState.Ready(result, ts)
        }
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

        // 7. Connectivity — exposes isOnline and auto-retries when network is restored
        viewModelScope.launch {
            var wasOnline: Boolean? = null
            connectivityFlow.collect { isNowOnline ->
                _isOnline.value = isNowOnline
                Log.d("📊 MYSTATS:", "📶 connectivity=$isNowOnline (was=$wasOnline)")

                // Detect offline → online transition
                if (wasOnline == false && isNowOnline) {
                    val cacheAgeMs = System.currentTimeMillis() - _stats.value.lastSyncAt
                    val cacheIsStale = cacheAgeMs > 15 * 60 * 1000L || !_stats.value.hasRealData
                    if (cacheIsStale) {
                        Log.i("📊 MYSTATS:", "📶 Red restaurada, caché viejo → auto-sync")
                        refreshStats(forceSync = true)
                    } else {
                        Log.d("📊 MYSTATS:", "📶 Red restaurada, caché fresco → sin sync")
                    }
                }
                wasOnline = isNowOnline
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

    /**
     * "How am I doing?" — online path calls OpenAI immediately and persists the
     * result; offline path stores the prompt and schedules a WorkManager job.
     * Calling this again clears the previous result before requesting a new one.
     */
    override fun requestInsight() {
        val current = _insightState.value
        if (current is InsightState.Loading || current is InsightState.Queued) return

        val ctx    = appContext ?: run {
            _insightState.value = InsightState.Error("Context unavailable — try again.")
            return
        }

        // Clear previous result so a fresh one is stored
        StatsInsightResultStore.clear(ctx)

        val prompt = buildInsightPrompt()

        if (_isOnline.value) {
            // ── Online: call OpenAI directly ──────────────────────────────────
            viewModelScope.launch {
                _insightState.value = InsightState.Loading
                Log.d("📊 MYSTATS:", "🤖 Requesting insight online…")
                try {
                    val response = openAiApi.analyzeReviewWithOpenAi(
                        authHeader = "Bearer ${AiConstants.OPENAI_API_KEY}",
                        request    = OpenAiRequest(
                            model    = "gpt-4o-mini",
                            messages = listOf(
                                OpenAiMessage("system",
                                    "You are an expert, motivational sports coach providing personalized progress feedback."),
                                OpenAiMessage("user", prompt)
                            ),
                            maxTokens = 500
                        )
                    )
                    val text = response.body()?.choices?.firstOrNull()?.message?.content
                        ?.toString()?.trim()
                    if (!text.isNullOrBlank()) {
                        Log.d("📊 MYSTATS:", "🤖 Insight received (${text.length} chars)")
                        val now = System.currentTimeMillis()
                        StatsInsightResultStore.save(ctx, text, now)   // persist
                        _insightState.value = InsightState.Ready(text, now)
                    } else {
                        Log.e("📊 MYSTATS:", "🤖 Empty OpenAI response")
                        _insightState.value = InsightState.Error("AI didn't return a response. Try again.")
                    }
                } catch (e: Exception) {
                    Log.e("📊 MYSTATS:", "🤖 OpenAI error: ${e.message}")
                    _insightState.value = InsightState.Error("Connection error. Try again later.")
                }
            }
        } else {
            // ── Offline: queue prompt + schedule worker ───────────────────────
            PendingStatsInsightStore.enqueue(
                ctx,
                PendingStatsInsightPayload(userId = userId, promptText = prompt)
            )
            val work = OneTimeWorkRequestBuilder<StatsInsightSyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(ctx).enqueue(work)
            _insightState.value = InsightState.Queued
            Log.i("📊 MYSTATS:", "🤖 Insight queued — worker scheduled for when network returns")
        }
    }

    /** Clears the persisted result and resets to Idle (user explicitly deletes insight). */
    override fun dismissInsight() {
        appContext?.let { StatsInsightResultStore.clear(it) }
        _insightState.value = InsightState.Idle
    }

    /**
     * Called every time the screen enters composition (via LaunchedEffect).
     * If there is a persisted result and we are not already in Loading/Queued state,
     * restore it — this handles the notification-tap path where the ViewModel is
     * reused (launchSingleTop) and init does not run again.
     */
    override fun loadStoredInsight() {
        val current = _insightState.value
        if (current is InsightState.Loading || current is InsightState.Queued) return
        // Already showing a result — no need to re-read from disk
        if (current is InsightState.Ready) return
        checkForStoredInsightResult()
    }

    // Builds the prompt from current StateFlow values — called at button press time.
    private fun buildInsightPrompt(): String {
        val s  = _stats.value
        val cs = _challengeStats.value
        val sb = _sportBreakdown.value.take(3).joinToString(", ") { "${it.sport} (${it.count})" }
        val badgesEarned = _badges.value.size

        return """
            You are a motivational sports coach AI for UniAndes Sports, a university sports platform.
            Analyze this student-athlete's activity stats and provide personalized feedback.

            📊 ACTIVITY STATS:
            - Level: ${s.level} | Total points: ${s.points}
            - Activity streak: ${s.streakDays} consecutive days
            - Events joined: ${s.totalEvents}
            - Community posts: ${s.totalPosts}
            - Total km run: ${"%.1f".format(s.totalKm)} km

            🏆 CHALLENGES:
            - Total joined: ${cs.total} | Completed: ${cs.completed} | In progress: ${cs.inProgress}

            ⚽ TOP SPORTS: ${sb.ifBlank { "None recorded yet" }}
            🏅 BADGES: $badgesEarned / 12 unlocked

            Please provide:
            1. A brief honest assessment of overall progress (2-3 sentences)
            2. Their single strongest area (1 sentence)
            3. Top 2-3 specific, actionable tips to improve
            4. An encouraging closing message

            Tone: motivational and honest. Max 200 words. Use emojis sparingly.
        """.trimIndent()
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        fun provideFactory(
            repository: MyStatsRepository,
            userId: String,
            connectivityFlow: Flow<Boolean> = flowOf(true)
        ): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    MyStatsViewModel(repository, userId, connectivityFlow) as T
            }
    }
}
