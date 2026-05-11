# Almacenamiento local en Uniandes Sports

## Qué está evaluando el profesor
El profesor suele buscar dos cosas: que exista una estrategia de persistencia real y que el estudiante pueda justificar por qué se eligió cada mecanismo. En esta app, eso se traduce en defender una arquitectura híbrida: Room para caché relacional persistente, SharedPreferences y DataStore para estado pequeño y preferencias, archivos privados para exportaciones e historial multimedia, y cachés en memoria para reducir latencia.

También evalúa si la solución resuelve conectividad eventual. Aquí no se depende de un único backend: varias pantallas leen primero de almacenamiento local y luego sincronizan con Firestore cuando la red vuelve. Eso es lo que más conviene enfatizar oralmente.

## Qué implementamos
La app no usa SQLite manual con SQLiteOpenHelper ni SQLiteDatabase; la persistencia relacional está delegada a Room, que a su vez usa SQLite por debajo. Eso se ve en las dependencias de Gradle y en tres bases locales separadas por dominio: retos, profesores y comunidades.

Además, la app usa:

- SharedPreferences para filtros, drafts, banderas simples y colas de pendientes offline.
- DataStore de tipo preferences para preferencias tipadas del módulo de profesores.
- Archivos privados en filesDir para exportar snapshots JSON/TXT y guardar imágenes locales.
- Cachés en memoria para eventos, mensajes y sugerencias geográficas.
- WorkManager para sincronizar colas offline cuando vuelve la conectividad.

El diseño importante es este: la fuente de verdad remota sigue siendo Firestore, pero la experiencia de usuario se apoya en una capa local que evita pantallas vacías, reduce latencia y permite trabajar sin internet.

## Tabla de puntajes exacta
La rúbrica oficial de esta sección dice lo siguiente:

| Estrategia | Puntos |
|---|---|
| BD local relacional (ej. Room) | 10 |
| BD llave/valor (ej. Hive, RealmDB) | 5 |
| Archivos locales | 5 |
| Preferences / UserDefaults / DataStore / KeyChain | 5 |

> Puntaje: 20 pts

Además, la parte de caché suma puntos aparte:

| Estrategia | Puntos |
|---|---|
| Librerías de caché de imágenes (Glide / Picasso / NetworkCacheImage / Kingfisher / Coil) | 5 |
| Estructuras en memoria (LRU / SparseArray / ArrayMap / NSCache) — explicando bien la estructura, sus parámetros y decisiones de implementación | 10 |

## Qué tecnologías suman puntos en esta app
- Room suma los 10 puntos de BD relacional porque existe una implementación real con entidades, DAOs, bases de datos y repositorios locales.
- SharedPreferences suma dentro de BD llave/valor y también en preferencias, porque la app usa múltiples stores pequeños para estado y colas offline.
- DataStore suma los 5 puntos de Preferences porque existe un repositorio propio con `Flow` y API tipada.
- Archivos locales suman los 5 puntos porque la app escribe snapshots y reportes dentro de `filesDir`.
- Coil suma en la rúbrica de caché de imágenes, porque la app carga imágenes con Coil y usa archivos locales para facilitar su lectura desde disco.
- Las estructuras en memoria suman en la rúbrica de caché porque hay LRU y caches con TTL o límite de tamaño.

