# Multithreading en Social: implementación, código y justificación

## Qué se implementó

En la parte de **Social** se implementó una estrategia asincrónica para que la pantalla de comunidades y sus detalles no bloqueen el hilo principal. La idea fue combinar:

- una corrutina con dispatcher explícito para cargar caché y monitorear conectividad,
- múltiples corrutinas anidadas usando `Dispatchers.IO` para cargar en paralelo posts, canales y miembros,
- una corrutina en `IO` para obtener datos y otra en `Main` para publicar el estado en UI.

Todo esto vive en el ViewModel de comunidades, porque ahí está la lógica de orquestación del estado que consume Compose.

## Cómo se implementó

### 1) Corrutina con dispatcher

**Archivo:** [FirestoreCommunitiesViewModel.kt](../../app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt)

**Líneas:** 124-142

El `init` del ViewModel arranca corrutinas con `viewModelScope.launch(Dispatchers.IO)` para leer caché local y observar conectividad sin bloquear la UI.

```kotlin
init {
    // A: corrutina con dispatcher.
    // Se carga cache de Room en IO para no bloquear el hilo principal.
    viewModelScope.launch(Dispatchers.IO) {
        val cached = cacheDao.getCachedCommunities().map { it.toModel() }
        // separacion IO/Main. La actualizacion de estado para UI se hace en Main.
        withContext(Dispatchers.Main) {
            if (cached.isNotEmpty()) _communities.value = cached
        }
    }

    // Monitor network connectivity
    viewModelScope.launch(Dispatchers.IO) {
        application.observeConnectivityAsFlow().collect { isConnected ->
            _isOnline.value = isConnected
            if (!isConnected) {
                // Store last online time when connection is lost
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                _lastOnlineTime.value = sdf.format(Date())
            } else {
                // When connection is restored, trigger sync of pending posts
                syncPendingPosts()
            }
        }
    }
}
```

### 2) Múltiples corrutinas anidadas usando I/O

**Archivo:** [FirestoreCommunitiesViewModel.kt](../../app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt)

**Líneas:** 254-318

En `loadCommunityDetails()` se lanza una corrutina padre con `viewModelScope.launch` y dentro se crea un `coroutineScope` con tres `async(Dispatchers.IO)` para ejecutar en paralelo la carga de posts, channels y members.

```kotlin
override fun loadCommunityDetails(communityId: String) {
    _isLoading.value = true
    viewModelScope.launch {
        try {
            // B: IO + Main.
            // Primero recuperamos cache local en IO y luego actualizamos estado en Main.
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

            // multiples corrutinas anidadas usando Input/Output.
            // Corrutina externa (viewModelScope.launch) + corrutinas internas async(IO) en paralelo.
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
                            val member = doc.toObject(CommunityMember::class.java)
                            member?.copy(
                                id = doc.id,
                                userId = if (member.userId.isBlank()) doc.id else member.userId,
                                displayName = if (member.displayName.isBlank()) "Miembro" else member.displayName
                            )
                        }.sortedBy { it.displayName.lowercase() }
                }

                CommunityDetailsPayload(
                    posts = postsDeferred.await(),
                    channels = channelsDeferred.await(),
                    members = membersDeferred.await()
                )
            }

            // resultado remoto se publica en Main para refrescar UI.
            withContext(Dispatchers.Main) {
                _posts.value = remotePayload.posts
                _channels.value = remotePayload.channels
                _members.value = remotePayload.members
            }

            // Persistencia de datos remotos en Room en IO para cache offline.
            launch(Dispatchers.IO) {
                cacheDao.clearPostsByCommunity(communityId)
                cacheDao.upsertPosts(remotePayload.posts.map { it.toEntity(communityId) })

                cacheDao.clearChannelsByCommunity(communityId)
                cacheDao.upsertChannels(remotePayload.channels.map { it.toEntity(communityId) })

                cacheDao.clearMembersByCommunity(communityId)
                cacheDao.upsertMembers(remotePayload.members.map { it.toEntity(communityId) })
            }

        } catch (e: Exception) {
            Log.e("FirestoreCommunities", "Error fetching community details", e)
        } finally {
            _isLoading.value = false
        }
    }
}
```

### 3) Corrutina I/O y una en Main

**Archivo:** [FirestoreCommunitiesViewModel.kt](../../app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt)

**Líneas:** 211-241

En `loadCommunities()` se usa primero `withContext(Dispatchers.IO)` para leer caché y Firestore, y luego `withContext(Dispatchers.Main)` para publicar el resultado en `StateFlow`.

