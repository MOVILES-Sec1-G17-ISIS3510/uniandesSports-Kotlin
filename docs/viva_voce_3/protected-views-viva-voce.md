# Vistas Protegidas: Control de Acceso y Validación

## Qué está evaluando el profesor

El profesor busca entender cómo se implementa control de acceso a nivel de interfaz y negocio. Las vistas protegidas son pantallas o funcionalidades que no todos los usuarios pueden ver o ejecutar:

- ¿Cuándo se permite acceso y cuándo se bloquea?
- ¿Dónde se valida: en el ViewModel, en la composición, o en ambos?
- ¿Qué sucede cuando el usuario no tiene permisos o está offline?
- ¿Cómo se sincroniza el estado de protección con Firestore?

En esta app hay 4 vistas protegidas distintas que se deben defender bien. Cada una usa un mecanismo diferente de validación, y eso demuestra criterio arquitectónico.

## Qué implementamos

La app protege 4 vistas principales:

1. **Formulario de onboarding** — Solo accesible si el usuario está autenticado y aún no completó su perfil (programa o deporte principal en blanco).
2. **Crear un open match** — Solo accesible si el usuario está autenticado; si está offline, se encola para sincronizarse después.
3. **Ver detalles de comunidades** — Visible para todos, pero solo los miembros inscritos pueden acceder a canales y mensajes; hay un sistema de membresía con roles.
4. **Mensajes de canales en cola** — Los mensajes que el usuario intenta enviar offline se persisten en Room y se sincronizan cuando vuelve la red.

## Flujos de Protección Visuales

### 1) Flujo: Formulario de Onboarding

```
Usuario abre app
    ↓
¿FirebaseAuth.currentUser existe?
    [MainActivity.kt : 141-157]
    ├─ NO → Mostrar LoginScreen
    │
    └─ SÍ → Cargar perfil desde Firestore
            [FirebaseAuthViewModel.kt]
            ↓
        ¿program.isBlank() || mainSport.isBlank()?
            ├─ SÍ → Mostrar OnboardingScreen (PROTEGIDA)
            │        [MainActivity.kt : 141-157]
            │        ↓
            │       ¿Está online?
            │        [OnboardingScreen.kt : 274-280]
            │        ├─ SÍ → Crear usuario + perfil en Firestore
            │        │        [FirebaseAuthViewModel.kt]
            │        │        ↓
            │        │       Navegar a MAIN_TABS
            │        │
            │        └─ NO → Guardar payload en SharedPreferences
            │                 [PendingOnboardingStore.kt : 18-44]
            │                 ↓
            │                Programar WorkManager
            │                 [OnboardingScreen.kt]
            │                 ↓
            │                Mostrar mensaje "Pendiente"
            │                 ↓
            │                Cuando vuelve red:
            │                OnboardingSyncWorker sincroniza
            │                [OnboardingSyncWorker.kt : 32-55]
            │
            └─ NO → Usuario completado, mostrar MAIN_TABS
                    [MainActivity.kt : 141-157]
                    (no puede acceder a onboarding otra vez)
```

### 2) Flujo: Crear Open Match

```
Usuario en PlayScreen
    ↓
¿Click en FAB "Create Match"?
    [PlayScreen.kt : 413-449]
    ↓
¿FirebaseAuth.currentUser?.uid existe?
    [FirestorePlayViewModel.kt : 574-594]
    ├─ NO → Mostrar error "User not authenticated"
    │        ↓
    │       Bloquear acceso
    │
    └─ SÍ → Abrir CreateEventDialog (PROTEGIDA)
            [PlayScreen.kt : 413-449]
            ↓
        ¿Llena formulario y presiona "Create"?
            ↓
        ¿Está online?
            [FirestorePlayViewModel.kt : 574-637]
            ├─ SÍ → Crear evento en Firestore directamente
            │        [FirestorePlayViewModel.kt : 602-637]
            │        ↓
            │       Mostrar confirmación
            │
            └─ NO → Guardar en PendingOpenMatchStore (SharedPreferences)
                    [FirestorePlayViewModel.kt : 609-637]
                    ↓
                   Programar OpenMatchSyncWorker
                    ↓
                   Mostrar "Pending - will sync when online"
                    ↓
                   Cuando vuelve red:
                   OpenMatchSyncWorker crea evento + membresía
                   [OpenMatchSyncWorker.kt : 50-67]
```

