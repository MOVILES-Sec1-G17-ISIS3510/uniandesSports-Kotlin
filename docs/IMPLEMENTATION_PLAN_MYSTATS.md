# 📊 Implementation Plan: MyStats — Estadísticas Personalizadas Gamificadas

**Autor:** Juan Felipe Hernández  
**Fecha:** Mayo 26, 2026  
**Entrega:** Entrega 4 — Features + Vistas Nuevas  
**Grupo:** G17 (ISIS3510 MOVILES)

---

## 📋 Executive Summary

Implementación de una **feature integral** que incluye:
- **Multi-threading:** WorkManager + Coroutines para sincronización en background
- **Local Storage:** Room Database para almacenar estadísticas y badges
- **Caching:** Patrón cache-first con sincronización periódica
- **Eventual Connectivity:** Indicadores de sync y modo offline

**Nueva vista:** `MyStatsScreen` — Estadísticas personalizadas con gráficas y badges gamificados.

**Puntos esperados:** 80 puntos (20 + 20 + 20 + 20)

---

## 🎯 Requisitos Obligatorios

| Requisito | Puntos | Plataforma | Status | Implementación |
|-----------|--------|-----------|--------|-----------------|
| **(a) Multi-threading/Concurrency** | 20 | Ambas | ✅ Planeado | WorkManager + Coroutines |
| **(b) Local storage** | 20 | Ambas | ✅ Planeado | Room Database (5 entities) |
| **(c) Caching** | 20 | Ambas | ✅ Planeado | Cache-first + StateFlow |
| **(d) Eventual connectivity** | 20 | Todas | ✅ Planeado | Sync status + offline mode |
| **Nueva vista** | 15 | Ambas | ✅ Planeado | MyStatsScreen |
| **TOTAL ESPERADO** | **95** | — | — | — |

---

## 📁 Estructura del Proyecto

### **Carpetas a crear:**

```
app/src/main/java/com/uniandes/sport/
├── data/
│   ├── database/
│   │   ├── AppDatabase.kt (Room DB definition)
│   │   └── dao/
│   │       ├── BadgeDao.kt
│   │       ├── UserStatsDao.kt
│   │       ├── ActivityLogDao.kt
│   │       └── StreakDao.kt
│   ├── entities/
│   │   ├── BadgeEntity.kt
│   │   ├── UserStatsEntity.kt
│   │   ├── ActivityLogEntity.kt
│   │   └── StreakEntity.kt
│   └── repositories/
│       └── MyStatsRepository.kt
├── viewmodels/
│   └── stats/
│       ├── MyStatsViewModelInterface.kt
│       ├── MyStatsViewModel.kt (Firestore impl)
│       └── DummyMyStatsViewModel.kt (testing)
├── workers/
│   └── SyncStatsWorker.kt (WorkManager)
└── ui/
    └── screens/
        ├── MyStatsScreen.kt (Nueva vista principal)
        └── components/
            ├── BadgeCard.kt
            ├── StatCard.kt
            ├── LineChart.kt (con Canvas o Vico)
            ├── BarChart.kt
            ├── PieChart.kt
            └── ProgressBars.kt
```

---

## 📊 **FEATURE 1: Multi-threading & Background Sync**

### **Objetivo:**
Sincronizar estadísticas y badges en background cada 30 minutos sin bloquear la UI.

### **Componentes:**

#### **1.1 WorkManager Setup (`SyncStatsWorker.kt`)**

```kotlin
class SyncStatsWorker(
    appContext: Context,
    params: WorkerParameters,
    private val statsRepository: MyStatsRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Obtener datos de Firestore
            val firestoreStats = statsRepository.fetchStatsFromFirestore()
            
            // Calcular badges
            val newBadges = calculateBadges(firestoreStats)
            
            // Guardar en Room DB
            statsRepository.saveStats(firestoreStats)
            statsRepository.saveBadges(newBadges)
            
            // Notificar si hay badge nuevo
            if (newBadges.any { it.isNew }) {
                notifyNewBadges(newBadges.filter { it.isNew })
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncStatsWorker", "Error syncing stats", e)
            Result.retry()
        }
    }

    private fun notifyNewBadges(badges: List<BadgeEntity>) {
        // Enviar notificación al usuario
    }

    private suspend fun calculateBadges(stats: UserStatsEntity): List<BadgeEntity> {
        // Lógica de cálculo de badges (ver sección Badges)
        return emptyList()
    }
}
```

#### **1.2 Periodic Sync Setup (`MainActivity.kt` o `WorkerSetup.kt`)**

