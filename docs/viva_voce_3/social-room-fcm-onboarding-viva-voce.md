# Persistencia en Social y Onboarding

## Qué está evaluando el profesor
El profesor quiere ver si entendemos por qué cada mecanismo de persistencia existe y cómo encaja con MVVM, Firestore y Compose. En este módulo hay tres decisiones distintas que se deben defender bien:

- Room para cachear Social y evitar que la pantalla dependa totalmente de Firestore.
- SharedPreferences para guardar el estado local de suscripción a tópicos FCM en Social.
- SharedPreferences para guardar borradores y datos pendientes del onboarding.

La idea clave para oral es esta: no elegimos una sola tecnología para todo, sino la más adecuada para cada tipo de dato. Room maneja estructuras relacionales; SharedPreferences maneja estado pequeño y flags; y Firestore sigue siendo la fuente de verdad remota.

## Qué implementamos
### Social con Room
El módulo Social usa una base local de Room llamada `communities_cache.db` con varias entidades: comunidades, posts, canales, miembros, comentarios, memberships y colas pendientes. Esto permite mostrar contenido localmente antes de que Firestore termine de responder y también guardar acciones offline para sincronizarlas después.

### FCM en Social con SharedPreferences
En Social, el repo no guarda el token FCM en SharedPreferences. Lo correcto para defender es que:

- el token FCM se obtiene de Firebase Messaging,
- el token se sincroniza a Firestore en el usuario autenticado,
- y el estado local que sí persiste en SharedPreferences es el set de tópicos de comunidades suscritas (`community_topics`).

Ese estado local evita re-suscribirse a los mismos tópicos y hace idempotente la sincronización de notificaciones.

### Onboarding con SharedPreferences
El onboarding usa dos capas locales:

- `OnboardingDraftStore.kt` para guardar el progreso y los campos del formulario mientras el usuario avanza.
- `PendingOnboardingStore.kt` para guardar el payload completo cuando el registro queda pendiente por conectividad.

Luego `OnboardingSyncWorker.kt` reintenta crear la cuenta y subir el perfil cuando vuelve la red.