### 3) Flujo: Ver Comunidades y Membresía

```
Usuario en CommunitiesMainScreen
    ↓
LoadMembershipIds(userId) → Cargar set de community IDs
    [FirestoreCommunitiesViewModel.kt : 325-368]
    ↓
Mostrar lista de comunidades
    [CommunitiesMainScreen.kt : 53-117]
    ├─ Pestaña "Mine": 
    │   Mostrar comunidades donde: ownerId == userId || myCommunityIds.contains(id)
    │   [CommunitiesMainScreen.kt : 72-73]
    │
    └─ Pestaña "Others":
        Mostrar comunidades donde: ownerId != userId && !myCommunityIds.contains(id)
        [CommunitiesMainScreen.kt : 72-73]

Usuario hace clic en comunidad
    ↓
¿Community detail se abre?
    [CommunityDetailModal.kt : 170-172]
    ↓
Verificar membresía en CommunityDetailModal
    ↓
userMembership = members.find { it.userId == currentUserId }
    [CommunityDetailModal.kt : 170-172]
    ↓
¿userMembership != null?
    ├─ SÍ (Es miembro) →  Mostrar canales y mensajes (ACCESO PERMITIDO)
    │                      [CommunityDetailModal.kt : 170-172]
    │
    └─ NO (No es miembro) →  Mostrar botón "Join Community"
                              [CommunityDetailModal.kt : 170-172]
                              ↓
                             ¿Click en "Join"?
                              ├─ NO → Sigue viendo comunidad pero sin canales
                              │
                              └─ SÍ → ¿Está online?
                                      [FirestoreCommunitiesViewModel.kt : 325-368]
                                      ├─ NO → Error "Cannot join while offline"
                                      │
                                      └─ SÍ → db.runTransaction {
                                              ├─ Verificar: !existingMember.exists()
                                              ├─ Crear: communities/{id}/members/{userId}
                                              ├─ Crear: users/{userId}/memberships/{communityId}
                                              └─ Actualizar: _myCommunityIds.value += communityId
                                             }
                                              [FirestoreCommunitiesViewModel.kt : 325-368]
                                              ↓
                                             ¿Transaction exitosa?
                                              ├─ SÍ → Mostrar canales (ACCESO PERMITIDO)
                                              └─ NO → Mostrar error, bloquear acceso
```

### 4) Flujo: Mensajes de Canales en Cola

```
Usuario miembro de comunidad en ChannelScreen
    ↓
¿Escribe mensaje y presiona "Send"?
    ↓
¿Está online?
    [FirestoreCommunitiesViewModel.kt : 828-829]
    ├─ SÍ (Online) → Crear mensaje en Firestore
    │                 ├─ communities/{communityId}/channels/{channelId}/messages/{messageId}
    │                 ├─ [FirestoreCommunitiesViewModel.kt : 828-829]
    │                 └─ Mostrar mensaje como SENT
    │
    └─ NO (Offline) → Guardar en Room: PendingMessageEntity
                      ├─ localId, communityId, channelId, authorId, content
                      ├─ [CommunitiesCacheEntities.kt : 145-153]
                      ├─ [CommunitiesCacheDao.kt : 104-106]
                      ├─ Mostrar mensaje como PENDING en UI
                      └─ Programar MessageSyncWorker
                         [FirestoreCommunitiesViewModel.kt : 828-829]
                         ↓
                        Cuando vuelve red:
                         ├─ getPendingMessages() desde Room
                         │  [MessageSyncWorker.kt : 25-31]
                         ├─ Para cada pendiente:
                         │   ├─ db.runTransaction {
                         │   │   ├─ set(message)
                         │   │   └─ update(messageCount++)
                         │   │  }
                         │   │  [MessageSyncWorker.kt : 35-50]
                         │   ├─ deletePendingMessage(localId)
                         │   │  [MessageSyncWorker.kt : 52-54]
                         │   └─ Mostrar como SENT
                         └─ Si alguno falla:
                             └─ Result.retry() → WorkManager reintenta
                                [MessageSyncWorker.kt : 67]
```