```kotlin
// En MainActivity.onCreate() o en un setup helper
private fun setupPeriodicSync() {
    val syncStatsRequest = PeriodicWorkRequestBuilder<SyncStatsWorker>(
        repeatInterval = 30, // cada 30 minutos
        repeatIntervalTimeUnit = TimeUnit.MINUTES
    ).build()
    
    WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
        "sync_stats_work",
        ExistingPeriodicWorkPolicy.KEEP, // Si existe, mantener
        syncStatsRequest
    )
}
```

#### **1.3 Dependencies (`app/build.gradle`)**

```gradle
// WorkManager
implementation "androidx.work:work-runtime-ktx:2.8.1"

// Coroutines (ya debe estar)
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1"
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1"
```

---

## 💾 **FEATURE 2: Local Storage (Room Database)**

### **Objetivo:**
Almacenar estadísticas, badges, log de actividades y streaks localmente para caché persistente.

### **Componentes:**

#### **2.1 Entities**

**`BadgeEntity.kt`**
```kotlin
@Entity(tableName = "badges", indices = [Index("userId")])
data class BadgeEntity(
    @PrimaryKey val badgeId: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val icon: String = "", // nombre del ícono (social_shark, runner, etc)
    val rarity: String = "", // COMMON, RARE, EPIC, LEGENDARY
    val unlockedAt: Long = 0L,
    val isNew: Boolean = false
)
```

**`UserStatsEntity.kt`**
```kotlin
@Entity(tableName = "user_stats", primaryKeys = ["userId"])
data class UserStatsEntity(
    val userId: String = "",
    val totalKm: Float = 0f,
    val totalEvents: Int = 0,
    val totalPosts: Int = 0,
    val totalMessages: Int = 0,
    val totalBadgesUnlocked: Int = 0,
    val level: Int = 1,
    val points: Int = 0,
    val streakDays: Int = 0,
    val lastSyncAt: Long = 0L,
    val syncStatus: String = "IDLE" // SYNCING, IDLE, ERROR
)
```

**`ActivityLogEntity.kt`**
```kotlin
@Entity(
    tableName = "activity_log",
    indices = [Index("userId"), Index("date")],
    foreignKeys = [ForeignKey(
        entity = UserStatsEntity::class,
        parentColumns = ["userId"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String = "",
    val activityType: String = "", // POST, MESSAGE, EVENT, RUN, BADGE
    val activityDate: Long = 0L, // fecha de la actividad
    val value: Float = 0f, // km, count, etc
    val recordedAt: Long = System.currentTimeMillis()
)
```

**`StreakEntity.kt`**
```kotlin
@Entity(tableName = "streaks", indices = [Index("userId")])
data class StreakEntity(
    @PrimaryKey val streakId: String = "",
    val userId: String = "",
    val streakType: String = "", // SOCIAL_DAYS, EVENT_WEEKS, RUN_CONSECUTIVE
    val currentCount: Int = 0,
    val maxCount: Int = 0,
    val lastDate: Long = 0L,
    val startDate: Long = 0L
)
```

#### **2.2 Data Access Objects (DAOs)**

**`BadgeDao.kt`**
```kotlin
@Dao
interface BadgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBadge(badge: BadgeEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBadges(badges: List<BadgeEntity>)
    
    @Query("SELECT * FROM badges WHERE userId = :userId ORDER BY unlockedAt DESC")
    fun getBadgesForUser(userId: String): Flow<List<BadgeEntity>>
    
    @Query("SELECT COUNT(*) FROM badges WHERE userId = :userId AND isNew = 1")
    fun getNewBadgesCount(userId: String): Flow<Int>
    
    @Query("UPDATE badges SET isNew = 0 WHERE userId = :userId")
    suspend fun markBadgesAsViewed(userId: String)
}
```

**`UserStatsDao.kt`**
```kotlin
@Dao
interface UserStatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: UserStatsEntity)
    
    @Query("SELECT * FROM user_stats WHERE userId = :userId")
    fun getStats(userId: String): Flow<UserStatsEntity?>
    
    @Query("UPDATE user_stats SET syncStatus = :status WHERE userId = :userId")
    suspend fun updateSyncStatus(userId: String, status: String)
    
    @Query("UPDATE user_stats SET lastSyncAt = :timestamp WHERE userId = :userId")
    suspend fun updateLastSync(userId: String, timestamp: Long)
}
```

**`ActivityLogDao.kt`**
```kotlin
@Dao
interface ActivityLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActivity(activity: ActivityLogEntity)
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActivities(activities: List<ActivityLogEntity>)
    
    @Query("""
        SELECT * FROM activity_log 
        WHERE userId = :userId 
        AND activityDate BETWEEN :startDate AND :endDate
        ORDER BY activityDate DESC
    """)
    fun getActivitiesInRange(
        userId: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<ActivityLogEntity>>
    
    @Query("""
        SELECT strftime('%Y-%m-%d', activityDate / 1000, 'unixepoch') as date, SUM(value) as total
        FROM activity_log
        WHERE userId = :userId AND activityType = :type AND activityDate > :since
        GROUP BY date
        ORDER BY date
    """)
    fun getActivitySummaryByDay(
        userId: String,
        type: String,
        since: Long
    ): Flow<List<DailySummary>>
}

data class DailySummary(
    val date: String,
    val total: Float
)
```

