package com.uniandes.sport.data.repositories

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.uniandes.sport.data.cache.BadgeArrayMapCache
import com.uniandes.sport.data.database.StatsDatabase
import com.uniandes.sport.data.entities.BadgeEntity
import com.uniandes.sport.data.entities.UserStatsEntity
import com.uniandes.sport.models.ActiveChallengeData
import com.uniandes.sport.models.RunDataPoint
import com.uniandes.sport.models.SportBreakdownItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

/**
 * MyStatsRepositoryInterface — Contrato para acceso a datos de estadísticas.
 */
interface MyStatsRepositoryInterface {
    fun getStats(userId: String, forceRefresh: Boolean = false): Flow<UserStatsEntity>
    fun getBadges(userId: String): Flow<List<BadgeEntity>>
    fun getSyncStatus(): Flow<String>
    /** Last ≤10 run sessions in chronological order (oldest → newest). */
    fun getRecentRuns(userId: String): Flow<List<RunDataPoint>>
    /** Active challenges the user is participating in, with their personal progress. */
    fun getActiveChallenges(userId: String): Flow<List<ActiveChallengeData>>
    /** Events the user joined, grouped and counted by sport. */
    fun getSportBreakdown(userId: String): Flow<List<SportBreakdownItem>>
}

/**
 * MyStatsRepository — Cache-first strategy.
 *
 * BUGS CORREGIDOS:
 *  1. collect {} en Flow de Room nunca terminaba → reemplazado por firstOrNull()
 *  2. runBlocking { Flow.collect {} } en calculateStreakDays → ahora suspend + firstOrNull()
 *  3. Posts query usaba userId pero Firestore guarda displayName → ahora fetchea fullName primero
 *  4. Badges nunca se calculaban ni guardaban → nuevo computeAndSaveBadges()
 *  5. getBadges() ahora retorna el flow reactivo de Room para que se actualice al insertar badges
 */
