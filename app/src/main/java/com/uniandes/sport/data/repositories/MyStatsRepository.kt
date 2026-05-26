package com.uniandes.sport.data.repositories

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.uniandes.sport.data.cache.BadgeArrayMapCache
import com.uniandes.sport.data.database.StatsDatabase
import com.uniandes.sport.data.entities.ActivityLogEntity
import com.uniandes.sport.data.entities.BadgeEntity
import com.uniandes.sport.data.entities.UserStatsEntity
import com.uniandes.sport.viewmodels.communities.CommunitiesViewModelInterface
import com.uniandes.sport.viewmodels.play.PlayViewModelInterface
import com.uniandes.sport.viewmodels.running.FirestoreRunningViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/**
 * MyStatsRepositoryInterface — Contrato para acceso a datos de estadísticas.
 */
interface MyStatsRepositoryInterface {
    fun getStats(userId: String, forceRefresh: Boolean = false): Flow<UserStatsEntity>
    fun getBadges(userId: String): Flow<List<BadgeEntity>>
    fun getSyncStatus(): Flow<String>
}

/**
 * MyStatsRepository — Implementación con cache-first strategy.
 * 
 * FEATURES IMPLEMENTADOS:
 * a) Multi-threading (Requisito a): Coroutines en IO dispatcher
 * c) Caching (Requisito c): Room (50ms) + StateFlow + ArrayMap
 * d) Eventual Connectivity (Requisito d): SyncStatus + fallback
 * 
 * @author Juan Felipe Hernández
 * @since 26-may-2026
 */