**`StreakDao.kt`**
```kotlin
@Dao
interface StreakDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreak(streak: StreakEntity)
    
    @Query("SELECT * FROM streaks WHERE userId = :userId")
    fun getStreaksForUser(userId: String): Flow<List<StreakEntity>>
    
    @Query("UPDATE streaks SET currentCount = :count, lastDate = :lastDate WHERE streakId = :streakId")
    suspend fun updateStreak(streakId: String, count: Int, lastDate: Long)
}
```

#### **2.3 Room Database (`AppDatabase.kt`)**

```kotlin
@Database(
    entities = [
        BadgeEntity::class,
        UserStatsEntity::class,
        ActivityLogEntity::class,
        StreakEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StatsDatabase : RoomDatabase() {
    abstract fun badgeDao(): BadgeDao
    abstract fun userStatsDao(): UserStatsDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun streakDao(): StreakDao

    companion object {
        @Volatile
        private var INSTANCE: StatsDatabase? = null

        fun getDatabase(context: Context): StatsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StatsDatabase::class.java,
                    "stats_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

#### **2.4 Dependencies (`app/build.gradle`)**

```gradle
// Room Database
implementation "androidx.room:room-runtime:2.5.2"
implementation "androidx.room:room-ktx:2.5.2"
kapt "androidx.room:room-compiler:2.5.2"
```

---

## 🎨 **FEATURE 3: Caching Strategy**

### **Objetivo:**
Implementar patrón **cache-first** con sincronización en background, mostrando datos locales inmediatamente.

### **Componentes:**

#### **3.1 Repository Pattern (`MyStatsRepository.kt`)**

```kotlin
interface MyStatsRepositoryInterface {
    suspend fun getStats(userId: String, forceRefresh: Boolean = false): Flow<UserStatsEntity?>
    suspend fun getBadges(userId: String): Flow<List<BadgeEntity>>
    suspend fun getActivityMetrics(userId: String, days: Int = 7): Flow<List<ActivityMetric>>
    suspend fun getSyncStatus(): Flow<String>
}

class MyStatsRepository(
    private val playViewModel: PlayViewModelInterface,
    private val tweetsViewModel: TweetsViewModelInterface,
    private val communitiesViewModel: CommunitiesViewModelInterface,
    private val runningViewModel: FirestoreRunningViewModel,
    private val statsDatabase: StatsDatabase,
    private val authViewModel: AuthViewModelInterface
) : MyStatsRepositoryInterface {

    private val _syncStatus = MutableStateFlow<String>("IDLE")
    override fun getSyncStatus(): Flow<String> = _syncStatus.asStateFlow()

    override suspend fun getStats(userId: String, forceRefresh: Boolean): Flow<UserStatsEntity?> {
        return flow {
            // 1. Emitir datos locales primero (cache-first)
            val cachedStats = statsDatabase.userStatsDao().getStats(userId).firstOrNull()
            if (cachedStats != null) {
                emit(cachedStats)
            }

            // 2. Si forceRefresh o caché viejo (>15 min), sincronizar desde Firestore
            val shouldSync = forceRefresh || 
                (cachedStats?.lastSyncAt?.let { 
                    System.currentTimeMillis() - it > 15 * 60 * 1000 
                } ?: true)

            if (shouldSync) {
                try {
                    _syncStatus.value = "SYNCING"
                    
                    // Obtener datos de múltiples ViewModels
                    val events = playViewModel.fetchEvents()
                    val posts = tweetsViewModel.fetchTweets()
                    val runs = runningViewModel.fetchPastRuns()
                    val communities = communitiesViewModel.loadCommunities()
                    
                    // Calcular totales
                    val newStats = UserStatsEntity(
                        userId = userId,
                        totalEvents = events.size,
                        totalPosts = posts.size,
                        totalKm = runs.sumOf { it.distanceKm }.toFloat(),
                        totalMessages = communities.sumOf { /* count messages */ 0 },
                        lastSyncAt = System.currentTimeMillis(),
                        syncStatus = "IDLE"
                    )
                    
                    // Guardar en Room
                    statsDatabase.userStatsDao().insertStats(newStats)
                    
                    emit(newStats)
                    _syncStatus.value = "IDLE"
                    
                } catch (e: Exception) {
                    Log.e("MyStatsRepository", "Error syncing stats", e)
                    _syncStatus.value = "ERROR"
                    // Re-emitir caché como fallback
                    if (cachedStats != null) {
                        emit(cachedStats)
                    }
                }
            }
        }
    }

    override suspend fun getBadges(userId: String): Flow<List<BadgeEntity>> {
        return statsDatabase.badgeDao().getBadgesForUser(userId)
    }

    override suspend fun getActivityMetrics(
        userId: String,
        days: Int
    ): Flow<List<ActivityMetric>> {
        return flow {
            val endDate = System.currentTimeMillis()
            val startDate = endDate - (days * 24 * 60 * 60 * 1000)
            
            val activities = statsDatabase.activityLogDao()
                .getActivitiesInRange(userId, startDate, endDate)
                .firstOrNull() ?: emptyList()
            
            emit(convertToMetrics(activities))
        }
    }

    private fun convertToMetrics(activities: List<ActivityLogEntity>): List<ActivityMetric> {
        // Agrupar y procesar para gráficas
        return emptyList()
    }

    suspend fun fetchStatsFromFirestore(): UserStatsEntity {
        // Implementar lógica de fetch desde todos los ViewModels
        return UserStatsEntity()
    }

    suspend fun saveStats(stats: UserStatsEntity) {
        statsDatabase.userStatsDao().insertStats(stats)
    }

    suspend fun saveBadges(badges: List<BadgeEntity>) {
        statsDatabase.badgeDao().insertBadges(badges)
    }
}
```

#### **3.2 StateFlow Management (`MyStatsViewModel.kt`)**

```kotlin
interface MyStatsViewModelInterface {
    val stats: StateFlow<UserStatsEntity?>
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

