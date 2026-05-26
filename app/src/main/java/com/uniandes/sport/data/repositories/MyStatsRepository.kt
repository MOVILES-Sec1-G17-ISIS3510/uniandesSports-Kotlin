package com.uniandes.sport.data.repositories

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.uniandes.sport.data.cache.BadgeArrayMapCache
import com.uniandes.sport.data.database.StatsDatabase
import com.uniandes.sport.data.entities.BadgeEntity
import com.uniandes.sport.data.entities.UserStatsEntity
import com.uniandes.sport.models.ChallengeStats
import com.uniandes.sport.models.EventSummary
import com.uniandes.sport.models.RunDataPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

// ─── Interface ────────────────────────────────────────────────────────────────

interface MyStatsRepositoryInterface {
    fun getStats(userId: String, forceRefresh: Boolean = false): Flow<UserStatsEntity>
    fun getBadges(userId: String): Flow<List<BadgeEntity>>
    fun getSyncStatus(): Flow<String>
    /** Last ≤10 run sessions in chronological order (oldest → newest). */
    fun getRecentRuns(userId: String): Flow<List<RunDataPoint>>
    /** Summary counts (total / completed / inProgress) across all the user's challenges. */
    fun getActiveChallenges(userId: String): Flow<ChallengeStats>
    /** Open-match events the user has joined (up to 20 most recent). */
    fun getJoinedEvents(userId: String): Flow<List<EventSummary>>
}

// ─── Implementation ───────────────────────────────────────────────────────────

/**
 * MyStatsRepository — Cache-first strategy.
 *
 * Data access order:
 *  1. Room DB   (50 ms,  persistent)
 *  2. Firestore (500 ms, source of truth)
 *
 * Bugs fixed in earlier iterations:
 *  1. collect{} on an infinite Room Flow → replaced by firstOrNull()
 *  2. runBlocking{} in calculateStreakDays → now suspend + firstOrNull()
 *  3. Posts query used userId but Firestore stores displayName → fetch first
 *  4. Badges were never computed or persisted → computeAndSaveBadges()
 *  5. getBadges() now returns the reactive Room Flow directly
 */