class MyStatsRepository(
    private val statsDatabase: StatsDatabase,
    private val badgeArrayMapCache: BadgeArrayMapCache = BadgeArrayMapCache(),
    private val playViewModel: PlayViewModelInterface? = null,
    private val communitiesViewModel: CommunitiesViewModelInterface? = null,
    private val runningViewModel: FirestoreRunningViewModel? = null
) : MyStatsRepositoryInterface {

    private val _syncStatus = MutableStateFlow<String>("IDLE")

    override fun getSyncStatus(): Flow<String> = _syncStatus.asStateFlow()

    /**
     * GET STATS con cache-first strategy
     * 
     * FLUJO:
     * 1. Room Database (50ms) → Si existe
     * 2. Firestore Sync (500ms) → Si forceRefresh o TTL expiró
     * 3. Error Handling → Re-emitir caché como fallback
     */
    override fun getStats(
        userId: String,
        forceRefresh: Boolean
    ): Flow<UserStatsEntity> = flow {
        Log.d("📊 MYSTATS:", "🚀 START getStats() for userId: $userId | forceRefresh: $forceRefresh")
        
        // Paso 1: Obtener datos del Room Database (caché persistente)
        Log.d("📊 MYSTATS:", "📍 Attempting to collect from Room DAO...")
        val cachedStats: UserStatsEntity? = try {
            // Recopilar datos del Room de forma síncrona dentro del flow
            var result: UserStatsEntity? = null
            statsDatabase.userStatsDao().getStats(userId).collect { roomResult ->
                Log.d("📊 MYSTATS:", "📍 Room DAO emitted data: $roomResult")
                result = roomResult
            }
            Log.d("📊 MYSTATS:", "✓ Finished collecting from Room. stats=$result")
            result
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "❌ Error collecting from Room: ${e.message}")
            null
        }

        if (cachedStats != null) {
            Log.d("📊 MYSTATS:", "💾 From Room Cache: events=${cachedStats.totalEvents} posts=${cachedStats.totalPosts} km=${cachedStats.totalKm} hasRealData=${cachedStats.hasRealData}")
            emit(cachedStats)

            // Si data es fresca (TTL válido), no sincronizar
            if (!forceRefresh && System.currentTimeMillis() - (cachedStats.lastSyncAt
                    ?: 0) < 15 * 60 * 1000
            ) {
                Log.d("📊 MYSTATS:", "✅ Room cache is fresh (TTL valid), returning without Firestore sync")
                return@flow // Room data es fresca, no sincronizar
            }
        } else {
            Log.d("📊 MYSTATS:", "⚠️ Room returned null, will fetch from Firestore")
        }

        // Paso 2: Sincronizar desde Firestore si es necesario
        Log.d("📊 MYSTATS:", "🔄 About to enter Firestore sync block...")
        try {
            _syncStatus.value = "SYNCING"
            Log.d("📊 MYSTATS:", "🔄 SYNCING from Firestore...")

            // FEATURE: Multi-threading (Requisito a)
            // Fetch desde Firestore subcollections sin bloquear UI
            val newStats = fetchFromFirestoreSubcollections(userId, cachedStats)

            if (newStats != null) {
                // Paso 3: Guardar en Room Database
                statsDatabase.userStatsDao().insertStats(newStats)

                Log.d("📊 MYSTATS:", "✅ From Firestore Sync: events=${newStats.totalEvents} posts=${newStats.totalPosts} km=${newStats.totalKm} hasRealData=${newStats.hasRealData}")
                emit(newStats)
            } else {
                // Usuario nuevo sin datos - emitir entidad con valores por defecto
                Log.d("📊 MYSTATS:", "ℹ️ No data found in Firestore - new user, emitting empty stats")
                val emptyStats = UserStatsEntity(
                    userId = userId,
                    totalKm = 0f,
                    totalEvents = 0,
                    totalPosts = 0,
                    totalMessages = 0,
                    level = 1,
                    points = 0,
                    streakDays = 0,
                    lastSyncAt = System.currentTimeMillis(),
                    syncStatus = "IDLE",
                    hasRealData = false
                )
                Log.d("📊 MYSTATS:", "📤 EMITTING empty stats for new user")
                emit(emptyStats)
            }

            _syncStatus.value = "IDLE"

        } catch (e: Exception) {
            // Paso 4: Error handling
            Log.e("📊 MYSTATS:", "❌ EXCEPTION in getStats: ${e.message}", e)
            _syncStatus.value = "ERROR"

            // FEATURE: Eventual Connectivity (Requisito d)
            // Re-emitir caché como fallback, no dejar app rota
            cachedStats?.let { fallbackStats ->
                Log.d("📊 MYSTATS:", "📤 EMITTING fallback from cache after error")
                emit(fallbackStats)
            } ?: run {
                // Usuario nuevo, emitir entidad vacía
                Log.d("📊 MYSTATS:", "📤 EMITTING empty stats after error (no cache available)")
                emit(UserStatsEntity(
                    userId = userId,
                    totalKm = 0f,
                    totalEvents = 0,
                    totalPosts = 0,
                    totalMessages = 0,
                    level = 1,
                    points = 0,
                    streakDays = 0,
                    lastSyncAt = System.currentTimeMillis(),
                    syncStatus = "ERROR",
                    hasRealData = false
                ))
            }
        }
    }

    /**
     * GET BADGES con ArrayMap cache
     * 
     * FLUJO:
     * 1. ArrayMap Cache → Si está cargado
     * 2. Room Database → Fallback
     * 3. Empty list → Si no existe data
     */
    override fun getBadges(userId: String): Flow<List<BadgeEntity>> = flow {
        // Paso 1: Intentar ArrayMap Cache
        var badges = badgeArrayMapCache.getAllBadges()

        if (badges.isNotEmpty()) {
            Log.i("MyStatsRepository", " Emitting badges from ArrayMap Cache (HIT)")
            emit(badges)
            return@flow
        }

        // Paso 2: Fallback a Room Database
        badges = statsDatabase.badgeDao().getBadgesForUser(userId).run {
            var result: List<BadgeEntity> = emptyList()
            collect { result = it }
            result
        }

        if (badges.isNotEmpty()) {
            Log.i("MyStatsRepository", " Emitting badges from Room Cache")
            // Cargar en ArrayMap para próximas consultas
            badgeArrayMapCache.loadBadges(badges)
            emit(badges)
        } else {
            // First time, no badges yet
            Log.i("MyStatsRepository", "No badges found, emitting empty list")
            emit(emptyList())
        }
    }

    /**
     * Calcula estadísticas reales buscando en Firestore directamente
     * 
     * ESTRATEGIA:
     * 1. Buscar en db.collection("events") donde members incluya userId (open matches)
     * 2. Buscar en todas las comunidades → collection("posts") donde author == userId
     * 3. Buscar en users/{userId}/runs (corridas - ruta correcta)
     * 4. Agregar datos y calcular level + points
     * 
     * IMPORTANTE: Esta función es suspend (no blocking)
     * Se ejecuta en coroutine sin bloquear UI
     */
    private suspend fun calculateRealStats(userId: String): UserStatsEntity? {
        return try {
            Log.d("📊 MYSTATS:", "📍 calculateRealStats() START for userId: $userId")
            val db = FirebaseFirestore.getInstance()
            
            // 1. Contar eventos donde el usuario es miembro
            // Ruta: db.collection("events") → {eventId} → collection("members") → {userId}
            // OPTIMIZADO: Una sola query para obtener todos los eventos
            var totalEvents = 0
            try {
                val eventsSnapshot = db.collection("events")
                    .get().await()
                
                Log.d("📊 MYSTATS:", "📋 Checking ${eventsSnapshot.size()} total events for membership of $userId")
                
                // Filtrar eventos donde userId es miembro (paralelizable en Firestore v9+)
                val eventIds = eventsSnapshot.documents
                    .mapNotNull { it.id }
                
                // Hacer lookup eficiente: obtener todos los miembros en batch si es posible
                // Alternativa: hacer una sola query por cada evento (N+1 pero necesario sin indexes)
                for (eventId in eventIds) {
                    try {
                        val memberDoc = db.collection("events")
                            .document(eventId)
                            .collection("members")
                            .document(userId)
                            .get()
                            .await()
                        
                        if (memberDoc.exists()) {
                            totalEvents++
                        }
                    } catch (e: Exception) {
                        Log.w("MyStatsRepository", "Error checking event membership for $eventId: ${e.message}")
                    }
                }
                
                Log.d("📊 MYSTATS:", "🎯 EVENTS TOTAL: $totalEvents events where user is member")
            } catch (e: Exception) {
                Log.e("📊 MYSTATS:", "❌ Error counting events: ${e.message}")
                totalEvents = 0
            }
            
            // 2. Contar posts en comunidades donde author == userId
            // Ruta: db.collection("communities") → {communityId} → collection("posts")
            var totalPosts = 0
            try {
                Log.d("📊 MYSTATS:", "📋 Searching posts in communities...")
                val communitiesSnapshot = db.collection("communities").get().await()
                Log.d("📊 MYSTATS:", "📋 Found ${communitiesSnapshot.size()} communities")
                
                for (communityDoc in communitiesSnapshot.documents) {
                    val postsSnapshot = communityDoc.reference
                        .collection("posts")
                        .whereEqualTo("author", userId)
                        .get()
                        .await()
                    if (postsSnapshot.size() > 0) {
                        Log.d("📊 MYSTATS:", "✓ Found ${postsSnapshot.size()} posts in community: ${communityDoc.id}")
                    }
                    totalPosts += postsSnapshot.size()
                }
                
                Log.d("📊 MYSTATS:", "🎯 POSTS TOTAL: $totalPosts posts for user: $userId")
            } catch (e: Exception) {
                Log.e("📊 MYSTATS:", "❌ Error counting posts: ${e.message}")
                totalPosts = 0
            }
            
            // 3. Sumar km en users/{userId}/runs
            var totalKm = 0f
            try {
                Log.d("📊 MYSTATS:", "📋 Searching runs at users/$userId/runs...")
                val runsSnapshot = db.collection("users").document(userId)
                    .collection("runs").get().await()
                
                Log.d("📊 MYSTATS:", "📋 Found ${runsSnapshot.size()} runs")
                
                totalKm = runsSnapshot.documents.fold(0f) { acc, doc ->
                    val distance = doc.getDouble("distanceKm") ?: 0.0
                    val runId = doc.id
                    Log.d("📊 MYSTATS:", "✓ Run $runId: ${String.format("%.2f", distance)}km")
                    acc + distance.toFloat()
                }
                
                Log.d("📊 MYSTATS:", "🎯 RUNS TOTAL: $totalKm km for user: $userId")
            } catch (e: Exception) {
                Log.e("📊 MYSTATS:", "❌ Error fetching runs: ${e.message}")
                totalKm = 0f
            }
            
            // 4. Calcular level y points
            val (level, points) = calculateLevelAndPoints(
                totalEvents,
                totalPosts,
                totalKm
            )
            
            // 5. Calcular streak
            val streakDays = calculateStreakDays(userId)
            
            val result = UserStatsEntity(
                userId = userId,
                totalKm = totalKm,
                totalEvents = totalEvents,
                totalPosts = totalPosts,
                totalMessages = 0, // NOTA: Requiere ChannelsViewModel para agregación de mensajes
                level = level,
                points = points,
                streakDays = streakDays,
                lastSyncAt = System.currentTimeMillis(),
                syncStatus = "IDLE",
                hasRealData = true
            )
            Log.d("📊 MYSTATS:", "✅ RESULT: level=$level points=$points streak=$streakDays (events=$totalEvents, posts=$totalPosts, km=$totalKm)")
            result
        } catch (e: Exception) {
            Log.e("📊 MYSTATS:", "❌ ERROR in calculateRealStats: ${e.message}", e)
            null
        }
    }

    /**
     * Calcula level y points basado en actividades
     * 
     * FÓRMULA:
     * points = (totalEvents * 10) + (totalPosts * 5) + totalKm.toInt()
     * level = (points / 100) + 1
     * 
     * EJEMPLOS:
     * - 0 actividades → 0 points → Level 1
     * - 100 points → Level 2
     * - 200 points → Level 3
     */
    private fun calculateLevelAndPoints(
        totalEvents: Int,
        totalPosts: Int,
        totalKm: Float
    ): Pair<Int, Int> {
        val points = (totalEvents * 10) + (totalPosts * 5) + totalKm.toInt()
        val level = (points / 100) + 1
        return Pair(level, points)
    }

    /**
     * Calcula días consecutivos de actividad (streak)
     * 
     * LÓGICA:
     * 1. Consultar últimos 365 días de actividades desde ActivityLog
     * 2. Agrupar por fecha única (hoy, ayer, hace 2 días, etc.)
     * 3. Contar cuántos días seguidos tienen al menos 1 actividad
     * 4. Parar cuando encontramos un hueco (día sin actividad)
     * 
     * EJEMPLOS:
     * - Actividades hoy y ayer → streak = 2
     * - Hoy, ayer, pero no anteayer → streak = 2
     * - Sin actividades → streak = 0
     */
    private fun calculateStreakDays(userId: String): Int {
        return try {
            // Obtener actividades de los últimos 365 días
            val thirtyDaysAgo = System.currentTimeMillis() - (365 * 24 * 60 * 60 * 1000L)
            val todayStart = System.currentTimeMillis() - (System.currentTimeMillis() % (24 * 60 * 60 * 1000L))
            
            val activities = mutableListOf<ActivityLogEntity>()
            
            // Ejecutar Flow bloqueante para obtener datos (en contexto sync)
            runBlocking {
                statsDatabase.activityLogDao()
                    .getActivitiesInRange(userId, thirtyDaysAgo, System.currentTimeMillis())
                    .collect { activities.addAll(it) }
            }
            
            if (activities.isEmpty()) return 0
            
            // Agrupar por día y calcular streak
            val activeDays = activities
                .map { activity ->
                    // Convertir timestamp a fecha (00:00)
                    activity.activityDate - (activity.activityDate % (24 * 60 * 60 * 1000L))
                }
                .toSortedSet(compareByDescending { it })
            
            // Contar días consecutivos desde hoy hacia atrás
            var streak = 0
            var currentDate = todayStart
            
            for (i in 0..365) {
                if (currentDate in activeDays) {
                    streak++
                    currentDate -= 24 * 60 * 60 * 1000L
                } else {
                    break
                }
            }
            
            streak
        } catch (e: Exception) {
            Log.w("MyStatsRepository", "Error calculating streak: ${e.message}")
            0
        }
    }

    /**
     * Fetch desde Firestore Subcollections
     * 
     * CASOS:
     * 1. Usuario NUEVO (sin Room) → null (mostrar "No hay datos...")
     * 2. Usuario con DATOS Room pero SIN Firestore → Calcular + crear subcollections
     * 3. Usuario con DATOS Room Y Firestore → Sincronizar
     */
    private suspend fun fetchFromFirestoreSubcollections(
        userId: String,
        localStats: UserStatsEntity?
    ): UserStatsEntity? {
        return try {
            // SIEMPRE intentar obtener datos reales desde Firestore
            Log.i("MyStatsRepository", "Buscando datos en Firestore para usuario: $userId")
            
            val realStats = calculateRealStats(userId)
            
            if (realStats != null && realStats.hasRealData) {
                // Se encontraron datos en Firestore
                Log.i("MyStatsRepository", "✅ Datos encontrados para $userId: ${realStats.totalEvents} events, ${realStats.totalPosts} posts, ${realStats.totalKm}km")
                
                // Guardar en Room Database para caché local
                statsDatabase.userStatsDao().insertStats(realStats)
                
                // Opcionalmente crear/actualizar subcollections en Firestore
                createFirestoreSubcollections(userId, realStats)
                
                return realStats
            }
            
            // No se encontraron datos en Firestore, pero verificar si hay data local
            if (localStats != null && localStats.hasRealData) {
                Log.i("MyStatsRepository", "Usando datos locales para $userId")
                return localStats.copy(
                    lastSyncAt = System.currentTimeMillis(),
                    syncStatus = "IDLE"
                )
            }
            
            // No hay datos ni en Firestore ni en Room → Usuario nuevo
            Log.i("MyStatsRepository", "ℹ No data found for user: $userId (new user)")
            UserStatsEntity(
                userId = userId,
                totalKm = 0f,
                totalEvents = 0,
                totalPosts = 0,
                totalMessages = 0,
                level = 1,
                points = 0,
                streakDays = 0,
                lastSyncAt = System.currentTimeMillis(),
                syncStatus = "IDLE",
                hasRealData = false
            )
        } catch (e: Exception) {
            // Firestore offline u error
            Log.w("MyStatsRepository", "Error fetching from Firestore: ${e.message}")

            // Si existe data local, usarla
            if (localStats?.hasRealData == true) {
                Log.i("MyStatsRepository", "Using local data (Firestore offline)")
                return localStats
            }

            // Si no hay data local y no hay Firestore → devolver entidad vacía
            Log.i("MyStatsRepository", "No data available (offline)")
            UserStatsEntity(
                userId = userId,
                totalKm = 0f,
                totalEvents = 0,
                totalPosts = 0,
                totalMessages = 0,
                level = 1,
                points = 0,
                streakDays = 0,
                lastSyncAt = System.currentTimeMillis(),
                syncStatus = "OFFLINE",
                hasRealData = false
            )
        }
    }

    /**
     * Crear subcollections en Firestore para usuario que tiene datos locales
     * pero no tiene subcollections creadas aún
     * 
     * NOTA: Opcional - MVP funciona completamente con Room Database
     */
    private suspend fun createFirestoreSubcollections(
        userId: String,
        stats: UserStatsEntity
    ) {
        try {
            // NOTA: Para habilitar sincronización con Firestore, descomentar:
            //
            // val db = FirebaseFirestore.getInstance()
            // db.collection("users").document(userId).collection("stats")
            //     .document("profile").set(stats.toMap())
            //
            // db.collection("users").document(userId).collection("badges")
            //     .document("all").set(mapOf("count" to stats.level))
            //
            // db.collection("users").document(userId).collection("activities")
            //     .document("all").set(mapOf(
            //         "events" to stats.totalEvents,
            //         "posts" to stats.totalPosts,
            //         "km" to stats.totalKm
            //     ))
            //
            // db.collection("users").document(userId).collection("streaks")
            //     .document("current").set(mapOf("days" to stats.streakDays))

            Log.i(
                "MyStatsRepository",
                "ℹ Firestore sync skipped (optional for MVP) - user: $userId"
            )
        } catch (e: Exception) {
            Log.e(
                "MyStatsRepository",
                "Error in createFirestoreSubcollections: ${e.message}"
            )
        }
    }

    /**
     * Estadísticas de caches (para debugging)
     */
    fun printCacheStats() {
        Log.d("CACHE_STATS", badgeArrayMapCache.getStats())
    }
}