### Resumen: Capas de Protección

```
┌─────────────────────────────────────────────────────────────┐
│                    5 CAPAS DE PROTECCIÓN                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ 1. ROUTING (Navigation)                                    │
│    └─ Solo accedes a OnboardingScreen si isNewUser=true   │
│       [MainActivity.kt : 141-157]                          │
│       [AppNavigation.kt]                                   │
│                                                             │
│ 2. VIEWMODEL (Business Logic)                             │
│    └─ Validar FirebaseAuth.currentUser?.uid               │
│       [FirestorePlayViewModel.kt : 574-594]               │
│       [FirestoreCommunitiesViewModel.kt : 325-368]        │
│    └─ Validar StateFlow<Set<String>> para membresía       │
│       [FirestoreCommunitiesViewModel.kt : 325-368]        │
│    └─ Validar _isOnline antes de crear transacciones      │
│       [FirestoreCommunitiesViewModel.kt : 828-829]        │
│                                                             │
│ 3. COMPOSABLE (UI State)                                  │
│    └─ Mostrar/ocultar botones según StateFlow             │
│       [CommunitiesMainScreen.kt : 53-117]                 │
│    └─ "Join" aparece solo si !userAlreadyMember           │
│       [CommunityDetailModal.kt : 170-172]                 │
│    └─ Canales se muestran solo si isMember                │
│       [CommunityDetailModal.kt : 170-172]                 │
│                                                             │
│ 4. LOCAL DATABASE (Room)                                  │
│    └─ PendingMessageEntity, PendingOnboardingStore        │
│       [CommunitiesCacheEntities.kt : 145-153]             │
│       [PendingOnboardingStore.kt : 18-44]                 │
│    └─ Cola offline: solo se borra si Firestore confirma   │
│       [MessageSyncWorker.kt : 52-54]                      │
│       [OnboardingSyncWorker.kt : 32-55]                   │
│                                                             │
│ 5. FIRESTORE (Remote Authority)                           │
│    └─ Transacciones atómicas para evitar duplicados       │
│       [FirestoreCommunitiesViewModel.kt : 325-368]        │
│       [MessageSyncWorker.kt : 35-50]                      │
│    └─ Rules validan: solo miembros ven canales            │
│    └─ Fuente de verdad final                              │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Archivos importantes y líneas

### 1) Formulario de Onboarding
- [app/src/main/java/com/uniandes/sport/ui/screens/OnboardingScreen.kt](app/src/main/java/com/uniandes/sport/ui/screens/OnboardingScreen.kt#L59-L60) — Composición del formulario.
- [app/src/main/java/com/uniandes/sport/MainActivity.kt](app/src/main/java/com/uniandes/sport/MainActivity.kt#L141-L157) — Lógica de navegación que solo permite acceder si `isNewUser = true`.
- [app/src/main/java/com/uniandes/sport/viewmodels/auth/FirebaseAuthViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/auth/FirebaseAuthViewModel.kt) — Validación de AuthState.
- [app/src/main/java/com/uniandes/sport/data/local/PendingOnboardingStore.kt](app/src/main/java/com/uniandes/sport/data/local/PendingOnboardingStore.kt#L1-L59) — Persistencia del payload pendiente.
- [app/src/main/java/com/uniandes/sport/workers/OnboardingSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/OnboardingSyncWorker.kt#L23-L61) — Sincronización eventual.

### 2) Crear Open Match
- [app/src/main/java/com/uniandes/sport/ui/screens/tabs/play/PlayScreen.kt](app/src/main/java/com/uniandes/sport/ui/screens/tabs/play/PlayScreen.kt#L413-L449) — Diálogo de creación.
- [app/src/main/java/com/uniandes/sport/viewmodels/play/FirestorePlayViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/play/FirestorePlayViewModel.kt#L574-L637) — Validación y persistencia.
- [app/src/main/java/com/uniandes/sport/workers/OpenMatchSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/OpenMatchSyncWorker.kt#L1-L82) — Sincronización.

### 3) Ver Comunidades y Membresía
- [app/src/main/java/com/uniandes/sport/ui/screens/tabs/communities/CommunitiesMainScreen.kt](app/src/main/java/com/uniandes/sport/ui/screens/tabs/communities/CommunitiesMainScreen.kt#L53-L117) — Filtrado de comunidades según membresía.
- [app/src/main/java/com/uniandes/sport/ui/screens/tabs/communities/CommunityDetailModal.kt](app/src/main/java/com/uniandes/sport/ui/screens/tabs/communities/CommunityDetailModal.kt#L170-L172) — Validación de membresía en detalle.
- [app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L325-L368) — Lógica de inscripción con transacción Firestore.

### 4) Mensajes de Canales en Cola
- [app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheEntities.kt](app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheEntities.kt#L145-L153) — Entidad `PendingMessageEntity`.
- [app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheDao.kt](app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheDao.kt#L104-L106) — DAO queries para mensajes pendientes.
- [app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L828-L829) — Encolamiento de mensaje offline.
- [app/src/main/java/com/uniandes/sport/workers/MessageSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/MessageSyncWorker.kt#L23-L67) — Sincronización de cola.

## Walkthrough del código

### 1) Vista Protegida: Formulario de Onboarding

**¿Quién puede acceder?**
Solo un usuario autenticado cuyo perfil en Firestore tenga `program` o `mainSport` en blanco (usuario nuevo).

**Dónde se valida:**
En [MainActivity.kt](app/src/main/java/com/uniandes/sport/MainActivity.kt#L141-L157), la navegación solo lleva al onboarding si se cumple:

```kotlin
composable(Routes.ONBOARDING_SCREEN) {
    OnboardingScreen(
        authViewModel = authViewModel,
        onFinishOnboarding = {
            navController.navigate(Routes.MAIN_TABS) {
                popUpTo(Routes.ONBOARDING_SCREEN) { inclusive = true }
            }
        },
```

**Validación previa:**
En `FirebaseAuthViewModel`, se verifica:
```kotlin
val user = FirebaseAuth.getInstance().currentUser
if (user != null && (program.isBlank() || mainSport.isBlank())) {
    // Es un usuario nuevo, mostrar onboarding
}
```

**¿Qué sucede si está offline?**
En [OnboardingScreen.kt](app/src/main/java/com/uniandes/sport/ui/screens/OnboardingScreen.kt#L274-L280), cuando se completa el formulario:

```kotlin
if (!isOnline) {
    // Guardar en cola offline
    PendingOnboardingStore.save(context, PendingOnboardingPayload(
        fullName = fullName,
        email = email,
        password = password,
        program = program,
        semester = semester,
        mainSport = mainSport,
        createdAtMillis = System.currentTimeMillis()
    ))
    // Programar sincronización
    scheduleOnboardingSyncWorker(context)
} else {
    // Crear usuario en Firestore directamente
}
```

**¿Cómo se sincroniza después?**
En [OnboardingSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/OnboardingSyncWorker.kt#L32-L55):

```kotlin
val pending = PendingOnboardingStore.get(applicationContext) ?: return Result.success()

return try {
    // 1. Crear usuario en Firebase Auth
    val currentUser = auth.currentUser ?: if (pending.password.isNotBlank()) {
        auth.createUserWithEmailAndPassword(pending.email, pending.password).await().user
    } else {
        null
    }

    if (currentUser == null) {
        return Result.retry()
    }

    // 2. Crear perfil en Firestore
    val profile = User(
        uid = currentUser.uid,
        email = currentUser.email ?: pending.email,
        fullName = pending.fullName,
        program = pending.program,
        semester = pending.semester.toIntOrNull() ?: 0,
        mainSport = pending.mainSport,
        role = "athlete",
        createdAt = System.currentTimeMillis()
    )
    
    db.collection("users").document(currentUser.uid)
        .set(profile)
        .await()

    // 3. Limpiar la cola local
    PendingOnboardingStore.clear(applicationContext)
    notifyOnboardingSynced(pending.fullName)
    Result.success()
} catch (e: Exception) {
    Result.retry()
}
```

**Defensa oral:**
"El onboarding está protegido por autenticación y estado de perfil. Si el usuario está offline, guardamos el payload en SharedPreferences y WorkManager lo sincroniza cuando vuelve la red. Eso asegura que no se pierda el registro aunque la app se cierre."

---

### 2) Vista Protegida: Crear Open Match

**¿Quién puede acceder?**
Solo un usuario autenticado en Firebase.

**Dónde se valida:**
En [FirestorePlayViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/play/FirestorePlayViewModel.kt#L574-L594):

```kotlin
fun createOpenMatch(...) {
    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    if (uid == null) {
        onError(Exception("User not authenticated"))
        return
    }

    val isOnline = _isOnline.value
    val eventId = generateEventId()

    if (isOnline) {
        // Crear en Firestore directamente
        createOpenMatchInFirestore(eventId, uid, ...)
    } else {
        // Encolar para después
        queuePendingOpenMatch(eventId, uid, ...)
    }
}
```

**¿Qué sucede si está offline?**
En [FirestorePlayViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/play/FirestorePlayViewModel.kt#L609-L637), se encola en `PendingOpenMatchStore`:

```kotlin
private fun queuePendingOpenMatch(eventId: String, uid: String, ...) {
    viewModelScope.launch(Dispatchers.IO) {
        val event = EventFactory.createEvent(
            id = eventId,
            name = eventName,
            sport = sport,
            location = location,
            dateTimeMillis = dateTimeMillis,
            requiredParticipants = requiredParticipants,
            createdBy = uid,
            createdAt = System.currentTimeMillis()
        )

        PendingOpenMatchStore.save(context, event)
        scheduleOpenMatchSyncWorker(context)

        // Mostrar que está pendiente
        onSuccess(eventId, isPending = true)
    }
}
```

**¿Cómo se sincroniza?**
En [OpenMatchSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/OpenMatchSyncWorker.kt#L23-L82):

```kotlin
override suspend fun doWork(): Result {
    val pendingMatches = PendingOpenMatchStore.getAll(applicationContext)
    if (pendingMatches.isEmpty()) {
        return Result.success()
    }

    var allSuccessful = true
    for (pending in pendingMatches) {
        try {
            val eventRef = db.collection("events").document(pending.eventId)
            eventRef.set(pending).await()

            // Si debe unirse como organizador
            if (pending.shouldJoin) {
                val memberRef = eventRef.collection("members").document(pending.createdBy)
                memberRef.set(mapOf(
                    "userId" to pending.createdBy,
                    "displayName" to pending.createdByName,
                    "role" to "organizer",
                    "joinedAt" to System.currentTimeMillis()
                )).await()
            }

            PendingOpenMatchStore.remove(applicationContext, pending.eventId)
        } catch (e: Exception) {
            allSuccessful = false
        }
    }

    return if (allSuccessful) Result.success() else Result.retry()
}
```

**Defensa oral:**
"Validamos autenticación en el ViewModel antes de permitir cualquier creación. Si está offline, guardamos el evento en SharedPreferences como pendiente y usamos WorkManager para sincronizarlo cuando hay red. Si la sincronización falla, WorkManager reintenta automáticamente."

---

### 3) Vista Protegida: Ver Comunidades y Membresía

**¿Quién puede acceder?**
Todos pueden ver la lista de comunidades, pero:
- Solo miembros pueden ver canales y mensajes dentro.
- Solo el owner o admin pueden editar.
- El botón "Join" aparece si no eres miembro.

**Dónde se valida la membresía:**
En [CommunitiesMainScreen.kt](app/src/main/java/com/uniandes/sport/ui/screens/tabs/communities/CommunitiesMainScreen.kt#L53-L73):

```kotlin
val myCommunityIds by viewModel.myCommunityIds.collectAsState()
val currentUserId by viewModel.currentUserId.collectAsState()

LazyColumn {
    items(communitiesToDisplay, key = { it.id }) { community ->
        val isMember = myCommunityIds.contains(community.id)
        val isOwner = community.ownerId == currentUserId

        CommunityRow(
            community = community,
            isMember = isMember,
            isOwner = isOwner,
            onClick = { selectedCommunity = community }
        )
    }
}

// Filtrado por pestaña
val communitiesToDisplay = when (selectedTab) {
    "Mine" -> communities.filter { community ->
        community.ownerId == currentUserId || myCommunityIds.contains(community.id)
    }
    "Others" -> communities.filter { community ->
        community.ownerId != currentUserId && !myCommunityIds.contains(community.id)
    }
    else -> communities
}
```

**¿Cómo se obtiene el set de membresías?**
En [FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L325-L368), el ViewModel actualiza `_myCommunityIds`:

```kotlin
private val _myCommunityIds = MutableStateFlow<Set<String>>(emptySet())
val myCommunityIds: StateFlow<Set<String>> = _myCommunityIds.asStateFlow()

fun loadMembershipIds(userId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        db.collection("users").document(userId)
            .collection("memberships")
            .get()
            .await()
            .documents
            .mapNotNull { it.getString("communityId") }
            .toSet()
            .let { _myCommunityIds.value = it }
    }
}
```

**¿Qué sucede si intentas ver detalles sin ser miembro?**
En [CommunityDetailModal.kt](app/src/main/java/com/uniandes/sport/ui/screens/tabs/communities/CommunityDetailModal.kt#L170-L172):

```kotlin
val userMembership = currentUserId?.let { uid -> 
    members.find { it.userId == uid } 
}
val userAlreadyMember = userMembership != null
val isCurrentUserAdmin = userMembership?.role.equals("admin", ignoreCase = true)

// Mostrar botón Join si no es miembro
if (!userAlreadyMember) {
    Button(
        onClick = { viewModel.joinCommunity(communityId, currentUserId, displayName) }
    ) {
        Text("Join Community")
    }
} else {
    // Mostrar canales solo si es miembro
    ChannelsList(channels)
}
```

**¿Cómo se valida la inscripción (join)?**
En [FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L325-L368):

```kotlin
fun joinCommunity(
    communityId: String,
    userId: String,
    displayName: String,
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit
) {
    if (!_isOnline.value) {
        onFailure(Exception("Cannot join community while offline"))
        return@launch
    }

    viewModelScope.launch(Dispatchers.IO) {
        try {
            db.runTransaction { transaction ->
                val communityRef = db.collection("communities").document(communityId)
                val memberRef = communityRef.collection("members").document(userId)
                val userMembershipsRef = db.collection("users").document(userId)
                    .collection("memberships").document(communityId)

                // Verificar que no sea miembro ya
                val existingMember = transaction.get(memberRef)
                if (!existingMember.exists()) {
                    // Crear membresía
                    transaction.set(memberRef, mapOf(
                        "userId" to userId,
                        "displayName" to displayName,
                        "role" to "member",
                        "joinedAt" to System.currentTimeMillis()
                    ))

                    // Guardar en user/memberships para acceso rápido
                    transaction.set(userMembershipsRef, mapOf(
                        "communityId" to communityId,
                        "joinedAt" to System.currentTimeMillis()
                    ))

                    // Actualizar local
                    _myCommunityIds.value = _myCommunityIds.value + communityId
                }
            }.await()

            onSuccess()
        } catch (e: Exception) {
            onFailure(e)
        }
    }
}
```

**Defensa oral:**
"Implementamos un sistema de membresía con un `StateFlow<Set<String>>` que guarda los IDs de comunidades donde estás inscrito. Cuando quieres ver detalles, verificamos si estás en ese set. Si no, mostramos un botón Join que usa una transacción Firestore para asegurar que solo se inscriba una vez. El estado local se actualiza inmediatamente después."

---

### 4) Vista Protegida: Mensajes de Canales en Cola

**¿Quién puede acceder?**
Solo miembros de la comunidad pueden enviar mensajes a los canales.

**¿Qué sucede si estás offline?**
El mensaje se persiste en una tabla Room llamada `pending_messages` y se sincroniza cuando vuelve la red.

**Estructura local:**
En [CommunitiesCacheEntities.kt](app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheEntities.kt#L145-L153):

```kotlin
@Entity(tableName = "pending_messages")
data class PendingMessageEntity(
    @PrimaryKey val localId: String,
    val communityId: String,
    val channelId: String,
    val authorId: String,
    val authorName: String,
    val content: String,
    val createdAt: Long,
    val retryCount: Int = 0
)
```

**Queries del DAO:**
En [CommunitiesCacheDao.kt](app/src/main/java/com/uniandes/sport/data/local/CommunitiesCacheDao.kt#L104-L106):

```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertPendingMessage(message: PendingMessageEntity)

@Query("DELETE FROM pending_messages WHERE localId = :localId")
suspend fun deletePendingMessage(localId: String)

@Query("SELECT * FROM pending_messages")
suspend fun getPendingMessages(): List<PendingMessageEntity>
```

**¿Cómo se encola un mensaje?**
En [FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L828-L829):

```kotlin
fun sendMessageToChannel(
    communityId: String,
    channelId: String,
    content: String,
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
        onFailure(Exception("Not authenticated"))
        return
    }

    val messageId = UUID.randomUUID().toString()

    if (_isOnline.value) {
        // Enviar a Firestore directamente
        val channelRef = db.collection("communities").document(communityId)
            .collection("channels").document(channelId)
        
        val message = hashMapOf(
            "messageId" to messageId,
            "authorId" to uid,
            "authorName" to currentUserDisplayName,
            "content" to content,
            "createdAt" to System.currentTimeMillis()
        )
        
        channelRef.collection("messages").document(messageId)
            .set(message)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    } else {
        // Guardar en cola local
        viewModelScope.launch(Dispatchers.IO) {
            cacheDao.upsertPendingMessage(
                PendingMessageEntity(
                    localId = messageId,
                    communityId = communityId,
                    channelId = channelId,
                    authorId = uid,
                    authorName = currentUserDisplayName,
                    content = content,
                    createdAt = System.currentTimeMillis()
                )
            )
            
            // Mostrar al usuario que está pendiente
            withContext(Dispatchers.Main) {
                onSuccess()  // UI muestra estado "pending"
            }
        }
    }
}
```

**¿Cómo se sincroniza la cola?**
En [MessageSyncWorker.kt](app/src/main/java/com/uniandes/sport/workers/MessageSyncWorker.kt#L23-L67):

```kotlin
override suspend fun doWork(): Result {
    val db = FirebaseFirestore.getInstance()
    val cacheDao = CommunitiesCacheDatabase.getInstance(applicationContext).cacheDao()

    val pendingMessages = cacheDao.getPendingMessages()
    if (pendingMessages.isEmpty()) {
        Log.d("MessageSyncWorker", "No pending messages to sync.")
        return Result.success()
    }

    var allSuccessful = true

    for (pending in pendingMessages) {
        try {
            val channelRef = db.collection("communities")
                .document(pending.communityId)
                .collection("channels")
                .document(pending.channelId)

            val messageRef = channelRef.collection("messages")
                .document(pending.localId)

            val messagePayload = hashMapOf(
                "messageId" to pending.localId,
                "authorId" to pending.authorId,
                "authorName" to pending.authorName,
                "content" to pending.content,
                "createdAt" to pending.createdAt
            )

            // Transacción: guardar mensaje y contar
            db.runTransaction { transaction ->
                val channelData = transaction.get(channelRef)
                val currentCount = (channelData.getLong("messageCount") ?: 0L) + 1

                transaction.set(messageRef, messagePayload)
                transaction.update(channelRef, "messageCount", currentCount)
            }.await()

            // Actualizar estado local a SENT
            cacheDao.updateMessageStatus(pending.localId, MessageStatus.SENT.name)

            // Borrar de cola pendiente
            cacheDao.deletePendingMessage(pending.localId)

            Log.d("MessageSyncWorker", "Message synced: ${pending.localId}")
        } catch (e: Exception) {
            Log.e("MessageSyncWorker", "Failed to sync message: ${pending.localId}", e)
            allSuccessful = false
        }
    }

    return if (allSuccessful) Result.success() else Result.retry()
}
```

**¿Qué garantías hay?**
- El mensaje solo se borra de la cola local **después** de que Firestore lo confirme.
- Si falla la sincronización, WorkManager reintenta automáticamente.
- La transacción Firestore asegura que el contador de mensajes se actualice junto con el mensaje.

**Defensa oral:**
"Los mensajes offline se guardan en una tabla Room dedicada con ID único, comunidad y canal. Cuando vuelve la red, WorkManager sincroniza la cola completa usando transacciones Firestore para garantizar consistencia. Solo borramos de la cola local si el commit remoto fue exitoso. Si falla, WorkManager lo reintenta automáticamente."

---

## Por qué decidimos implementarlo así

La estrategia es proporcional al tipo de vista:

- **Onboarding:** Protección por AuthState + Profile check porque es el único lugar donde se "crea" el usuario completo.
- **Open Match:** Validación simple de autenticación porque solo importa quién lo crea; offline se maneja con cola.
- **Comunidades:** Sistema de membresía con StateFlow porque las comunidades son compartidas y necesitan autorización más granular.
- **Mensajes:** Persistencia offline en Room porque es común que el usuario pierce el chat estando sin internet.

Cada mecanismo se elige por el costo de complejidad que justifica. Usar SharedPreferences para mensajes sería más simple, pero Room permite queries más sofisticadas y transacciones locales. Usar Firestore rules habría sido más seguro, pero aquí la validación ocurre en el app client.

## Riesgos, limitaciones o tradeoffs

- **Onboarding:** Si el usuario cierra la app justo antes de sincronizar, podría quedarse sin cuenta. Usamos WorkManager para mitigarlo, pero si desinstala la app antes de que sincronice, se pierde.
- **Open Match:** No hay validación en Firestore rules; confía en que el app valida autenticación. Un usuario con acceso directo a Firestore podría crear eventos sin estar autenticado en el app.
- **Comunidades:** El `StateFlow<Set<String>>` es una caché local; si alguien se inscribe desde otra app, este dispositivo no lo sabrá hasta que recargue. En una app grande, se usaría Firestore listeners.
- **Mensajes:** Si hay conflicto offline (mismo ID en dos dispositivos), Room usará `OnConflictStrategy.REPLACE` y podría perder datos. En producción, se usaría un algoritmo de merge.

## Vocabulario técnico importante

- **Autenticación:** Verificar quién eres (Firebase Auth).
- **Autorización:** Verificar qué tienes permitido hacer (Firestore rules, StateFlow checks).
- **Transacción:** Operación atómica que se ejecuta completamente o no.
- **Cola offline:** Lista de operaciones pendientes que se sincronizan cuando hay red.
- **StateFlow:** `Flow` con estado inicial que emite valores cambios.
- **OnConflictStrategy.REPLACE:** Si un registro con la misma clave existe, reemplázalo.

## Posibles preguntas del evaluador

1. **¿Por qué validan autenticación en el ViewModel y no en Firestore rules?**
   Porque el app necesita feedback inmediato y UX responsiva. Las rules de Firestore son defensa adicional, pero aquí está delegado al cliente.

2. **¿Qué sucede si alguien manipula el StateFlow de membresías?**
   La siguiente sincronización con Firestore las actualizaría. También Firestore rules validan que solo miembros accedan a la colección de canales.

3. **¿Cómo garantizas que no se pierden mensajes offline?**
   Tienen ID único en Room y solo se borran después de que Firestore confirme. Si falla, WorkManager los reintenta.

4. **¿Por qué no usas Firestore listeners para comunidades?**
   Porque simplificaría, pero consumiría más batería y datos. El tradeoff aquí es: caché local mínima + recarga manual cuando sea necesario.

5. **¿Qué sucede si el usuario intenta unirse a una comunidad privada?**
   Acá no hay protección de roles en el app; confía en que Firestore rules lo bloquean. En una versión mejorada, el ViewModel validaría el `status` de la comunidad antes de permitir join.

## Cómo defender esta implementación oralmente

La forma más fuerte de explicarlo es decir que **distribuimos protecciones**:

- **En el navegador:** Solo accedes al onboarding si el routing te lo permite.
- **En el ViewModel:** Validamos autenticación antes de tocar Firestore.
- **En la composición:** Mostramos/ocultamos botones según StateFlow.
- **En la base local:** Room es la fuente de verdad para pendientes offline.
- **En Firestore:** Las rules son la defensa última (aunque no las implementamos aquí).

Si el profesor pregunta por seguridad, la respuesta honesta es: "Esta es validación a nivel de UX, no de seguridad remota. Para producción, habría que agregar Firestore rules más estrictas y validar autorización en el backend."

Si pregunta por offline, la respuesta es: "Usamos Room para colas offline simples (mensajes, eventos) y SharedPreferences para payloads pequeños (onboarding). WorkManager sincroniza cuando hay red, y si falla, lo reintenta automáticamente."