    private val _stats = MutableStateFlow<UserStatsEntity?>(null)
    override val stats: StateFlow<UserStatsEntity?> = _stats.asStateFlow()

    private val _badges = MutableStateFlow<List<BadgeEntity>>(emptyList())
    override val badges: StateFlow<List<BadgeEntity>> = _badges.asStateFlow()

    private val _syncStatus = MutableStateFlow<String>("IDLE")
    override val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Cargar stats y badges al inicializar
        viewModelScope.launch {
            // Observar stats
            repository.getStats(userId).collect { stats ->
                _stats.value = stats
                _isLoading.value = false
            }
        }

        viewModelScope.launch {
            // Observar badges
            repository.getBadges(userId).collect { badgeList ->
                _badges.value = badgeList
            }
        }

        viewModelScope.launch {
            // Observar sync status
            repository.getSyncStatus().collect { status ->
                _syncStatus.value = status
                _isLoading.value = status == "SYNCING"
            }
        }
    }

    override fun refreshStats(forceSync: Boolean) {
        viewModelScope.launch {
            repository.getStats(userId, forceSync = forceSync).collect { stats ->
                _stats.value = stats
            }
        }
    }

    override fun markBadgesAsViewed() {
        viewModelScope.launch {
            // Implementar
        }
    }
}
```

---

## 📡 **FEATURE 4: Eventual Connectivity**

### **Objetivo:**
Detectar pérdida de conexión, mostrar estado, permitir acciones offline.

### **Componentes:**

#### **4.1 Connectivity Manager**

```kotlin
class ConnectivityObserver(private val context: Context) {
    private val connectivityManager = context.getSystemService(
        Context.CONNECTIVITY_SERVICE
    ) as ConnectivityManager

    fun observe(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
}
```

#### **4.2 ViewModel Integration**

```kotlin
class MyStatsViewModel(
    private val repository: MyStatsRepository,
    private val userId: String,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel(), MyStatsViewModelInterface {
    
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        // Observar conectividad
        viewModelScope.launch {
            connectivityObserver.observe().collect { isConnected ->
                _isOnline.value = isConnected
            }
        }
    }

    override fun refreshStats(forceSync: Boolean) {
        if (!_isOnline.value && !forceSync) {
            // Si está offline, usar caché local
            Log.i("MyStatsViewModel", "Offline mode: showing cached data")
            return
        }

        viewModelScope.launch {
            repository.getStats(userId, forceSync = forceSync).collect { stats ->
                _stats.value = stats
            }
        }
    }
}
```

#### **4.3 UI Indicators**

En `MyStatsScreen.kt`:
```kotlin
// Mostrar indicador de conectividad
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    if (isOnline) {
        Text(
            "🟢 Conectado",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Green
        )
    } else {
        Text(
            "🔴 Sin conexión (mostrando datos locales)",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Red
        )
    }