## Archivos importantes y líneas
- [app/build.gradle](app/build.gradle#L86-L90) y [app/build.gradle](app/build.gradle#L106): declara Room, DataStore, WorkManager y Coil.
- [app/src/main/java/com/uniandes/sport/data/local/RetosCacheDatabase.kt](app/src/main/java/com/uniandes/sport/data/local/RetosCacheDatabase.kt#L13-L33): base Room para retos, singleton thread-safe y fallback destructivo.
- [app/src/main/java/com/uniandes/sport/data/local/RetosCacheDao.kt](app/src/main/java/com/uniandes/sport/data/local/RetosCacheDao.kt#L13-L34): lecturas reactivas, snapshot, upsert y limpieza total.
- [app/src/main/java/com/uniandes/sport/data/local/RetosCacheEntities.kt](app/src/main/java/com/uniandes/sport/data/local/RetosCacheEntities.kt#L15-L63): entidad, serialización JSON y mapeo a modelo de dominio.
- [app/src/main/java/com/uniandes/sport/data/local/RetosLocalRepository.kt](app/src/main/java/com/uniandes/sport/data/local/RetosLocalRepository.kt#L13-L48): fachada local que oculta Room al ViewModel.
- [app/src/main/java/com/uniandes/sport/data/local/ProfesoresCacheDatabase.kt](app/src/main/java/com/uniandes/sport/data/local/ProfesoresCacheDatabase.kt#L8-L30): segunda base Room para profesores, reviews y reservas.
- [app/src/main/java/com/uniandes/sport/data/local/ProfesoresCacheDao.kt](app/src/main/java/com/uniandes/sport/data/local/ProfesoresCacheDao.kt#L8-L34): CRUD local para profesores, reviews y booking requests.
- [app/src/main/java/com/uniandes/sport/data/local/ProfesoresCacheEntities.kt](app/src/main/java/com/uniandes/sport/data/local/ProfesoresCacheEntities.kt#L10-L124): entidades de profesores, reviews y reservas con convertidores de dominio.
- [app/src/main/java/com/uniandes/sport/data/local/ProfesoresLocalRepository.kt](app/src/main/java/com/uniandes/sport/data/local/ProfesoresLocalRepository.kt#L8-L65): fachada local para el módulo de profesores.
- [app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheDatabase.kt](app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheDatabase.kt#L8-L36): base Room de comunidades, posts y colas pendientes.
- [app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheDao.kt](app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheDao.kt#L8-L121): consultas y colas offline de mensajes y posts.
- [app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheEntities.kt](app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheEntities.kt#L16-L156): entidades de comunidades, mensajes y pendientes.
- [app/src/main/java/com/uniandes/sport/data/preferences/ProfesoresPreferencesDataStore.kt](app/src/main/java/com/uniandes/sport/data/preferences/ProfesoresPreferencesDataStore.kt#L4-L72): DataStore tipado para preferencias de profesores.
- [app/src/main/java/com/uniandes/sport/data/local/RetosKeyValueStore.kt](app/src/main/java/com/uniandes/sport/data/local/RetosKeyValueStore.kt#L5-L90): key-value con SharedPreferences para filtros y drafts de retos.
- [app/src/main/java/com/uniandes/sport/data/local/ProfesoresKeyValueStore.kt](app/src/main/java/com/uniandes/sport/data/local/ProfesoresKeyValueStore.kt#L5-L88): key-value con SharedPreferences para filtros y borradores del coach.
- [app/src/main/java/com/uniandes/sport/data/local/PendingBookingStore.kt](app/src/main/java/com/uniandes/sport/data/local/PendingBookingStore.kt#L1-L73), [PendingReviewStore.kt](app/src/main/java/com/uniandes/sport/data/local/PendingReviewStore.kt#L1-L91), [PendingRetoActionStore.kt](app/src/main/java/com/uniandes/sport/data/local/PendingRetoActionStore.kt#L1-L80), [PendingOpenMatchStore.kt](app/src/main/java/com/uniandes/sport/data/local/PendingOpenMatchStore.kt#L1-L109), [PendingOnboardingStore.kt](app/src/main/java/com/uniandes/sport/data/local/PendingOnboardingStore.kt#L1-L70) y [PendingRunAiStore.kt](app/src/main/java/com/uniandes/sport/data/local/PendingRunAiStore.kt#L1-L80): colas offline guardadas en SharedPreferences.
- [app/src/main/java/com/uniandes/sport/data/local/AiHistoryStore.kt](app/src/main/java/com/uniandes/sport/data/local/AiHistoryStore.kt#L12-L112): historial local híbrido, metadata en SharedPreferences e imágenes en archivos.
- [app/src/main/java/com/uniandes/sport/data/local/RetosFileStorage.kt](app/src/main/java/com/uniandes/sport/data/local/RetosFileStorage.kt#L13-L82), [ProfesoresFileStorage.kt](app/src/main/java/com/uniandes/sport/data/local/ProfesoresFileStorage.kt#L11-L113) y [RunningFileStorage.kt](app/src/main/java/com/uniandes/sport/data/local/RunningFileStorage.kt#L8-L73): exportaciones a archivos privados.
- [app/src/main/java/com/uniandes/sport/MainActivity.kt](app/src/main/java/com/uniandes/sport/MainActivity.kt#L52-L233): persistencia del tema con SharedPreferences.
- [app/src/main/java/com/uniandes/sport/viewmodels/weather/WeatherViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/weather/WeatherViewModel.kt#L23-L90): cache de clima en SharedPreferences.
- [app/src/main/java/com/uniandes/sport/sensors/StepCounterManager.kt](app/src/main/java/com/uniandes/sport/sensors/StepCounterManager.kt#L14-L58): estado persistente del contador de pasos con SharedPreferences.
- [app/src/main/java/com/uniandes/sport/repositories/EventCacheRepository.kt](app/src/main/java/com/uniandes/sport/repositories/EventCacheRepository.kt#L17-L73): cache en RAM con TTL para eventos.
- [app/src/main/java/com/uniandes/sport/cache/MessageLRUCache.kt](app/src/main/java/com/uniandes/sport/cache/MessageLRUCache.kt#L20-L103) y [OpenMatchLocationCache.kt](app/src/main/java/com/uniandes/sport/cache/OpenMatchLocationCache.kt#L32-L255): estructuras LRU en memoria.
- [app/src/main/java/com/uniandes/sport/viewmodels/retos/FirestoreRetosViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/retos/FirestoreRetosViewModel.kt#L35-L46), [FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L123-L199) y [FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L509-L521): lectura cache-first y escritura de vuelta al almacenamiento local.
- [app/src/main/java/com/uniandes/sport/workers/BookingSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/BookingSyncWorker.kt#L1-L74), [ReviewSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/ReviewSyncWorker.kt#L1-L87), [RetoActionSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/RetoActionSyncWorker.kt#L1-L100), [OpenMatchSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/OpenMatchSyncWorker.kt#L1-L82), [OnboardingSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/OnboardingSyncWorker.kt#L1-L61), [MessageSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/MessageSyncWorker.kt#L1-L67), [PostSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/PostSyncWorker.kt#L1-L62), [RunAiSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/RunAiSyncWorker.kt#L1-L68), [TrackAnalysisSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/TrackAnalysisSyncWorker.kt#L1-L126) y [PoseAnalysisSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/PoseAnalysisSyncWorker.kt#L1-L93): sincronización eventual de colas locales hacia Firestore.

## Walkthrough del código
### 1) Room como caché relacional persistente
En [RetosCacheDatabase.kt](app/src/main/java/com/uniandes/sport/data/local/RetosCacheDatabase.kt#L13-L33) se define la base local de retos:

```kotlin
@Database(
    entities = [CachedRetoEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RetosCacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): RetosCacheDao

    companion object {
        @Volatile
        private var INSTANCE: RetosCacheDatabase? = null

        fun getInstance(context: Context): RetosCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RetosCacheDatabase::class.java,
                    "retos_cache.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

La defensa oral aquí es clara: Room me da una abstracción tipo ORM sobre SQLite, pero sin perder el control de queries SQL. El singleton con `@Volatile` y `synchronized` evita crear múltiples instancias de la base. El `fallbackToDestructiveMigration` acepta perder la caché si cambia el esquema, y eso es razonable porque aquí la base local no es la fuente de verdad.

El DAO de esa base está en [RetosCacheDao.kt](app/src/main/java/com/uniandes/sport/data/local/RetosCacheDao.kt#L13-L34):

```kotlin
@Query("SELECT * FROM cached_retos ORDER BY status ASC, participantsCount DESC, title ASC")
fun observeRetos(): Flow<List<CachedRetoEntity>>

@Query("SELECT * FROM cached_retos ORDER BY status ASC, participantsCount DESC, title ASC")
suspend fun getRetos(): List<CachedRetoEntity>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertRetos(items: List<CachedRetoEntity>)

@Query("DELETE FROM cached_retos")
suspend fun clearRetos()
```

Aquí hay dos patrones importantes: `Flow` para observación reactiva y funciones `suspend` para no bloquear el hilo principal. Eso permite que la UI se actualice sola cuando cambie la tabla.

La traducción entre dominio y persistencia está en [RetosCacheEntities.kt](app/src/main/java/com/uniandes/sport/data/local/RetosCacheEntities.kt#L15-L63):

```kotlin
@Entity(tableName = "cached_retos")
data class CachedRetoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sport: String,
    val difficulty: String,
    val type: String,
    val goalLabel: String,
    val createdBy: String,
    val status: String,
    val participantsJson: String,
    val participantsCount: Long,
    val progress: Double,
    val progressByUserJson: String,
    val createdAtMillis: Long,
    val startDateMillis: Long,
    val endDateMillis: Long,
    val cachedAt: Long
)
```

El punto fuerte para defender es que los campos complejos se serializan manualmente a JSON y los timestamps se convierten a `Long`. Eso evita depender de converters adicionales y deja la entidad lista para SQLite.

### 2) Repositorio local como fachada
En [RetosLocalRepository.kt](app/src/main/java/com/uniandes/sport/data/local/RetosLocalRepository.kt#L13-L48):

```kotlin
fun observeRetos(): Flow<List<Reto>> =
    dao.observeRetos().map { list -> list.map { it.toModel() } }

suspend fun replaceRetos(items: List<Reto>) {
    dao.clearRetos()
    dao.upsertRetos(items.map { it.toEntity() })
}
```

La idea arquitectónica es buena para la defensa: el ViewModel nunca habla con `Room` directamente, sino con un repositorio local que expone modelos de negocio. Eso mejora mantenibilidad y desacopla la UI de SQLite.

### 3) Persistencia local de profesores y comunidades
El bloque de profesores sigue la misma lógica en [ProfesoresCacheDatabase.kt](app/src/main/java/com/uniandes/sport/data/local/ProfesoresCacheDatabase.kt#L8-L30), [ProfesoresCacheDao.kt](app/src/main/java/com/uniandes/sport/data/local/ProfesoresCacheDao.kt#L8-L34) y [ProfesoresCacheEntities.kt](app/src/main/java/com/uniandes/sport/data/local/ProfesoresCacheEntities.kt#L10-L124). La diferencia es funcional: aquí se cachean profesores, reviews y booking requests, porque el módulo de profesores necesita leer y ordenar varias listas sin depender de la red.

En comunidades, la base [CommunitiesCacheDatabase.kt](app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheDatabase.kt#L8-L36) y el DAO [CommunitiesCacheDao.kt](app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheDao.kt#L8-L121) incluyen no solo caché de comunidades y posts, sino también colas pendientes:

```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertPendingMessage(message: PendingMessageEntity)

@Query("SELECT * FROM pending_messages")
suspend fun getPendingMessages(): List<PendingMessageEntity>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertPendingPost(post: PendingPostEntity)

@Query("SELECT * FROM pending_posts")
suspend fun getPendingPosts(): List<PendingPostEntity>
```

Eso es importante: aquí Room no solo cachea lectura, también actúa como cola duradera para sincronización eventual.

### 4) SharedPreferences para estado pequeño y colas offline
Los stores de retos y profesores usan SharedPreferences cuando el dato es pequeño y no requiere esquema relacional. Por ejemplo, [RetosKeyValueStore.kt](app/src/main/java/com/uniandes/sport/data/local/RetosKeyValueStore.kt#L5-L90) guarda filtros, búsqueda y borradores.

```kotlin
fun saveSelectedType(context: Context, type: String) {
    prefs(context).edit().putString(KEY_SELECTED_TYPE, type).apply()
}

fun getSelectedType(context: Context): String =
    prefs(context).getString(KEY_SELECTED_TYPE, "All") ?: "All"
```

La justificación oral es que no vale la pena usar una base de datos para dos o tres strings de interfaz. `apply()` escribe de forma asíncrona y evita bloquear el hilo principal.

El mismo patrón aparece en [MainActivity.kt](app/src/main/java/com/uniandes/sport/MainActivity.kt#L52-L233) para el tema global, en [WeatherViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/weather/WeatherViewModel.kt#L23-L90) para el cache del clima y en [StepCounterManager.kt](app/src/main/java/com/uniandes/sport/sensors/StepCounterManager.kt#L14-L58) para el offset diario de pasos.

### 5) DataStore para preferencias tipadas
[ProfesoresPreferencesDataStore.kt](app/src/main/java/com/uniandes/sport/data/preferences/ProfesoresPreferencesDataStore.kt#L4-L72) es la pieza más limpia de preferencias:

```kotlin
private val dataStore = PreferenceDataStoreFactory.create(
    produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) }
)

val preferencesFlow: Flow<ProfesoresUiPreferences> = dataStore.data.map { prefs ->
    ProfesoresUiPreferences(
        onlyVerified = prefs[KEY_ONLY_VERIFIED] ?: false,
        sortMode = prefs[KEY_SORT_MODE]?.let {
            runCatching { ProfesoresSortMode.valueOf(it) }.getOrDefault(ProfesoresSortMode.RATING)
        } ?: ProfesoresSortMode.RATING,
        showQuickContact = prefs[KEY_SHOW_QUICK_CONTACT] ?: true,
        showRecentRequests = prefs[KEY_SHOW_RECENT_REQUESTS] ?: true
    )
}
```

La defensa aquí es que DataStore es mejor que SharedPreferences para preferencias de UI cuando quieres `Flow`, consistencia y tipado más explícito. No se usa para entidades complejas, sino para cuatro preferencias del módulo de profesores.

### 6) Archivos locales para exportación e historial
[RetosFileStorage.kt](app/src/main/java/com/uniandes/sport/data/local/RetosFileStorage.kt#L13-L82), [ProfesoresFileStorage.kt](app/src/main/java/com/uniandes/sport/data/local/ProfesoresFileStorage.kt#L11-L113) y [RunningFileStorage.kt](app/src/main/java/com/uniandes/sport/data/local/RunningFileStorage.kt#L8-L73) escriben en `context.filesDir`, que es almacenamiento privado de la app.

Ejemplo de [RetosFileStorage.kt](app/src/main/java/com/uniandes/sport/data/local/RetosFileStorage.kt#L13-L82):

```kotlin
val file = File(baseDir(context), "retos_snapshot_${timestampSuffix()}.json")
file.writeText(payload.toString(2))
```

Esto se defiende como una decisión de auditoría y debugging, no de consulta. Los archivos sirven para exportar snapshots, no para filtrar o hacer joins.

### 7) Persistencia offline y sincronización
El flujo offline más importante está en [FirestoreRetosViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/retos/FirestoreRetosViewModel.kt#L35-L46) y [FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L123-L199).

En retos, el ViewModel hace cache-first y luego escribe de vuelta a Room:

```kotlin
viewModelScope.launch(Dispatchers.IO) {
    localRepository?.replaceRetos(list)
}

viewModelScope.launch(Dispatchers.IO) {
    appContext?.let { ctx ->
        RetosFileStorage.exportRetosSnapshot(ctx, list)
    }
}
```

En comunidades, el patrón es parecido: primero se lee Room, luego Firestore, y después se reescribe la caché local. Cuando hay acciones offline, las colas se guardan en Room y se sincronizan con WorkManager.

Ejemplo en [BookingSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/BookingSyncWorker.kt#L1-L74):

```kotlin
val pendingItems = PendingBookingStore.getAll(applicationContext)
for (pending in pendingItems) {
    val requestDoc = db.collection("coach_requests").document(pending.localId)
    requestDoc.set(request).await()
    PendingBookingStore.remove(applicationContext, pending.localId)
}
```

La idea técnica para defender es: guardar localmente primero, sincronizar después, y notificar al usuario cuando la operación realmente quedó publicada.

### 8) Cachés en memoria
[EventCacheRepository.kt](app/src/main/java/com/uniandes/sport/repositories/EventCacheRepository.kt#L17-L73) usa `MutableStateFlow` como cache de eventos con TTL de 5 minutos. [MessageLRUCache.kt](app/src/main/java/com/uniandes/sport/cache/MessageLRUCache.kt#L20-L103) usa un `LinkedHashMap` con orden de acceso para LRU. [OpenMatchLocationCache.kt](app/src/main/java/com/uniandes/sport/cache/OpenMatchLocationCache.kt#L32-L255) hace algo similar pero con límite de entradas y ajustes dinámicos por presión de memoria.

Eso no es almacenamiento persistente, pero sí suma en la rúbrica de caché y demuestra criterio técnico: memoria para lo caliente, disco para lo durable.

## Por qué decidimos implementarlo así
El criterio general es costo versus valor:

- Room se usa cuando hay estructura, relaciones y necesidad de consultas ordenadas o reactivas.
- SharedPreferences se usa cuando el dato es pequeño, clave-valor y el costo de una base sería innecesario.
- DataStore se usa cuando se quiere la ergonomía de `Flow` y un API más seguro para preferencias.
- Archivos se usan cuando el objetivo es exportar, auditar o guardar binarios / snapshots, no consultar.
- Memoria se usa cuando la velocidad importa más que la persistencia.

También hay una decisión de arquitectura clara: Firestore sigue siendo la fuente de verdad remota, pero la app no obliga a estar online para ver o completar flujos frecuentes. Eso mejora UX y es fácil de defender como conectividad eventual.

## Riesgos, limitaciones o tradeoffs
- `fallbackToDestructiveMigration` borra la caché local cuando cambia la versión del esquema. Es aceptable porque se trata de caché, pero implica perder datos locales si no se sincronizan a tiempo.
- Muchos stores usan JSON manual dentro de SharedPreferences. Eso simplifica la implementación, pero no da validación de esquema ni transacciones.
- Los caches en memoria desaparecen al matar el proceso. Sirven para rendimiento, no para persistencia.
- Los archivos en `filesDir` no tienen índices ni consultas; son útiles para exportación, no para lectura estructurada compleja.
- El cache del clima y los drafts de onboarding dependen de claves de texto plano; si cambian los nombres de las claves, hay que migrarlos manualmente.

## Vocabulario técnico importante
- Caché relacional persistente: datos guardados localmente en tablas con estructura y consultas SQL.
- Fuente de verdad: sistema del que se recupera el dato canónico; aquí sigue siendo Firestore.
- Conectividad eventual: el sistema tolera trabajar offline y sincroniza después.
- Fachada: repositorio local que oculta los detalles de Room al ViewModel.
- LRU: política que elimina lo menos usado recientemente.
- TTL: tiempo máximo de vida de un dato en caché.
- Deserialización manual: convertir JSON almacenado a objetos Kotlin sin converters externos.

## Posibles preguntas del evaluador
1. ¿Por qué usaron Room y no solo SharedPreferences?
   Room se usa cuando hay listas grandes, relaciones y necesidad de consultas ordenadas y reactivas; SharedPreferences solo sirve para pares clave-valor pequeños.

2. ¿Por qué el cache de retos se borra con migración destructiva?
   Porque no es la fuente de verdad. Si cambia el esquema, es mejor regenerar la caché desde Firestore que arrastrar datos potencialmente incompatibles.

3. ¿Qué parte de la app funciona sin internet?
   La lectura de retos, profesores, comunidades y varios estados de interfaz funciona con caché local; además hay colas offline para booking, reviews, retos, mensajes y onboarding.

4. ¿Por qué DataStore y no SharedPreferences para profesores?
   Porque DataStore da `Flow`, escritura más estructurada y mejor encaje con preferencias tipadas.

5. ¿Qué pasa con las imágenes del historial de IA?
   Se guardan como archivos privados en `filesDir/ai_history` y luego se consumen desde Coil o desde `FileProvider`, según el caso.

## Cómo defender esta implementación oralmente
La respuesta fuerte es esta: la app usa una estrategia de persistencia por capas.

- Si el dato es relacional y consultable, se guarda en Room.
- Si es una preferencia o un draft pequeño, se guarda en SharedPreferences o DataStore.
- Si es un snapshot o un archivo binario, se guarda en `filesDir`.
- Si solo acelera navegación, se guarda en memoria con LRU o TTL.
- Si el usuario crea contenido offline, la acción se encola y se sincroniza con WorkManager cuando vuelve la red.

Si el profesor pregunta por qué no unificamos todo en una sola tecnología, la respuesta es que eso sería menos eficiente y menos claro. Cada mecanismo se eligió por el tipo de dato, la frecuencia de acceso y el costo de complejidad que justifica.