## Archivos importantes y líneas
- [app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheDatabase.kt](app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheDatabase.kt#L8-L36) — base Room de Social.
- [app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheDao.kt](app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheDao.kt#L8-L123) — consultas, upserts y colas pendientes de Social.
- [app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheEntities.kt](app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheEntities.kt#L16-L156) — entidades y mapeos de dominio para Social.
- [app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L59-L59) — `fcm_topics` como SharedPreferences local.
- [app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L124-L199) — lectura cache-first desde Room y escritura de vuelta a la caché local.
- [app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L1038-L1055) — sincronización de tópicos FCM con SharedPreferences.
- [app/src/main/java/com/uniandes/sport/MainActivity.kt](app/src/main/java/com/uniandes/sport/MainActivity.kt#L237-L248) — obtención del token FCM y sincronización a Firestore.
- [app/src/main/java/com/uniandes/sport/MyFirebaseMessagingService.kt](app/src/main/java/com/uniandes/sport/MyFirebaseMessagingService.kt#L54-L59) — `onNewToken()` y arranque de la persistencia del token.
- [app/src/main/java/com/uniandes/sport/MyFirebaseMessagingService.kt](app/src/main/java/com/uniandes/sport/MyFirebaseMessagingService.kt#L115-L128) — persistencia del token FCM en Firestore.
- [app/src/main/java/com/uniandes/sport/ui/screens/OnboardingDraftStore.kt](app/src/main/java/com/uniandes/sport/ui/screens/OnboardingDraftStore.kt#L5-L82) — drafts y progreso del onboarding con SharedPreferences.
- [app/src/main/java/com/uniandes/sport/data/local/PendingOnboardingStore.kt](app/src/main/java/com/uniandes/sport/data/local/PendingOnboardingStore.kt#L1-L59) — payload pendiente del onboarding en SharedPreferences.
- [app/src/main/java/com/uniandes/sport/workers/OnboardingSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/OnboardingSyncWorker.kt#L23-L61) — sincronización eventual del onboarding.

## Walkthrough del código
### 1) Room en Social: caché relacional y persistente

En [CommunitiesCacheDatabase.kt](app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheDatabase.kt#L8-L36) se define la base local de Social:

```kotlin
@Database(
    entities = [
        CachedCommunityEntity::class,
        CachedPostEntity::class,
        CachedChannelEntity::class,
        CachedMemberEntity::class,
        CachedPostCommentEntity::class,
        CachedMembershipEntity::class,
        CachedChannelMessageEntity::class,
        PendingMessageEntity::class,
        PendingPostEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class CommunitiesCacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CommunitiesCacheDao
```

La defensa oral aquí es que Room actúa como una capa de cache persistente sobre SQLite. No estamos usando SQLite a mano; Room nos da DAO, entidades y consultas tipadas. Además, incluir `PendingMessageEntity` y `PendingPostEntity` en la base hace que Social no solo cachee lectura, sino también escritura offline.

El DAO de [CommunitiesCacheDao.kt](app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheDao.kt#L8-L123) concentra la lógica local:

```kotlin
@Query("SELECT * FROM cached_communities ORDER BY name ASC")
suspend fun getCachedCommunities(): List<CachedCommunityEntity>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertCommunities(items: List<CachedCommunityEntity>)

@Query("SELECT * FROM cached_posts WHERE communityId = :communityId ORDER BY createdAt DESC")
suspend fun getPostsByCommunity(communityId: String): List<CachedPostEntity>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertPendingMessage(message: PendingMessageEntity)

@Query("SELECT * FROM pending_messages")
suspend fun getPendingMessages(): List<PendingMessageEntity>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertPendingPost(post: PendingPostEntity)

@Query("SELECT * FROM pending_posts")
suspend fun getPendingPosts(): List<PendingPostEntity>
```

Esto permite dos patrones: lectura cache-first y cola offline duradera. Es una buena decisión porque no obliga al usuario a volver a cargar todo desde la red y permite reintentos controlados.

Las entidades de [CommunitiesCacheEntities.kt](app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheEntities.kt#L16-L156) muestran cómo se modela el cache:

```kotlin
@Entity(tableName = "cached_communities")
data class CachedCommunityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val sport: String,
    val description: String,
    val memberCount: Int,
    val channelCount: Int,
    val ownerId: String,
    val cachedAt: Long
)

@Entity(tableName = "cached_posts", primaryKeys = ["communityId", "postId"])
data class CachedPostEntity(
    val communityId: String,
    val postId: String,
    val author: String,
    val role: String,
    val content: String,
    val time: String,
    val pinned: Boolean,
    val likes: Int,
    val createdAt: Long,
    val cachedAt: Long,
    val status: String = MessageStatus.SENT.name
)
```

La defensa aquí es que cada tabla guarda `cachedAt` para poder razonarlo como un cache y no como la fuente de verdad. En mensajes, además, se serializan mapas JSON porque Room no guarda estructuras complejas directamente.

En el ViewModel de comunidades, la cache local se consume antes que Firestore. En [FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L124-L199) se ve el patrón:

```kotlin
viewModelScope.launch(Dispatchers.IO) {
    val cached = cacheDao.getCachedCommunities().map { it.toModel() }
    withContext(Dispatchers.Main) {
        if (cached.isNotEmpty()) _communities.value = cached
    }
}
```

Y luego, cuando llega la red:

```kotlin
withContext(Dispatchers.IO) {
    val snapshot = db.collection("communities").get().await()
    snapshot.documents.mapNotNull { doc ->
        val c = doc.toObject(Community::class.java)
        c?.copy(id = doc.id)
    }
}

launch(Dispatchers.IO) {
    cacheDao.clearCommunities()
    cacheDao.upsertCommunities(loadedCommunities.map { it.toEntity() })
}
```

Ese flujo es el argumento más fuerte para defender Room en Social: la interfaz ve datos al instante y la persistencia local se actualiza en segundo plano.

### 2) SharedPreferences para el estado FCM en Social

El repositorio **no** guarda el token FCM localmente. Lo que sí persiste en SharedPreferences es el estado de tópicos de comunidades. En [FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L59-L59) aparece la clave local:

```kotlin
private val topicPrefs = application.getSharedPreferences("fcm_topics", Context.MODE_PRIVATE)
```

Y la sincronización real se hace en [FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L1038-L1055):

```kotlin
private fun syncCommunityTopics(membershipIds: Set<String>) {
    val targetTopics = membershipIds.map { toCommunityTopic(it) }.toSet()
    val savedTopics = topicPrefs.getStringSet("community_topics", emptySet())?.toSet() ?: emptySet()

    val toSubscribe = targetTopics - savedTopics
    val toUnsubscribe = savedTopics - targetTopics

    toSubscribe.forEach { topic ->
        Firebase.messaging.subscribeToTopic(topic)
            .addOnFailureListener { e -> Log.e("FirestoreCommunities", "Topic subscribe failed: $topic", e) }
    }

    toUnsubscribe.forEach { topic ->
        Firebase.messaging.unsubscribeFromTopic(topic)
            .addOnFailureListener { e -> Log.e("FirestoreCommunities", "Topic unsubscribe failed: $topic", e) }
    }

    topicPrefs.edit().putStringSet("community_topics", targetTopics.toMutableSet()).apply()
}
```

La defensa oral correcta es esta: SharedPreferences se usa para recordar qué tópicos ya estaban suscritos, no para guardar el token en sí. Eso evita suscripciones duplicadas, hace la operación idempotente y mantiene el estado local mínimo.

El token FCM como tal se obtiene y se sincroniza a Firestore en [MainActivity.kt](app/src/main/java/com/uniandes/sport/MainActivity.kt#L237-L248):

```kotlin
private fun syncCurrentUserFcmToken() {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    Firebase.messaging.token
        .addOnSuccessListener { token ->
            if (token.isBlank()) return@addOnSuccessListener
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .set(
                    mapOf(
                        "fcmToken" to token,
                        "fcmTokens" to FieldValue.arrayUnion(token)
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
        }
```

Y en [MyFirebaseMessagingService.kt](app/src/main/java/com/uniandes/sport/MyFirebaseMessagingService.kt#L54-L59) y [MyFirebaseMessagingService.kt](app/src/main/java/com/uniandes/sport/MyFirebaseMessagingService.kt#L115-L128) ocurre lo mismo cuando llega un token nuevo:

```kotlin
override fun onNewToken(token: String) {
    super.onNewToken(token)
    Log.d("FCM", "New FCM token: $token")
    Firebase.messaging.subscribeToTopic("all")
        .addOnFailureListener { e -> Log.e("FCM", "Failed to subscribe to all topic", e) }
    persistTokenForCurrentUser(token)
}
```

```kotlin
private fun persistTokenForCurrentUser(token: String) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    FirebaseFirestore.getInstance()
        .collection("users")
        .document(uid)
        .set(
            mapOf(
                "fcmToken" to token,
                "fcmTokens" to FieldValue.arrayUnion(token)
            ),
            com.google.firebase.firestore.SetOptions.merge()
        )
}
```

Si te preguntan por qué no guardamos el token en SharedPreferences, la respuesta honesta es que este código no lo hace. El token vive como dato remoto de usuario en Firestore y lo local solo conserva la configuración de tópicos.

### 3) SharedPreferences para los datos del onboarding

El onboarding tiene persistencia de borrador en [OnboardingDraftStore.kt](app/src/main/java/com/uniandes/sport/ui/screens/OnboardingDraftStore.kt#L5-L82):

```kotlin
private const val ONBOARDING_DRAFT_PREFS = "onboarding_draft_prefs"

internal fun saveOnboardingProgress(
    context: Context,
    currentStep: Int,
    program: String,
    semester: String,
    mainSport: String
) {
    context.getSharedPreferences(ONBOARDING_DRAFT_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_ENTRY_MODE, MODE_SIGNUP)
        .putInt(KEY_CURRENT_STEP, currentStep)
        .putString(KEY_PROGRAM, program)
        .putString(KEY_SEMESTER, semester)
        .putString(KEY_MAIN_SPORT, mainSport)
        .apply()
}

internal fun restoreOnboardingProgress(
    context: Context,
    applyState: (currentStep: Int, program: String, semester: String, mainSport: String) -> Unit
) {
    val prefs = context.getSharedPreferences(ONBOARDING_DRAFT_PREFS, Context.MODE_PRIVATE)
    val currentStep = prefs.getInt(KEY_CURRENT_STEP, 1)
    val program = prefs.getString(KEY_PROGRAM, "").orEmpty()
    val semester = prefs.getString(KEY_SEMESTER, "").orEmpty()
    val mainSport = prefs.getString(KEY_MAIN_SPORT, "").orEmpty()

    applyState(currentStep, program, semester, mainSport)
}
```

La justificación aquí es simple: son campos pequeños de UI que deben sobrevivir a cierres involuntarios de la app. No merece una base relacional completa.

El store de onboarding pendiente en [PendingOnboardingStore.kt](app/src/main/java/com/uniandes/sport/data/local/PendingOnboardingStore.kt#L1-L59) guarda el payload completo que luego se reintenta sincronizar:

```kotlin
data class PendingOnboardingPayload(
    val localId: String = UUID.randomUUID().toString(),
    val fullName: String,
    val email: String,
    val password: String,
    val program: String,
    val semester: String,
    val mainSport: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

fun save(context: Context, payload: PendingOnboardingPayload) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_PENDING_ONBOARDING, payload.toJson().toString())
        .apply()
}
```

Eso se consume en [OnboardingSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/OnboardingSyncWorker.kt#L23-L61):

```kotlin
override suspend fun doWork(): Result {
    val pending = PendingOnboardingStore.get(applicationContext) ?: return Result.success()

    return try {
        val currentUser = auth.currentUser ?: if (pending.password.isNotBlank()) {
            auth.createUserWithEmailAndPassword(pending.email, pending.password).await().user
        } else {
            null
        }

        if (currentUser == null) {
            return Result.retry()
        }

        db.collection("users").document(currentUser.uid)
            .set(profile)
            .await()

        PendingOnboardingStore.clear(applicationContext)
        notifyOnboardingSynced(pending.fullName)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
```

La defensa oral aquí es que SharedPreferences sirve como cola simple, durable y fácil de leer para un único payload pendiente. Si el onboarding se interrumpe, no se pierde el avance del usuario.

## Por qué decidimos implementarlo así
La decisión es por granularidad:

- Room para Social porque hay muchas relaciones, listas y ordenamientos.
- SharedPreferences para tópicos FCM porque el dato es pequeño, booleano/conjunto y el objetivo es evitar duplicados.
- SharedPreferences para onboarding porque son estados ligeros o un payload pendiente único que luego se sincroniza.

También hay una separación importante entre persistencia y sincronización. Guardar localmente no significa que el dato sea definitivo; en Social y onboarding el dato local sirve para resiliencia y UX, pero Firestore sigue mandando en el backend.

## Riesgos, limitaciones o tradeoffs
- Room con `fallbackToDestructiveMigration` borra la caché si cambia el esquema.
- `SharedPreferences` no es ideal para datos complejos; por eso aquí solo se usa para sets, flags y payloads pequeños.
- El token FCM no se guarda localmente en este repo; si un evaluador pregunta por eso, hay que aclarar que se persiste en Firestore.
- El onboarding pendiente incluye datos sensibles como contraseña; eso hay que defenderlo como una decisión funcional, pero también reconocer que tiene riesgo y que en una versión futura debería endurecerse.
- Los tópicos FCM guardados localmente dependen de que el `Set<String>` no se corrompa ni se sobrescriba fuera del flujo esperado.

## Vocabulario técnico importante
- **Cache-first**: leer primero desde el almacenamiento local para dar respuesta inmediata.
- **Fuente de verdad**: backend o sistema que manda sobre el estado real.
- **Idempotencia**: repetir una operación sin producir duplicados; aquí se aplica a suscripciones FCM.
- **Cola offline**: lista persistente de acciones pendientes de sincronizar.
- **Fallback destructivo**: recrear la base local si el esquema cambia.
- **Payload pendiente**: paquete de datos que no pudo completarse y se reintenta luego.

## Posibles preguntas del evaluador
1. ¿Por qué usaron Room en Social y no SharedPreferences?
   Porque Social maneja comunidades, posts, canales, miembros y mensajes. Eso es relacional y requiere consultas ordenadas; SharedPreferences no escala para eso.

2. ¿Dónde guardan el token FCM?
   En este repo no se guarda en SharedPreferences. Se sincroniza a Firestore en el usuario autenticado. Lo local solo guarda el estado de tópicos suscritos para no repetir suscripciones.

3. ¿Por qué guardar `community_topics` en SharedPreferences?
   Porque es un dato pequeño que solo necesita recordar qué tópicos ya estaban suscritos. Es una solución simple, rápida y suficiente.

4. ¿Por qué guardar el onboarding pendiente en SharedPreferences?
   Porque es una cola de un solo payload o un draft muy pequeño. Guardarlo en una base más compleja sería innecesario.

5. ¿Qué pasa si se cae la app durante onboarding?
   El draft se restaura desde `OnboardingDraftStore` y, si ya se había creado un payload pendiente, `PendingOnboardingStore` permite reintentar la sincronización.

## Cómo defender esta implementación oralmente
La forma más fuerte de explicarlo es por capas:

- En Social usamos Room para cachear una vista compleja del dominio y soportar offline.
- En FCM usamos SharedPreferences solo para recordar el estado de tópicos, porque eso evita duplicados y hace la sincronización idempotente.
- En onboarding usamos SharedPreferences para no perder formularios y para reintentar un registro pendiente cuando vuelve la red.

Si el profesor insiste en el token FCM, conviene corregir la premisa: el token no vive en SharedPreferences en este código; se sincroniza en Firestore. Lo que sí vive localmente es el estado de suscripción, que es la parte que realmente necesita persistencia ligera.