    when (syncStatus) {
        "SYNCING" -> {
            CircularProgressIndicator(modifier = Modifier.size(16.dp))
        }
        "ERROR" -> {
            Text("⚠️ Error de sincronización", color = Color.Orange)
        }
        "IDLE" -> {
            if (lastSyncTime != null) {
                Text(
                    "Última sincronización: ${formatTime(lastSyncTime)}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// Botón para sincronizar manualmente
Button(
    onClick = { viewModel.refreshStats(forceSync = true) },
    enabled = isOnline && syncStatus != "SYNCING"
) {
    Text("Sincronizar ahora")
}
```

---

## 🎮 **Badges & Gamificación**

### **Badges a Implementar:**

#### **Social Badges** 🗣️

```kotlin
enum class SocialBadge(val id: String, val title: String, val icon: String, val rarity: Rarity) {
    SOCIAL_SHARK(
        "social_shark",
        "Social Shark",
        "🦈",
        Rarity.RARE
    ), // 3 días seguidos escribiendo
    CHATTERBOX(
        "chatterbox",
        "Chatterbox",
        "💬",
        Rarity.COMMON
    ), // 50+ mensajes
    COMMUNITY_LEADER(
        "community_leader",
        "Community Leader",
        "👑",
        Rarity.EPIC
    ), // Admin en 2+ comunidades
    WALL_STAR(
        "wall_star",
        "Wall Star",
        "⭐",
        Rarity.COMMON
    ), // 20+ posts
    INFLUENCER(
        "influencer",
        "Influencer",
        "💥",
        Rarity.RARE
    ), // 100+ likes recibidos
}
```

#### **Athlete Badges** 🏃

```kotlin
enum class AthleteBadge(val id: String, val title: String, val icon: String, val rarity: Rarity) {
    RUNNER(
        "runner",
        "Runner",
        "🏃",
        Rarity.EPIC
    ), // 100+ km corridos
    MARATHON_MASTER(
        "marathon_master",
        "Marathon Master",
        "🏅",
        Rarity.LEGENDARY
    ), // 42+ km en una sesión
    WEEKLY_WARRIOR(
        "weekly_warrior",
        "Weekly Warrior",
        "⚔️",
        Rarity.RARE
    ), // 5+ eventos en una semana
    EVENT_CREATOR(
        "event_creator",
        "Event Creator",
        "📅",
        Rarity.COMMON
    ), // 5+ eventos creados
    CONSISTENT(
        "consistent",
        "Consistent",
        "🔥",
        Rarity.EPIC
    ), // 4 semanas seguidas con eventos
}
```

#### **Challenge Badges** 🎯

```kotlin
enum class ChallengeBadge(val id: String, val title: String, val icon: String, val rarity: Rarity) {
    CHALLENGE_COMPLETE(
        "challenge_complete",
        "Challenge Complete",
        "🎯",
        Rarity.COMMON
    ), // Completar 1 reto
    RETO_MASTER(
        "reto_master",
        "Reto Master",
        "🏆",
        Rarity.RARE
    ), // Completar 3+ retos
    SPEED_DEMON(
        "speed_demon",
        "Speed Demon",
        "⚡",
        Rarity.EPIC
    ), // Completar reto en <7 días
}
```

### **Badge Calculation Logic (`BadgeCalculator.kt`)**

```kotlin
class BadgeCalculator(
    private val statsRepository: MyStatsRepository,
    private val database: StatsDatabase
) {
    
    suspend fun calculateNewBadges(userId: String): List<BadgeEntity> {
        val newBadges = mutableListOf<BadgeEntity>()
        val stats = statsRepository.fetchStatsFromFirestore()
        val previousBadges = database.badgeDao().getBadgesForUser(userId).firstOrNull() ?: emptyList()
        
        // Social Badges
        if (stats.totalMessages >= 50 && !hasBadge(previousBadges, "chatterbox")) {
            newBadges.add(createBadge("chatterbox", "Chatterbox", Rarity.COMMON))
        }
        
        if (stats.totalPosts >= 20 && !hasBadge(previousBadges, "wall_star")) {
            newBadges.add(createBadge("wall_star", "Wall Star", Rarity.COMMON))
        }
        
        // Check streak badges
        val streaks = database.streakDao().getStreaksForUser(userId).firstOrNull() ?: emptyList()
        val socialSharkStreak = streaks.find { it.streakType == "SOCIAL_DAYS" }
        if ((socialSharkStreak?.currentCount ?: 0) >= 3 && !hasBadge(previousBadges, "social_shark")) {
            newBadges.add(createBadge("social_shark", "Social Shark", Rarity.RARE))
        }
        
        // Athlete Badges
        if (stats.totalKm >= 100 && !hasBadge(previousBadges, "runner")) {
            newBadges.add(createBadge("runner", "Runner", Rarity.EPIC))
        }
        
        if (stats.totalEvents >= 5 && !hasBadge(previousBadges, "event_creator")) {
            newBadges.add(createBadge("event_creator", "Event Creator", Rarity.COMMON))
        }
        
        return newBadges
    }
    
    private fun createBadge(id: String, name: String, rarity: Rarity): BadgeEntity {
        return BadgeEntity(
            badgeId = id,
            name = name,
            description = getBadgeDescription(id),
            icon = getBadgeIcon(id),
            rarity = rarity.toString(),
            unlockedAt = System.currentTimeMillis(),
            isNew = true
        )
    }
    
    private fun hasBadge(badges: List<BadgeEntity>, badgeId: String): Boolean {
        return badges.any { it.badgeId == badgeId }
    }
    
    private fun getBadgeDescription(badgeId: String): String {
        return when (badgeId) {
            "social_shark" -> "Escribiste mensajes 3 días seguidos"
            "chatterbox" -> "Has escrito 50+ mensajes en comunidades"
            "wall_star" -> "Has publicado 20+ posts"
            "runner" -> "Has corrido 100+ km acumulados"
            "event_creator" -> "Has creado 5+ eventos"
            else -> "Badge desbloqueado"
        }
    }
    
    private fun getBadgeIcon(badgeId: String): String {
        return when (badgeId) {
            "social_shark" -> "🦈"
            "chatterbox" -> "💬"
            "wall_star" -> "⭐"
            "runner" -> "🏃"
            "event_creator" -> "📅"
            else -> "🏅"
        }
    }
}

enum class Rarity {
    COMMON,    // Gris
    RARE,      // Azul
    EPIC,      // Púrpura
    LEGENDARY  // Naranja/Dorado
}
```

---

## 📊 Gráficas

### **Gráfica 1: Actividad Semanal (Line Chart)**

Mostrar km corridos por día (últimos 7 días).

**Fuente:** `ActivityLogEntity` filtrado por tipo "RUN" y últimos 7 días.

**Componente: `WeeklyActivityChart.kt`**
```kotlin
@Composable
fun WeeklyActivityChart(
    activities: List<ActivityLogEntity>,
    modifier: Modifier = Modifier
) {
    // Usando Canvas nativo de Compose (sin librerías externas)
    // O agregar Vico library si se desea
    
    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        // Dibujar ejes
        // Dibujar puntos y líneas
        // Etiquetar días
    }
}
```

### **Gráfica 2: Eventos por Deporte (Pie Chart)**

Mostrar distribución de eventos por deporte.

**Fuente:** `Event` collection, contar por tipo de deporte.

### **Gráfica 3: Mensajes por Comunidad (Bar Chart)**

Mostrar actividad en comunidades.

**Fuente:** `ChannelMessage` collection, contar por community.

### **Gráfica 4: Progreso de Retos (Horizontal Bars)**

Mostrar progreso visual de retos activos.

**Fuente:** `Challenge.progressByUser[userId]`

**Componente: `ChallengeProgressBars.kt`**
```kotlin
@Composable
fun ChallengeProgressBars(challenges: List<Challenge>) {
    Column {
        challenges.forEach { challenge ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = challenge.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
                
                LinearProgressIndicator(
                    progress = challenge.progress / 100f,
                    modifier = Modifier
                        .weight(2f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                
                Text(
                    text = "${challenge.progress.toInt()}%",
                    modifier = Modifier
                        .width(40.dp)
                        .padding(start = 8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
```

---

## 🖼️ **Nueva Vista: MyStatsScreen**

### **Estructura Completa**

```kotlin
@Composable
fun MyStatsScreen(
    viewModel: MyStatsViewModelInterface,
    onNavigate: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val stats by viewModel.stats.collectAsState()
    val badges by viewModel.badges.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Actividad") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Sección: Indicador de sincronización
            item {
                SyncStatusBar(
                    isOnline = isOnline,
                    syncStatus = syncStatus,
                    lastSyncTime = stats?.lastSyncAt,
                    onSync = { viewModel.refreshStats(forceSync = true) },
                    isLoading = isLoading
                )
            }

            // Sección: Profile + Level
            item {
                ProfileLevelCard(
                    userName = "Juan Felipe", // Obtener del AuthViewModel
                    level = stats?.level ?: 1,
                    points = stats?.points ?: 0,
                    badges = badges.size
                )
            }

            // Sección: Badges
            item {
                BadgesSection(
                    badges = badges,
                    totalBadgesAvailable = 18,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Sección: Resumen de esta semana
            item {
                SummaryCardsSection(stats = stats)
            }

            // Sección: Gráficas
            item {
                ChartsSection(stats = stats, viewModel = viewModel)
            }

            // Sección: Acciones
            item {
                ActionsSection(
                    onSync = { viewModel.refreshStats(forceSync = true) },
                    onExport = { /* Exportar PDF */ },
                    isEnabled = isOnline && !isLoading
                )
            }
        }
    }
}
```

### **Componentes Secundarios**

**`SyncStatusBar.kt`**
```kotlin
@Composable
fun SyncStatusBar(
    isOnline: Boolean,
    syncStatus: String,
    lastSyncTime: Long?,
    onSync: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOnline) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isOnline) "🟢 Conectado" else "🔴 Sin conexión",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                
                if (lastSyncTime != null) {
                    Text(
                        text = "Última sincronización: ${formatRelativeTime(lastSyncTime)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (syncStatus == "SYNCING") {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Button(
                    onClick = onSync,
                    enabled = isOnline && !isLoading
                ) {
                    Text("Sincronizar")
                }
            }
        }
    }
}
```

**`ProfileLevelCard.kt`**
```kotlin
@Composable
fun ProfileLevelCard(
    userName: String,
    level: Int,
    points: Int,
    badges: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$points puntos acumulados",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Nivel $level",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                // Estrellas según nivel
                Row {
                    repeat(level) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_dialog_info),
                            contentDescription = "star",
                            tint = Color.Yellow,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Text(
                    text = "$badges badges",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
```

**`BadgesSection.kt`**
```kotlin
@Composable
fun BadgesSection(
    badges: List<BadgeEntity>,
    totalBadgesAvailable: Int,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🏅 Badges (${badges.size}/$totalBadgesAvailable)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(badges.size) { index ->
                    BadgeCard(badge = badges[index])
                }
                
                // Mostrar slots vacíos
                items(totalBadgesAvailable - badges.size) {
                    LockBadgeCard()
                }
            }
        }
    }
}
```

**`BadgeCard.kt`**
```kotlin
@Composable
fun BadgeCard(badge: BadgeEntity) {
    Card(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(
            containerColor = getRarityColor(badge.rarity)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = badge.icon,
                style = MaterialTheme.typography.headlineMedium
            )
            
            Text(
                text = badge.name,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                fontSize = 8.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
fun LockBadgeCard() {
    Card(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color.LightGray.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "🔒", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

fun getRarityColor(rarity: String): Color = when (rarity) {
    "COMMON" -> Color(0xFFD3D3D3)      // Gris
    "RARE" -> Color(0xFF1E88E5)        // Azul
    "EPIC" -> Color(0xFF7B1FA2)        // Púrpura
    "LEGENDARY" -> Color(0xFFFFA500)   // Naranja
    else -> Color.Gray
}
```

---

## 🗺️ **Navegación**

### **Cambios a `Screen.kt`**

```kotlin
sealed class Screen(val route: String) {
    // ... existing screens ...
    object MyStats : Screen("my_stats")
}
```

### **Cambios a `AppNavigation.kt`**

Agregar composable:
```kotlin
composable(Screen.MyStats.route) {
    MyStatsScreen(
        viewModel = hiltViewModel(),
        onNavigate = { route -> navController.navigate(route) },
        onNavigateBack = { navController.popBackStack() }
    )
}
```

### **Cambios a `MainScaffold.kt`**

Agregar dropdown en AppBar:
```kotlin
var showProfileMenu by remember { mutableStateOf(false) }

IconButton(onClick = { showProfileMenu = true }) {
    Icon(Icons.Default.Person, "profile")
}

DropdownMenu(
    expanded = showProfileMenu,
    onDismissRequest = { showProfileMenu = false }
) {
    DropdownMenuItem(
        text = { Text("Mi Perfil") },
        onClick = {
            onNavigate(Screen.Perfil.route)
            showProfileMenu = false
        }
    )
    
    DropdownMenuItem(
        text = { Text("Mi Actividad") },  // NUEVO
        onClick = {
            onNavigate(Screen.MyStats.route)
            showProfileMenu = false
        }
    )
    
    DropdownMenuItem(
        text = { Text("Cerrar Sesión") },
        onClick = {
            // Logout
            showProfileMenu = false
        }
    )
}
```

---

## 📦 **Dependencias a Agregar**

```gradle
// En app/build.gradle

dependencies {
    // Room Database
    implementation "androidx.room:room-runtime:2.5.2"
    implementation "androidx.room:room-ktx:2.5.2"
    kapt "androidx.room:room-compiler:2.5.2"

    // WorkManager
    implementation "androidx.work:work-runtime-ktx:2.8.1"

    // Coroutines
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1"

    // Connectivity Manager
    implementation "androidx.core:core:1.10.1"

    // Charts (Opcional, si se desea usar Vico)
    // implementation "com.patrykandpatrick.vico:compose:x.x.x"

    // Hilt para Dependency Injection
    implementation "com.google.dagger:hilt-android:2.46"
    kapt "com.google.dagger:hilt-compiler:2.46"
}
```

---

## 🛠️ **Pasos de Implementación**

### **Fase 1: Setup Básico (Día 1)**
- [ ] Crear estructura de carpetas
- [ ] Agregar dependencias en `build.gradle`
- [ ] Crear entidades Room (Badge, UserStats, ActivityLog, Streak)
- [ ] Crear DAOs

### **Fase 2: Database & Repository (Día 2)**
- [ ] Implementar `StatsDatabase`
- [ ] Crear `MyStatsRepository` con patrón cache-first
- [ ] Setup de `ConnectivityObserver`

### **Fase 3: WorkManager & Background Sync (Día 2-3)**
- [ ] Crear `SyncStatsWorker`
- [ ] Implementar periodic sync setup
- [ ] Crear `BadgeCalculator` y lógica de badges

### **Fase 4: ViewModel (Día 3)**
- [ ] Implementar `MyStatsViewModelInterface`
- [ ] Crear `MyStatsViewModel` con StateFlows
- [ ] Integrar con ConnectivityObserver

### **Fase 5: UI - Componentes Básicos (Día 4)**
- [ ] `SyncStatusBar`
- [ ] `ProfileLevelCard`
- [ ] `BadgeCard` + `BadgesSection`

### **Fase 6: UI - Gráficas (Día 4-5)**
- [ ] `WeeklyActivityChart`
- [ ] `ChallengeProgressBars`
- [ ] Componentes de pie/bar charts

### **Fase 7: UI - Screen Principal (Día 5)**
- [ ] `MyStatsScreen` completo
- [ ] Integrar todos los componentes

### **Fase 8: Navegación & Integration (Día 5-6)**
- [ ] Actualizar `Screen.kt`
- [ ] Agregar composable en `AppNavigation.kt`
- [ ] Modificar `MainScaffold.kt` para dropdown menu

### **Fase 9: Testing & Refinement (Día 6)**
- [ ] Test de sincronización offline
- [ ] Test de cálculo de badges
- [ ] UI polish y ajustes

---

## 🧪 **Testing Strategy**

### **Unit Tests**
```kotlin
// BadgeCalculatorTest
class BadgeCalculatorTest {
    @Test
    fun testSocialSharkBadgeUnlocked() {
        // Test que se desbloquea Social Shark con 3 días de streak
    }
    
    @Test
    fun testRunnerBadgeAt100Km() {
        // Test que se desbloquea Runner con 100+ km
    }
}

// MyStatsRepositoryTest
class MyStatsRepositoryTest {
    @Test
    fun testCacheFirstStrategy() {
        // Test que emite caché primero, luego sincroniza
    }
}
```

### **Integration Tests**
```kotlin
// WorkerTest
class SyncStatsWorkerTest {
    @Test
    fun testPeriodicSyncSucceeds() {
        // Test que WorkManager sincroniza correctamente
    }
}
```

### **UI Tests**
```kotlin
// MyStatsScreenTest
class MyStatsScreenTest {
    @Test
    fun testBadgesDisplayed() {
        // Test que se muestran los badges
    }
    
    @Test
    fun testSyncStatusIndicator() {
        // Test que el indicador de sync funciona
    }
}
```

---

## 📈 **Puntuación Esperada**

| Requisito | Puntos | Justificación |
|-----------|--------|---|
| Multi-threading (WorkManager + Coroutines) | 20 | Sincronización periódica en background, cálculo de badges con Coroutines |
| Local Storage (Room Database) | 20 | 4 entities, DAOs completos, persistencia de datos |
| Caching (Cache-first strategy) | 20 | Emitir caché local primero, sincronizar en background, StateFlow management |
| Eventual Connectivity (Sync + Offline) | 20 | ConnectivityManager, indicadores de sync, modo offline |
| Nueva Vista (MyStatsScreen) | 15 | Vista completa con badges, gráficas, métricas |
| **TOTAL** | **95** | — |

---

## 🚀 **Next Steps**

1. Crear rama: `git checkout -b feature/mystats-gamified`
2. Crear estructura de carpetas
3. Implementar en orden: DB → Repository → ViewModel → UI
4. Hacer commits frecuentes con PRs para code review
5. Final: Merge a `main` con testing completo

---

## 📚 **Referencias**

- [Room Database Guide](https://developer.android.com/training/data-storage/room)
- [WorkManager Guide](https://developer.android.com/topic/libraries/architecture/workmanager)
- [Connectivity Manager](https://developer.android.com/reference/android/net/ConnectivityManager)
- [Jetpack Compose StateFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)
- [MVVM Pattern in Android](https://developer.android.com/jetpack/guide)

---

**Última actualización:** Mayo 26, 2026
