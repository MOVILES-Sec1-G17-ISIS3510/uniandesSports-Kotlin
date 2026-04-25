# Implementation Plan - Screen Social (Kotlin Coroutines)

## Objetivo
Implementar y evidenciar estrategias de asincronía/multi-threading en la screen Social (Communities) cumpliendo la rúbrica:

1. Corrutina con un dispatcher (5 pts)
2. Múltiples corrutinas anidadas usando Input/Output (10 pts)
3. Una operación en Input/Output y una en Main (10 pts)

## Alcance técnico
Este plan se implementa sobre la arquitectura actual basada en:

- Compose para UI
- ViewModel + StateFlow para estado
- Firebase Firestore como origen remoto
- Room como caché local
- Kotlin Coroutines (viewModelScope, coroutineScope, async/await, withContext)

Archivos principales:

- `app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt`
- `app/src/main/java/com/uniandes/sport/ui/screens/tabs/communities/CommunitiesMainScreen.kt`
- `app/src/main/java/com/uniandes/sport/viewmodels/communities/CommunitiesViewModelInterface.kt`

---

## Fase 1 - Corrutina con dispatcher (5 pts)

### Objetivo
Ejecutar una tarea de datos fuera del hilo principal usando un dispatcher explícito.

### Implementación
Usar `viewModelScope.launch(Dispatchers.IO)` para cargar caché local al iniciar el ViewModel y luego publicar en UI.

```bash
init {
    viewModelScope.launch(Dispatchers.IO) {
        val cached = cacheDao.getCachedCommunities().map { it.toModel() }
        withContext(Dispatchers.Main) {
            if (cached.isNotEmpty()) _communities.value = cached
        }
    }
}
```

### Evidencia esperada
- Lectura de Room en `Dispatchers.IO`
- Publicación de estado en `Dispatchers.Main`

---

## Fase 2 - Múltiples corrutinas anidadas + IO (10 pts)

### Objetivo
Ejecutar tareas remotas independientes en paralelo (posts, channels, members), anidadas dentro de una corrutina principal.

### Implementación
En `loadCommunityDetails(communityId)`, abrir un `coroutineScope` y crear tres `async(Dispatchers.IO)`.

```bash
val remotePayload = coroutineScope {
    val postsDeferred = async(Dispatchers.IO) {
        db.collection("communities").document(communityId)
            .collection("posts").get().await()
            .documents.mapNotNull { doc ->
                val p = doc.toObject(Post::class.java)
                p?.copy(id = doc.id)
            }.sortedByDescending { it.createdAt }
    }

    val channelsDeferred = async(Dispatchers.IO) {
        db.collection("communities").document(communityId)
            .collection("channels").get().await()
            .documents.mapNotNull { doc ->
                val ch = doc.toObject(Channel::class.java)
                ch?.copy(id = doc.id)
            }
    }

    val membersDeferred = async(Dispatchers.IO) {
        db.collection("communities").document(communityId)
            .collection("members").get().await()
            .documents.mapNotNull { doc ->
                val m = doc.toObject(CommunityMember::class.java)
                m?.copy(id = doc.id)
            }
    }

    CommunityDetailsPayload(
        posts = postsDeferred.await(),
        channels = channelsDeferred.await(),
        members = membersDeferred.await()
    )
}
```

### Evidencia esperada
- Corrutina externa (`viewModelScope.launch { ... }`)
- Corrutinas internas (`async`) en paralelo
- Uso explícito de `Dispatchers.IO`

---

## Fase 3 - Una operación en IO y otra en Main (10 pts)

### Objetivo
Demostrar separación de responsabilidades por hilo:

- IO para acceso a datos
- Main para actualización de estado/UI

### Implementación
Primero cargar caché en IO, luego renderizar en Main:

```bash
val cachedPayload = withContext(Dispatchers.IO) {
    CommunityDetailsPayload(
        posts = cacheDao.getPostsByCommunity(communityId).map { it.toModel() },
        channels = cacheDao.getChannelsByCommunity(communityId).map { it.toModel() },
        members = cacheDao.getMembersByCommunity(communityId).map { it.toModel() }
    )
}

withContext(Dispatchers.Main) {
    if (cachedPayload.posts.isNotEmpty()) _posts.value = cachedPayload.posts
    if (cachedPayload.channels.isNotEmpty()) _channels.value = cachedPayload.channels
    if (cachedPayload.members.isNotEmpty()) _members.value = cachedPayload.members
}
```

Y luego publicar payload remoto también en Main:

```bash
withContext(Dispatchers.Main) {
    _posts.value = remotePayload.posts
    _channels.value = remotePayload.channels
    _members.value = remotePayload.members
}
```

### Evidencia esperada
- Bloque `withContext(Dispatchers.IO)`
- Bloque `withContext(Dispatchers.Main)`
- Estado reactivo que actualiza Compose sin bloquear UI

---

## Explicación de scopes

## 1) viewModelScope
- Vive mientras viva el ViewModel
- Se cancela automáticamente al destruirse el ViewModel
- Ideal para operaciones de negocio y datos de Social que deben sobrevivir recomposiciones de Compose

## 2) coroutineScope
- Scope local dentro de una corrutina suspendida
- Permite concurrencia estructurada
- Si falla una corrutina hija, se propaga el error correctamente al scope padre

## 3) LaunchedEffect (Compose)
- Scope atado al ciclo de vida de la composición
- Útil para disparar cargas iniciales y side effects desde la pantalla
- En Social se usa para arrancar `loadCommunities()` y carga de membresías

---

## Decisiones de implementación

1. **Cache-first + network-later**
- Se muestra contenido local inmediato
- Se refresca con Firebase al terminar
- Mejor UX en red lenta o inestable

2. **IO para datos, Main para estado**
- Evita bloquear el hilo principal
- Mantiene consistencia al publicar en StateFlow observado por Compose

3. **Paralelismo para colecciones independientes**
- Posts/channels/members no dependen entre sí
- `async/await` reduce tiempo total de carga

4. **Persistencia en segundo plano**
- El guardado en Room se hace en IO
- No retrasa la renderización de resultados remotos

---

## Plan de validación

1. Abrir Social con red lenta
- Esperar render rápido de caché
- Ver actualización posterior desde red

2. Abrir detalle de una comunidad
- Ver carga concurrente de posts/channels/members
- Confirmar que UI no se congela

3. Revisar logs y errores
- Verificar que excepciones no rompen la pantalla
- Confirmar que `_isLoading` cambia correctamente

4. Pruebas manuales de cancelación
- Navegar fuera de Social durante carga
- Confirmar cancelación limpia por lifecycle del ViewModel

---

## Entregable para sustentación

Mostrar en vivo o en video:

1. Código del caso 1 (dispatcher explícito)
2. Código del caso 2 (corrutinas anidadas + IO paralelo)
3. Código del caso 3 (IO + Main)
4. Explicación de scopes y por qué cada uno corresponde al requerimiento de la rúbrica

Con esto se cubren los 25 puntos solicitados en la screen Social.