class MyStatsRepository(
    private val statsDatabase: StatsDatabase,
    private val badgeArrayMapCache: BadgeArrayMapCache = BadgeArrayMapCache()
) : MyStatsRepositoryInterface {

    private val _syncStatus = MutableStateFlow("IDLE")

    override fun getSyncStatus(): Flow<String> = _syncStatus.asStateFlow()

    // ─── getStats ─────────────────────────────────────────────────────────────

    /**
     * Emite datos cacheados de Room primero (si existen), luego sincroniza con
     * Firestore y emite datos frescos. El flow completa después de la segunda emisión.
     *
     * BUG CORREGIDO: antes usaba .collect{} sobre el Room Flow (infinito) que
     * bloqueaba para siempre. Ahora usa .firstOrNull() que toma el primer valor
     * y cancela la suscripción inmediatamente.
     */
    override fun getStats(userId: String, forceRefresh: Boolean): Flow<UserStatsEntity> = flow {
        if (userId.isBlank()) {
            Log.w("📊 MYSTATS:", "userId vacío, abortando")
            emit(emptyStats(userId))
            return@flow
        }

        Log.d("📊 MYSTATS:", "🚀 getStats() userId=$userId forceRefresh=$forceRefresh")

        // Paso 1: Leer caché de Room (firstOrNull = toma primer valor y cancela el flow)
        val cachedStats: UserStatsEntity? = try {
            statsDatabase.userStatsDao().getStats(userId).firstOrNull()
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "Error leyendo Room: ${e.message}")
            null
        }

        if (cachedStats != null) {
            Log.d("📊 MYSTATS:", "💾 Room cache: events=${cachedStats.totalEvents} posts=${cachedStats.totalPosts} km=${cachedStats.totalKm}")
            emit(cachedStats)

            // Si la caché está fresca (TTL de 15 min), no sincronizar
            val age = System.currentTimeMillis() - cachedStats.lastSyncAt
            if (!forceRefresh && age < 15 * 60 * 1000L) {
                Log.d("📊 MYSTATS:", "✅ Caché fresca (${age / 1000}s), sin sync")
                return@flow
            }
        }

        // Paso 2: Sincronizar desde Firestore
        try {
            _syncStatus.value = "SYNCING"
            Log.d("📊 MYSTATS:", "🔄 Sincronizando desde Firestore...")

            val freshStats = fetchAndCalculateFromFirestore(userId, cachedStats)
            statsDatabase.userStatsDao().insertStats(freshStats)

            // Calcular y guardar badges basados en los stats recién obtenidos
            computeAndSaveBadges(userId, freshStats)

            Log.d("📊 MYSTATS:", "✅ Firestore sync: events=${freshStats.totalEvents} posts=${freshStats.totalPosts} km=${freshStats.totalKm} level=${freshStats.level}")
            emit(freshStats)
            _syncStatus.value = "IDLE"

        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "❌ Error Firestore: ${e.message}", e)
            _syncStatus.value = "ERROR"
            // Fallback: re-emitir caché si existe
            if (cachedStats != null) {
                emit(cachedStats)
            } else {
                emit(emptyStats(userId, syncStatus = "ERROR"))
            }
        }
    }

    // ─── getBadges ────────────────────────────────────────────────────────────

    /**
     * Retorna el Flow reactivo de Room directamente.
     *
     * BUG CORREGIDO: antes usaba collect{} dentro de un flow{} builder (bloqueante).
     * Ahora retorna el Room Flow directamente → Compose reacciona automáticamente
     * cuando computeAndSaveBadges() inserta nuevos badges.
     */
    override fun getBadges(userId: String): Flow<List<BadgeEntity>> {
        return statsDatabase.badgeDao().getBadgesForUser(userId)
    }

    // ─── Cálculo de stats desde Firestore ────────────────────────────────────

    /**
     * Obtiene datos reales del usuario desde Firestore y calcula sus estadísticas.
     *
     * BUGS CORREGIDOS:
     *  - Posts: antes usaba whereEqualTo("author", userId) pero Firestore guarda
     *    el displayName, no el UID. Ahora primero obtiene el fullName del usuario.
     *  - Eventos: usa collectionGroup("members") para una sola query eficiente.
     *  - calculateStreakDays: ahora es suspend y usa firstOrNull() en lugar de runBlocking.
     */
    private suspend fun fetchAndCalculateFromFirestore(
        userId: String,
        localStats: UserStatsEntity?
    ): UserStatsEntity {
        val db = FirebaseFirestore.getInstance()

        // ── 1. Contar eventos donde el usuario es miembro ──────────────────
        val totalEvents = countUserEvents(db, userId)

        // ── 2. Obtener displayName del usuario para buscar sus posts ────────
        val userDisplayName = fetchUserDisplayName(db, userId)

        // ── 3. Contar posts del usuario en todas las comunidades ─────────────
        val totalPosts = countUserPosts(db, userDisplayName)

        // ── 4. Sumar km de las sesiones de running ──────────────────────────
        val totalKm = countUserKm(db, userId)

        // ── 5. Calcular level y puntos ──────────────────────────────────────
        val (level, points) = calculateLevelAndPoints(totalEvents, totalPosts, totalKm)

        // ── 6. Calcular streak (BUG CORREGIDO: ahora suspend + firstOrNull) ─
        val streakDays = calculateStreakDays(userId)

        val hasData = totalEvents > 0 || totalPosts > 0 || totalKm > 0f

        return UserStatsEntity(
            userId = userId,
            totalKm = totalKm,
            totalEvents = totalEvents,
            totalPosts = totalPosts,
            totalMessages = 0,
            level = level,
            points = points,
            streakDays = streakDays,
            lastSyncAt = System.currentTimeMillis(),
            syncStatus = "IDLE",
            hasRealData = hasData
        )
    }

    /** Cuenta los eventos donde el usuario es miembro usando collectionGroup (1 query). */
    private suspend fun countUserEvents(db: FirebaseFirestore, userId: String): Int {
        return try {
            // collectionGroup("members") busca en TODAS las subcolecciones "members"
            // whereEqualTo("userId", userId) filtra los documentos del usuario
            val memberships = db.collectionGroup("members")
                .whereEqualTo("userId", userId)
                .get()
                .await()

            // Filtrar solo documentos cuyo abuelo es la colección "events"
            // Ruta: events/{eventId}/members/{userId}
            val count = memberships.documents.count { doc ->
                doc.reference.parent.parent?.parent?.id == "events"
            }
            Log.d("📊 MYSTATS:", "🎯 Eventos: $count (collectionGroup)")
            count
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "Error countUserEvents con collectionGroup: ${e.message}")
            // Fallback N+1 si collectionGroup falla (puede necesitar índice)
            countUserEventsFallback(db, userId)
        }
    }

    /** Fallback N+1 para contar eventos si collectionGroup no está disponible. */
    private suspend fun countUserEventsFallback(db: FirebaseFirestore, userId: String): Int {
        return try {
            val eventsSnapshot = db.collection("events").get().await()
            var count = 0
            for (eventDoc in eventsSnapshot.documents) {
                try {
                    val memberDoc = eventDoc.reference
                        .collection("members")
                        .document(userId)
                        .get()
                        .await()
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

    /**
     * Obtiene el fullName (o email) del usuario desde Firestore para luego
     * buscar sus posts por nombre de autor.
     *
     * BUG ORIGINAL: el post almacena "author" = displayName (no el UID).
     * Por eso la query anterior whereEqualTo("author", userId) nunca encontraba nada.
     */
    private suspend fun fetchUserDisplayName(db: FirebaseFirestore, userId: String): String {
        return try {
            val userDoc = db.collection("users").document(userId).get().await()
            val fullName = userDoc.getString("fullName")?.takeIf { it.isNotBlank() }
            val email = userDoc.getString("email")?.takeIf { it.isNotBlank() }
            val name = fullName ?: email ?: ""
            Log.d("📊 MYSTATS:", "👤 DisplayName para posts: '$name'")
            name
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "Error fetchUserDisplayName: ${e.message}")
            ""
        }
    }

    /**
     * Cuenta posts donde "author" == displayName del usuario.
     * Itera por comunidades (la estructura de Firestore no permite collectionGroup
     * sin índice compuesto para este caso).
     */
    private suspend fun countUserPosts(db: FirebaseFirestore, displayName: String): Int {
        if (displayName.isBlank()) {
            Log.w("📊 MYSTATS:", "⚠️ displayName vacío, no se pueden contar posts")
            return 0
        }
        return try {
            val communitiesSnapshot = db.collection("communities").get().await()
            var total = 0
            for (communityDoc in communitiesSnapshot.documents) {
                val postsSnapshot = communityDoc.reference
                    .collection("posts")
                    .whereEqualTo("author", displayName)
                    .get()
                    .await()
                total += postsSnapshot.size()
            }
            Log.d("📊 MYSTATS:", "🎯 Posts: $total (author='$displayName')")
            total
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "Error countUserPosts: ${e.message}")
            0
        }
    }

    /** Suma los km totales de las sesiones de running del usuario. */
    private suspend fun countUserKm(db: FirebaseFirestore, userId: String): Float {
        return try {
            val runsSnapshot = db.collection("users")
                .document(userId)
                .collection("runs")
                .get()
                .await()

            val km = runsSnapshot.documents.fold(0f) { acc, doc ->
                acc + (doc.getDouble("distanceKm") ?: 0.0).toFloat()
            }
            Log.d("📊 MYSTATS:", "🎯 Km totales: $km (${runsSnapshot.size()} runs)")
            km
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "Error countUserKm: ${e.message}")
            0f
        }
    }

    // ─── Cálculo de level, puntos y streak ──────────────────────────────────

    /**
     * Fórmula:
     *   points = (events × 10) + (posts × 5) + km.toInt()
     *   level  = (points / 100) + 1
     */
    private fun calculateLevelAndPoints(events: Int, posts: Int, km: Float): Pair<Int, Int> {
        val points = (events * 10) + (posts * 5) + km.toInt()
        val level = (points / 100) + 1
        return Pair(level, points)
    }

    /**
     * BUG CORREGIDO: antes usaba runBlocking { Flow.collect {} } que bloqueaba para siempre.
     * Ahora es suspend y usa firstOrNull() → cancela el flow tras la primera emisión.
     */
    private suspend fun calculateStreakDays(userId: String): Int {
        return try {
            val since = System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000)
            val todayStart = System.currentTimeMillis() -
                    (System.currentTimeMillis() % (24L * 60 * 60 * 1000))

            val activities = statsDatabase.activityLogDao()
                .getActivitiesInRange(userId, since, System.currentTimeMillis())
                .firstOrNull() ?: emptyList()

            if (activities.isEmpty()) return 0

            val activeDays = activities
                .map { it.activityDate - (it.activityDate % (24L * 60 * 60 * 1000)) }
                .toSortedSet(compareByDescending { it })

            var streak = 0
            var current = todayStart
            for (i in 0..365) {
                if (current in activeDays) { streak++; current -= 24L * 60 * 60 * 1000 }
                else break
            }
            streak
        } catch (e: Exception) {
            Log.w("📊 MYSTATS:", "Error calculateStreakDays: ${e.message}")
            0
        }
    }

    // ─── Cálculo y almacenamiento de badges ──────────────────────────────────

    /**
     * Calcula qué badges ha ganado el usuario según sus stats y los guarda en Room.
     *
     * BUG CORREGIDO: antes no existía ningún código que calculara o guardara badges.
     * Al insertar en Room, el Flow reactivo de getBadges() emite automáticamente
     * y el ViewModel actualiza la UI sin necesidad de acción adicional.
     */
    private suspend fun computeAndSaveBadges(userId: String, stats: UserStatsEntity) {
        try {
            val now = System.currentTimeMillis()
            val earned = mutableListOf<BadgeEntity>()

            // Badges por eventos
            if (stats.totalEvents >= 1)
                earned += BadgeEntity("event_first_$userId", userId, "First Match",
                    "Joined your first event", "⚽", "COMMON", now)
            if (stats.totalEvents >= 5)
                earned += BadgeEntity("event_team_$userId", userId, "Team Player",
                    "Joined 5 events", "🤝", "RARE", now)
            if (stats.totalEvents >= 20)
                earned += BadgeEntity("event_veteran_$userId", userId, "Sports Veteran",
                    "Joined 20 events", "🏆", "EPIC", now)

            // Badges por posts
            if (stats.totalPosts >= 1)
                earned += BadgeEntity("post_first_$userId", userId, "First Post",
                    "Made your first community post", "📝", "COMMON", now)
            if (stats.totalPosts >= 10)
                earned += BadgeEntity("post_voice_$userId", userId, "Community Voice",
                    "Made 10 posts", "📣", "RARE", now)
            if (stats.totalPosts >= 50)
                earned += BadgeEntity("post_influencer_$userId", userId, "Influencer",
                    "Made 50 posts", "⭐", "EPIC", now)

            // Badges por running
            if (stats.totalKm >= 1f)
                earned += BadgeEntity("run_first_$userId", userId, "First Run",
                    "Ran your first km", "🏃", "COMMON", now)
            if (stats.totalKm >= 10f)
                earned += BadgeEntity("run_10k_$userId", userId, "10K Club",
                    "Ran 10 km total", "👟", "RARE", now)
            if (stats.totalKm >= 42f)
                earned += BadgeEntity("run_marathon_$userId", userId, "Marathon Hero",
                    "Ran 42 km total", "🥇", "EPIC", now)

            // Badges por level
            if (stats.level >= 3)
                earned += BadgeEntity("level_rising_$userId", userId, "Rising Star",
                    "Reached Level 3", "🌟", "RARE", now)
            if (stats.level >= 5)
                earned += BadgeEntity("level_champion_$userId", userId, "Champion",
                    "Reached Level 5", "🏅", "EPIC", now)
            if (stats.level >= 10)
                earned += BadgeEntity("level_legend_$userId", userId, "Legend",
                    "Reached Level 10", "👑", "LEGENDARY", now)

            if (earned.isNotEmpty()) {
                statsDatabase.badgeDao().insertBadges(earned)
                badgeArrayMapCache.loadBadges(earned)
                Log.d("📊 MYSTATS:", "🏅 ${earned.size} badges guardados para $userId")
            }
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "Error computeAndSaveBadges: ${e.message}")
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun emptyStats(userId: String, syncStatus: String = "IDLE") = UserStatsEntity(
        userId = userId,
        totalKm = 0f,
        totalEvents = 0,
        totalPosts = 0,
        totalMessages = 0,
        level = 1,
        points = 0,
        streakDays = 0,
        lastSyncAt = System.currentTimeMillis(),
        syncStatus = syncStatus,
        hasRealData = false
    )

    // ─── Enriched data: runs, challenges, sport breakdown ────────────────────

    /**
     * Fetches the last 10 run sessions for the user from Firestore, reversed into
     * chronological order so the chart renders left = oldest → right = newest.
     */
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
            }.reversed()  // oldest first → looks natural in a bar chart

            Log.d("📊 MYSTATS:", "🏃 Recent runs: ${runs.size}")
            emit(runs)
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "Error getRecentRuns: ${e.message}")
            emit(emptyList())
        }
    }

    /**
     * Fetches active challenges the user participates in and maps them to
     * [ActiveChallengeData] with the user's personal progress (0–100 scale).
     */
    override fun getActiveChallenges(userId: String): Flow<List<ActiveChallengeData>> = flow {
        if (userId.isBlank()) { emit(emptyList()); return@flow }
        try {
            val db = FirebaseFirestore.getInstance()
            val snapshot = db.collection("challenges")
                .whereEqualTo("status", "active")
                .whereArrayContains("participants", userId)
                .get()
                .await()

            val challenges = snapshot.documents.mapNotNull { doc ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    val progressByUser = doc.get("progressByUser") as? Map<String, Any>
                        ?: emptyMap()
                    val userProg: Double = when (val raw = progressByUser[userId]) {
                        is Double -> raw
                        is Long   -> raw.toDouble()
                        is Number -> raw.toDouble()
                        else      -> 0.0
                    }
                    ActiveChallengeData(
                        id           = doc.id,
                        title        = doc.getString("title") ?: "",
                        sport        = doc.getString("sport") ?: "",
                        goalLabel    = doc.getString("goalLabel") ?: "",
                        userProgress = userProg,
                        endDate      = doc.getTimestamp("endDate")?.toDate()?.time
                    )
                } catch (e: Exception) {
                    Log.e("📊 MYSTATS:", "Error parsing challenge ${doc.id}: ${e.message}")
                    null
                }
            }

            Log.d("📊 MYSTATS:", "🎯 Active challenges: ${challenges.size}")
            emit(challenges)
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "Error getActiveChallenges: ${e.message}")
            emit(emptyList())
        }
    }

    /**
     * Counts how many events the user has joined per sport, using the same
     * collectionGroup("members") strategy as countUserEvents().
     * Returns a list sorted by count descending (most played sport first).
     */
    override fun getSportBreakdown(userId: String): Flow<List<SportBreakdownItem>> = flow {
        if (userId.isBlank()) { emit(emptyList()); return@flow }
        try {
            val db = FirebaseFirestore.getInstance()

            val memberships = db.collectionGroup("members")
                .whereEqualTo("userId", userId)
                .get()
                .await()

            // Keep only docs whose path is  events/{eventId}/members/{…}
            val eventRefs = memberships.documents
                .filter { doc -> doc.reference.parent.parent?.parent?.id == "events" }
                .mapNotNull { doc -> doc.reference.parent.parent }

            val sportCounts = mutableMapOf<String, Int>()
            for (eventRef in eventRefs) {
                try {
                    val eventDoc = eventRef.get().await()
                    val sport = eventDoc.getString("sport")
                        ?.takeIf { it.isNotBlank() } ?: "Other"
                    sportCounts[sport] = (sportCounts[sport] ?: 0) + 1
                } catch (_: Exception) { /* skip inaccessible event */ }
            }

            val breakdown = sportCounts.entries
                .sortedByDescending { it.value }
                .map { SportBreakdownItem(sport = it.key, count = it.value) }

            Log.d("📊 MYSTATS:", "⚽ Sport breakdown: ${breakdown.size} sports, ${eventRefs.size} events")
            emit(breakdown)
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "Error getSportBreakdown: ${e.message}")
            emit(emptyList())
        }
    }

    // ─── Cache diagnostics ────────────────────────────────────────────────────

    fun printCacheStats() {
        Log.d("CACHE_STATS", badgeArrayMapCache.getStats())
    }
}