class MyStatsRepository(
    private val statsDatabase: StatsDatabase,
    private val badgeArrayMapCache: BadgeArrayMapCache = BadgeArrayMapCache()
) : MyStatsRepositoryInterface {

    private val _syncStatus = MutableStateFlow("IDLE")

    override fun getSyncStatus(): Flow<String> = _syncStatus.asStateFlow()

    // ─── Core stats ───────────────────────────────────────────────────────────

    /**
     * Emits cached Room data first (if present), then syncs from Firestore.
     * The flow completes after the second emission (or after the first if cache is fresh).
     */
    override fun getStats(userId: String, forceRefresh: Boolean): Flow<UserStatsEntity> = flow {
        if (userId.isBlank()) {
            Log.w("📊 MYSTATS:", "userId vacío, abortando")
            emit(emptyStats(userId))
            return@flow
        }

        Log.d("📊 MYSTATS:", "🚀 getStats() userId=$userId forceRefresh=$forceRefresh")

        val cachedStats: UserStatsEntity? = try {
            statsDatabase.userStatsDao().getStats(userId).firstOrNull()
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "Error leyendo Room: ${e.message}")
            null
        }

        if (cachedStats != null) {
            Log.d("📊 MYSTATS:", "💾 Room cache: events=${cachedStats.totalEvents} posts=${cachedStats.totalPosts} km=${cachedStats.totalKm}")
            emit(cachedStats)
            val age = System.currentTimeMillis() - cachedStats.lastSyncAt
            if (!forceRefresh && age < 15 * 60 * 1000L) {
                Log.d("📊 MYSTATS:", "✅ Caché fresca (${age / 1000}s), sin sync")
                return@flow
            }
        }

        try {
            _syncStatus.value = "SYNCING"
            Log.d("📊 MYSTATS:", "🔄 Sincronizando desde Firestore...")
            val freshStats = fetchAndCalculateFromFirestore(userId, cachedStats)
            statsDatabase.userStatsDao().insertStats(freshStats)
            computeAndSaveBadges(userId, freshStats)
            Log.d("📊 MYSTATS:", "✅ Firestore sync: events=${freshStats.totalEvents} posts=${freshStats.totalPosts} km=${freshStats.totalKm} level=${freshStats.level}")
            emit(freshStats)
            _syncStatus.value = "IDLE"
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "❌ Error Firestore: ${e.message}", e)
            _syncStatus.value = "ERROR"
            if (cachedStats != null) emit(cachedStats)
            else emit(emptyStats(userId, syncStatus = "ERROR"))
        }
    }

    override fun getBadges(userId: String): Flow<List<BadgeEntity>> =
        statsDatabase.badgeDao().getBadgesForUser(userId)

    // ─── Enriched data ────────────────────────────────────────────────────────

    /** Fetches the last 10 runs, returns them in chronological order for the bar chart. */
    override fun getRecentRuns(userId: String): Flow<List<RunDataPoint>> = flow {
        if (userId.isBlank()) { emit(emptyList()); return@flow }
        try {
            val db = FirebaseFirestore.getInstance()
            val snapshot = db.collection("users")
                .document(userId)
                .collection("runs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .await()

            val runs = snapshot.documents.mapNotNull { doc ->
                try {
                    RunDataPoint(
                        distanceKm = (doc.getDouble("distanceKm") ?: 0.0).toFloat(),
                        timestamp  = doc.getLong("timestamp") ?: 0L,
                        pace       = doc.getString("pace") ?: ""
                    )
                } catch (e: Exception) { null }
            }.reversed()  // oldest first for the chart

            Log.d("📊 MYSTATS:", "🏃 Recent runs: ${runs.size}")
            emit(runs)
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "Error getRecentRuns: ${e.message}")
            emit(emptyList())
        }
    }

    /**
     * Counts all challenges the user has joined (all statuses) and classifies them
     * as completed (progress >= 100) or in-progress (progress < 100).
     */
    override fun getActiveChallenges(userId: String): Flow<ChallengeStats> = flow {
        if (userId.isBlank()) { emit(ChallengeStats(0, 0, 0)); return@flow }
        try {
            val db = FirebaseFirestore.getInstance()
            val snapshot = db.collection("challenges")
                .whereArrayContains("participants", userId)
                .get()
                .await()

            var completed  = 0
            var inProgress = 0
            for (doc in snapshot.documents) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val progressByUser = doc.get("progressByUser") as? Map<String, Any> ?: emptyMap()
                    val userProg: Double = when (val raw = progressByUser[userId]) {
                        is Double -> raw
                        is Long   -> raw.toDouble()
                        is Number -> raw.toDouble()
                        else      -> 0.0
                    }
                    if (userProg >= 100.0) completed++ else inProgress++
                } catch (_: Exception) {}
            }

            val stats = ChallengeStats(total = snapshot.size(), completed = completed, inProgress = inProgress)
            Log.d("📊 MYSTATS:", "🎯 Challenges: total=${stats.total} done=${stats.completed} active=${stats.inProgress}")
            emit(stats)
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "Error getActiveChallenges: ${e.message}")
            emit(ChallengeStats(0, 0, 0))
        }
    }

    /**
     * Returns up to 20 events the user has joined, with title, sport, status, and date.
     * The sport breakdown shown in the UI is derived from this same list in the ViewModel.
     */
    override fun getJoinedEvents(userId: String): Flow<List<EventSummary>> = flow {
        if (userId.isBlank()) { emit(emptyList()); return@flow }
        try {
            val db = FirebaseFirestore.getInstance()

            // One collectionGroup query — same strategy as countUserEvents()
            val memberships = db.collectionGroup("members")
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val eventRefs = memberships.documents
                .filter { doc -> doc.reference.parent.parent?.parent?.id == "events" }
                .mapNotNull { doc -> doc.reference.parent.parent }
                .take(20)

            val events = mutableListOf<EventSummary>()
            for (ref in eventRefs) {
                try {
                    val doc = ref.get().await()
                    events += EventSummary(
                        id          = doc.id,
                        title       = doc.getString("title") ?: "",
                        sport       = doc.getString("sport") ?: "",
                        status      = doc.getString("status") ?: "",
                        scheduledAt = doc.getTimestamp("scheduledAt")?.toDate()?.time
                    )
                } catch (_: Exception) { /* skip inaccessible event */ }
            }

            Log.d("📊 MYSTATS:", "⚽ Joined events: ${events.size}")
            emit(events)
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "Error getJoinedEvents: ${e.message}")
            emit(emptyList())
        }
    }

    // ─── Firestore calculation ────────────────────────────────────────────────

    private suspend fun fetchAndCalculateFromFirestore(
        userId: String,
        localStats: UserStatsEntity?
    ): UserStatsEntity {
        val db = FirebaseFirestore.getInstance()
        val totalEvents    = countUserEvents(db, userId)
        val userDisplayName = fetchUserDisplayName(db, userId)
        val totalPosts     = countUserPosts(db, userDisplayName)
        val totalKm        = countUserKm(db, userId)
        val (level, points) = calculateLevelAndPoints(totalEvents, totalPosts, totalKm)
        val streakDays     = calculateStreakDays(userId)
        val hasData        = totalEvents > 0 || totalPosts > 0 || totalKm > 0f

        return UserStatsEntity(
            userId        = userId,
            totalKm       = totalKm,
            totalEvents   = totalEvents,
            totalPosts    = totalPosts,
            totalMessages = 0,
            level         = level,
            points        = points,
            streakDays    = streakDays,
            lastSyncAt    = System.currentTimeMillis(),
            syncStatus    = "IDLE",
            hasRealData   = hasData
        )
    }

    private suspend fun countUserEvents(db: FirebaseFirestore, userId: String): Int {
        return try {
            val memberships = db.collectionGroup("members")
                .whereEqualTo("userId", userId)
                .get().await()
            val count = memberships.documents.count { doc ->
                doc.reference.parent.parent?.parent?.id == "events"
            }
            Log.d("📊 MYSTATS:", "🎯 Eventos: $count (collectionGroup)")
            count
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "Error countUserEvents: ${e.message}")
            countUserEventsFallback(db, userId)
        }
    }

    private suspend fun countUserEventsFallback(db: FirebaseFirestore, userId: String): Int {
        return try {
            val eventsSnapshot = db.collection("events").get().await()
            var count = 0
            for (eventDoc in eventsSnapshot.documents) {
                try {
                    val memberDoc = eventDoc.reference.collection("members").document(userId).get().await()
                    if (memberDoc.exists()) count++
                } catch (_: Exception) {}
            }
            Log.d("📊 MYSTATS:", "🎯 Eventos (fallback N+1): $count")
            count
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "Error countUserEventsFallback: ${e.message}")
            0
        }
    }

    private suspend fun fetchUserDisplayName(db: FirebaseFirestore, userId: String): String {
        return try {
            val userDoc = db.collection("users").document(userId).get().await()
            val name = userDoc.getString("fullName")?.takeIf { it.isNotBlank() }
                ?: userDoc.getString("email")?.takeIf { it.isNotBlank() }
                ?: ""
            Log.d("📊 MYSTATS:", "👤 DisplayName: '$name'")
            name
        } catch (e: Exception) { "" }
    }

    private suspend fun countUserPosts(db: FirebaseFirestore, displayName: String): Int {
        if (displayName.isBlank()) return 0
        return try {
            val communities = db.collection("communities").get().await()
            var total = 0
            for (community in communities.documents) {
                val posts = community.reference.collection("posts")
                    .whereEqualTo("author", displayName).get().await()
                total += posts.size()
            }
            Log.d("📊 MYSTATS:", "🎯 Posts: $total")
            total
        } catch (e: Exception) { 0 }
    }

    private suspend fun countUserKm(db: FirebaseFirestore, userId: String): Float {
        return try {
            val runs = db.collection("users").document(userId).collection("runs").get().await()
            val km   = runs.documents.fold(0f) { acc, doc -> acc + (doc.getDouble("distanceKm") ?: 0.0).toFloat() }
            Log.d("📊 MYSTATS:", "🎯 Km: $km")
            km
        } catch (e: Exception) { 0f }
    }

    // ─── Level, streak, badges ────────────────────────────────────────────────

    private fun calculateLevelAndPoints(events: Int, posts: Int, km: Float): Pair<Int, Int> {
        val points = (events * 10) + (posts * 5) + km.toInt()
        val level  = (points / 100) + 1
        return Pair(level, points)
    }

    private suspend fun calculateStreakDays(userId: String): Int {
        return try {
            val since    = System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000)
            val todayStart = System.currentTimeMillis() - (System.currentTimeMillis() % (24L * 60 * 60 * 1000))
            val activities = statsDatabase.activityLogDao()
                .getActivitiesInRange(userId, since, System.currentTimeMillis())
                .firstOrNull() ?: emptyList()

            if (activities.isEmpty()) return 0
            val activeDays = activities
                .map { it.activityDate - (it.activityDate % (24L * 60 * 60 * 1000)) }
                .toSortedSet(compareByDescending { it })

            var streak  = 0
            var current = todayStart
            for (i in 0..365) {
                if (current in activeDays) { streak++; current -= 24L * 60 * 60 * 1000 }
                else break
            }
            streak
        } catch (e: Exception) { 0 }
    }

    private suspend fun computeAndSaveBadges(userId: String, stats: UserStatsEntity) {
        try {
            val now    = System.currentTimeMillis()
            val earned = mutableListOf<BadgeEntity>()

            if (stats.totalEvents >= 1)  earned += BadgeEntity("event_first_$userId",    userId, "First Match",     "Joined your first event",       "⚽", "COMMON",    now)
            if (stats.totalEvents >= 5)  earned += BadgeEntity("event_team_$userId",     userId, "Team Player",     "Joined 5 events",               "🤝", "RARE",      now)
            if (stats.totalEvents >= 20) earned += BadgeEntity("event_veteran_$userId",  userId, "Sports Veteran",  "Joined 20 events",              "🏆", "EPIC",      now)
            if (stats.totalPosts  >= 1)  earned += BadgeEntity("post_first_$userId",     userId, "First Post",      "Made your first community post","📝", "COMMON",    now)
            if (stats.totalPosts  >= 10) earned += BadgeEntity("post_voice_$userId",     userId, "Community Voice", "Made 10 posts",                 "📣", "RARE",      now)
            if (stats.totalPosts  >= 50) earned += BadgeEntity("post_influencer_$userId",userId, "Influencer",      "Made 50 posts",                 "⭐", "EPIC",      now)
            if (stats.totalKm     >= 1f) earned += BadgeEntity("run_first_$userId",      userId, "First Run",       "Ran your first km",             "🏃", "COMMON",    now)
            if (stats.totalKm     >= 10f)earned += BadgeEntity("run_10k_$userId",        userId, "10K Club",        "Ran 10 km total",               "👟", "RARE",      now)
            if (stats.totalKm     >= 42f)earned += BadgeEntity("run_marathon_$userId",   userId, "Marathon Hero",   "Ran 42 km total",               "🥇", "EPIC",      now)
            if (stats.level       >= 3)  earned += BadgeEntity("level_rising_$userId",   userId, "Rising Star",     "Reached Level 3",               "🌟", "RARE",      now)
            if (stats.level       >= 5)  earned += BadgeEntity("level_champion_$userId", userId, "Champion",        "Reached Level 5",               "🏅", "EPIC",      now)
            if (stats.level       >= 10) earned += BadgeEntity("level_legend_$userId",   userId, "Legend",          "Reached Level 10",              "👑", "LEGENDARY", now)

            if (earned.isNotEmpty()) {
                statsDatabase.badgeDao().insertBadges(earned)
                badgeArrayMapCache.loadBadges(earned)
                Log.d("📊 MYSTATS:", "🏅 ${earned.size} badges guardados")
            }
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "Error computeAndSaveBadges: ${e.message}")
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun emptyStats(userId: String, syncStatus: String = "IDLE") = UserStatsEntity(
        userId = userId, totalKm = 0f, totalEvents = 0, totalPosts = 0,
        totalMessages = 0, level = 1, points = 0, streakDays = 0,
        lastSyncAt = System.currentTimeMillis(), syncStatus = syncStatus, hasRealData = false
    )

    fun printCacheStats() {
        Log.d("CACHE_STATS", badgeArrayMapCache.getStats())
    }
}