```kotlin
override fun loadCommunities() {
    _isLoading.value = true
    viewModelScope.launch {
        try {
            val loadedCommunities = withContext(Dispatchers.IO) {
                // Rubrica (10): operacion de Input/Output en hilo IO.
                // Cache-first: si no hay datos en pantalla, mostrar cache local primero.
                if (_communities.value.isEmpty()) {
                    val cached = cacheDao.getCachedCommunities().map { it.toModel() }
                    withContext(Dispatchers.Main) {
                        if (cached.isNotEmpty()) _communities.value = cached
                    }
                }

                // Red/Firebase tambien se resuelve en IO.
                val snapshot = db.collection("communities").get().await()
                snapshot.documents.mapNotNull { doc ->
                    val c = doc.toObject(Community::class.java)
                    c?.copy(id = doc.id)
                }
            }

            // Rubrica (10): actualizacion de estado en Main (UI reactiva via StateFlow).
            withContext(Dispatchers.Main) {
                _communities.value = loadedCommunities
            }

            // Persistencia en background para no bloquear el render principal.
            launch(Dispatchers.IO) {
                cacheDao.clearCommunities()
                cacheDao.upsertCommunities(loadedCommunities.map { it.toEntity() })
            }

        } catch (e: Exception) {
            Log.e("FirestoreCommunities", "Error fetching communities", e)
        } finally {
            _isLoading.value = false
        }
    }
}
```

## Justificación de por qué se implementó así

### 1) Corrutina con dispatcher

Se usa `Dispatchers.IO` desde el inicio porque la lectura de Room y el monitoreo de conectividad son tareas que no deben competir con el render de Compose. Si se hicieran en Main, la pantalla podría congelarse cuando el usuario entra al módulo Social. La actualización posterior con `withContext(Dispatchers.Main)` garantiza que el `StateFlow` se publique en el hilo correcto para la UI.

### 2) Múltiples corrutinas anidadas usando I/O

La carga de `posts`, `channels` y `members` se separa en tres `async` porque son consultas independientes y se pueden resolver al mismo tiempo. Esto reduce la latencia percibida y evita que una consulta espere a la anterior. Además, `coroutineScope` mantiene concurrencia estructurada: si una falla, las demás no quedan huérfanas.

### 3) Corrutina I/O y una en Main

`loadCommunities()` sigue el patrón clásico de "leer en I/O, publicar en Main". Esto permite combinar cache-first con actualización remota sin bloquear la UI. Primero se intenta mostrar caché local para dar respuesta inmediata, y luego se reemplaza con datos frescos. La persistencia final en otro `launch(Dispatchers.IO)` evita que escribir en Room frene el repintado.

## Qué debería decirse oralmente

- "En Social separé claramente la lectura pesada del hilo principal usando `Dispatchers.IO`."
- "Cuando necesito más de una consulta independiente, uso `coroutineScope` con `async` para ejecutarlas en paralelo."
- "Cuando el resultado ya está listo, regreso a `Dispatchers.Main` para actualizar `StateFlow` y permitir que Compose se recomponga sin errores de thread."
- "Elegimos cache-first porque mejora la latencia percibida: el usuario ve contenido antes y luego se sincroniza con Firestore."

## Riesgos y tradeoffs

- Si se olvidara `withContext(Dispatchers.Main)`, la UI podría actualizarse desde un hilo incorrecto.
- Si se quitara `coroutineScope`, podrían quedar corrutinas huérfanas o más difícil de controlar la cancelación.
- Si no se persistiera en background, la UI sería más simple, pero se perdería resiliencia offline.
- El flujo es más complejo que un fetch único, pero el beneficio en UX y escalabilidad justifica la complejidad.

## Vocabulario técnico importante

- **Dispatcher**: define en qué hilo corre la corrutina.
- **Structured concurrency**: hace que las corrutinas hijas queden controladas por el scope padre.
- **Cache-first**: muestra datos locales primero y luego los remotos.
- **StateFlow**: estado reactivo que Compose puede observar.
- **Parallelism**: ejecución simultánea de tareas independientes.

## Posibles preguntas del evaluador

### 1. ¿Por qué usaron `Dispatchers.IO` en Social?
Porque las consultas a Room y Firestore son operaciones bloqueantes o de espera de I/O. Llevarlas a IO evita congelar Main y mantiene la interfaz fluida.

### 2. ¿Para qué sirve `coroutineScope` dentro de `loadCommunityDetails()`?
Para agrupar las corrutinas hijas y esperar que todas terminen. Además, si una falla, se cancelan las demás de forma controlada.

### 3. ¿Por qué publican el resultado en `Dispatchers.Main`?
Porque `StateFlow` alimenta la UI y la actualización del estado visible debe hacerse en Main para respetar el ciclo de vida de Compose.

## Cómo defender esta implementación oralmente

La defensa más fuerte es decir que el módulo Social está optimizado para percepción de rapidez y seguridad de ciclo de vida. Primero cargas caché o consultas pesadas en IO, luego publicas en Main, y cuando hay varias piezas de información independientes las resuelves en paralelo. Esa combinación reduce latencia, evita bloqueos y mantiene una arquitectura fácil de justificar en MVVM.
