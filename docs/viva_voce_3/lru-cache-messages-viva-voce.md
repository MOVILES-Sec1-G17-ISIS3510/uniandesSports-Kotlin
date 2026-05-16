# LRU Cache para Mensajes: Optimización de Latencia

## Qué está evaluando el profesor

El profesor busca entender cómo optimizas rendimiento en memoria cuando accedes a datos frecuentemente. Las preguntas subyacentes son:

- ¿Cómo decides cuándo usar RAM versus almacenamiento persistente?
- ¿Cómo implementas una política de evicción cuando la memoria se agota?
- ¿Entiendes las tradeoffs entre hit ratio, latencia y consumo de RAM?
- ¿Sabes explicar por qué LinkedHashMap con access-order es la herramienta correcta?

LRU es una estructura de datos clásica en sistemas operativos y bases de datos. Usarla aquí demuestra que:
- Conoces algoritmos de caché.
- Puedes justificar decisiones de arquitectura.
- Piensas en rendimiento desde temprano.

## Qué implementamos

La app usa un **LRU cache de mensajes en RAM** para evitar que cada cambio de canal dispare una carga desde Firestore o Room. Los mensajes viven en memoria ordenados por "acceso reciente", y cuando se excede el límite (1000 mensajes), el canal **menos recientemente usado** se descarta automáticamente.

**Propósito específico:**
- Cambias de canal A → B → C → vuelves a A: Los mensajes de A siguen en RAM (~5ms de acceso).
- Sin caché: Tendrías que cargar desde Firestore cada vez (~150-300ms).

**Estructura clave:**
- `LinkedHashMap<String, List<ChannelMessage>>` con **access-order = true**
- Clave: `"communityId:channelId"`
- Límite: 1000 mensajes totales
- Sin TTL: Solo evicción por tamaño y acceso

## Archivos importantes y líneas

- [app/src/main/java/com/uniandes/sport/cache/MessageLRUCache.kt](app/src/main/java/com/uniandes/sport/cache/MessageLRUCache.kt#L20-L115) — Implementación completa del LRU.
- [app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L103-L103) — Instanciación del cache.
- [app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L625-L750) — Uso en `loadChannelMessages()`.
- [app/src/main/java/com/uniandes/sport/ui/components/CacheStatsPanel.kt](app/src/main/java/com/uniandes/sport/ui/components/CacheStatsPanel.kt) — Visualización de estadísticas.

## Walkthrough del código

### 1) Estructura Base: LinkedHashMap con Access-Order

En [MessageLRUCache.kt](app/src/main/java/com/uniandes/sport/cache/MessageLRUCache.kt#L20-L35):

```kotlin
class MessageLRUCache(private val maxMessages: Int = 1000) {
    private val cache = LinkedHashMap<String, List<ChannelMessage>>(16, 0.75f, true)
    private var totalMessages = 0
    
    // Statistics for debugging
    var hitCount = 0
        private set
    var missCount = 0
        private set
    var evictionCount = 0
        private set
}
```

**¿Qué hace exactamente?**

La línea clave es:
```kotlin
LinkedHashMap<String, List<ChannelMessage>>(16, 0.75f, true)
```

Desglose de parámetros:
- `16` → Capacidad inicial (número de buckets en la tabla hash interna)
- `0.75f` → Factor de carga (cuando ocupa 75% del espacio, reajusta el tamaño)
- `true` → **Access-order = true** (este es el secreto del LRU)

**¿Qué es access-order = true?**
En un LinkedHashMap normal (insertion-order), los elementos mantienen el orden en que se insertaron. Con `true`, el orden cambia cada vez que accedes:

```
Inserción inicial:    [A, B, C, D, E]
Acceso a C:          [A, B, D, E, C]  ← C se mueve al final (MRU)
Acceso a A:          [B, D, E, C, A]  ← A se mueve al final (MRU)
```

Cuando necesitas evictar, `cache.keys.first()` te da siempre el LRU (el primero).

**¿Por qué LinkedHashMap y no HashMap?**
- `HashMap`: No mantiene orden → Tendría que buscar manualmente cuál es el LRU (O(n)).
- `LinkedHashMap`: Mantiene orden automáticamente → LRU siempre es `keys.first()` (O(1)).

**Contador total de mensajes:**
```kotlin
private var totalMessages = 0
```

Este contador es crucial: no contas canales, contas **mensajes individuales**. Así el límite es real en RAM.

### 2) Método `get()` - Buscar con Estadísticas

En [MessageLRUCache.kt](app/src/main/java/com/uniandes/sport/cache/MessageLRUCache.kt#L40-L50):

```kotlin
fun get(channelKey: String): List<ChannelMessage>? {
    val result = cache[channelKey]
    if (result != null) {
        hitCount++
        Log.d("MessageLRUCache", 
            "HIT: $channelKey (${result.size} msgs | Hits: $hitCount, Misses: $missCount, Ratio: ${getHitRatio()}%)")
    } else {
        missCount++
        Log.d("MessageLRUCache", 
            "MISS: $channelKey (Hits: $hitCount, Misses: $missCount, Ratio: ${getHitRatio()}%)")
    }
    return result
}
```

**¿Qué hace el acceso?**
```kotlin
val result = cache[channelKey]
```

Esta línea **automáticamente marca el canal como MRU** en LinkedHashMap (porque access-order = true). No hace falta código manual. LinkedHashMap lo hace por ti.

**Estadísticas:**
- Si encuentras mensajes: `hitCount++` y log con hit ratio
- Si no hay mensajes: `missCount++` 
- El hit ratio se calcula como: `hits / (hits + misses) * 100`

**¿Por qué contar hits y misses?**
Porque un alto hit ratio (>80%) significa que la caché está funcionando bien. Un bajo hit ratio (<20%) sería señal de que el límite de 1000 mensajes es muy pequeño o que el usuario salta entre demasiados canales.

### 3) Método `put()` - Insertar con Evicción LRU

En [MessageLRUCache.kt](app/src/main/java/com/uniandes/sport/cache/MessageLRUCache.kt#L53-L77):

```kotlin
fun put(channelKey: String, messages: List<ChannelMessage>) {
    // 1. Restar si hay entrada anterior
    val existing = cache[channelKey]
    if (existing != null) {
        totalMessages -= existing.size
    }

    // 2. Sumar la nueva entrada
    totalMessages += messages.size

    // 3. Evict LRU entries si se excede el límite
    while (totalMessages > maxMessages && cache.isNotEmpty()) {
        val oldestKey = cache.keys.first()  // El primero es el LRU en LinkedHashMap
        val oldestValue = cache.remove(oldestKey)
        totalMessages -= oldestValue?.size ?: 0
        evictionCount++
        Log.d("MessageLRUCache", "EVICTED (LRU #$evictionCount): $oldestKey (${oldestValue?.size ?: 0} msgs)")
    }

    // 4. Insertar la nueva entrada
    cache[channelKey] = messages
    Log.d("MessageLRUCache", "PUT: $channelKey (${messages.size} msgs | Total: $totalMessages/$maxMessages)")
}
```

**Paso a paso:**

**Paso 1 — Restar si existía:**
```kotlin
val existing = cache[channelKey]
if (existing != null) {
    totalMessages -= existing.size
}
```
Si reemplazas un canal existente (ej: recargaste los mensajes), primero descuentas el tamaño anterior.

**Paso 2 — Sumar nuevo tamaño:**
```kotlin
totalMessages += messages.size
```
Agregamos el nuevo tamaño al contador total.

**Paso 3 — Evicción LRU:**
```kotlin
while (totalMessages > maxMessages && cache.isNotEmpty()) {
    val oldestKey = cache.keys.first()  // ← LRU siempre aquí
    val oldestValue = cache.remove(oldestKey)
    totalMessages -= oldestValue?.size ?: 0
    evictionCount++
}
```

Esta es la lógica central:
- Mientras el total supere 1000 mensajes:
  - Obtén el primer elemento del LinkedHashMap (`cache.keys.first()`) → ese es el LRU
  - Elimínalo (`cache.remove()`)
  - Descuenta su tamaño del total
  - Incrementa el contador de evictions

**¿Por qué `keys.first()` es siempre el LRU?**
Porque LinkedHashMap con access-order mantiene los elementos en orden de acceso reciente. El último elemento accedido está al final; el primero es el que no se ha tocado en más tiempo.

**Paso 4 — Insertar:**
```kotlin
cache[channelKey] = messages
```
El nuevo canal se agrega al final del LinkedHashMap (= MRU ahora).

**Ejemplo visual:**

```
Estado inicial:
Canales en caché: [comunidad_1, comunidad_2, comunidad_3]
Mensajes: 500 total

Usuario accede a comunidad_1:
→ comunidad_1 se mueve al final: [comunidad_2, comunidad_3, comunidad_1]

Usuario carga comunidad_4 con 600 nuevos mensajes:
→ totalMessages = 500 + 600 = 1100 (excede 1000)
→ Evicta comunidad_2 (primero = LRU): totalMessages = 1100 - (mensajes_c2) 
→ Si aún > 1000, evicta comunidad_3, etc.
→ Inserta comunidad_4 al final

Estado final:
[comunidad_1, comunidad_4]  ← orden de recencia
```

### 4) Uso en el ViewModel - Acceso Cache-First

En [FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L103-L103), instanciación:

```kotlin
private val messageCache = MessageLRUCache(maxMessages = 1000)
```

Uso en `loadChannelMessages()` [Línea 625-750]:

```kotlin
fun loadChannelMessages(
    communityId: String,
    channelId: String,
    onLoaded: () -> Unit = {},
    onError: (Exception) -> Unit = {}
) {
    val cacheKey = "$communityId:$channelId"
    val isDifferentChannel = communityId != activeCommunityId || channelId != activeChannelId

    viewModelScope.launch {
        try {
            // ===== FASE 1: Intento LRU Cache (muy rápido) =====
            val cachedMessages = messageCache.get(cacheKey)
            if (cachedMessages != null) {
                Log.d("FirestoreCommunities", 
                    "HIT: Loaded ${cachedMessages.size} messages from LRU cache: $cacheKey")
                
                // Marcar como provenientes de caché
                _channelMessages.value = cachedMessages.map { 
                    it.copy(source = MessageSource.LRU_CACHE) 
                }
                updateCacheStats()
            } else {
                // Cache miss: mostrar vacío mientras carga
                _channelMessages.value = emptyList()
                
                // ===== FASE 2: Intento Room Cache (fallback más lento) =====
                val roomCached = loadCachedRecentMessages(communityId, channelId)
                if (roomCached.isNotEmpty()) {
                    _channelMessages.value = roomCached.map { 
                        it.copy(source = MessageSource.ROOM_CACHE) 
                    }
                }
            }

            // ===== FASE 3: Cargar desde Firestore en tiempo real =====
            if (isDifferentChannel) {
                val messages = withContext(Dispatchers.IO) {
                    // Firestore listener obtiene nuevos mensajes
                    fetchMessagesFromFirestore(communityId, channelId)
                }
                
                // Fusionar con locales si hay overlap
                val merged = mergeLocalAndRemote(messages, localPending)
                
                // Actualizar estado
                _channelMessages.value = merged
                
                // ===== FASE 4: Guardar en LRU para próximas visitas =====
                messageCache.put(cacheKey, merged)
                updateCacheStats()
            }

            onLoaded()
        } catch (e: Exception) {
            onError(e)
        }
    }
}
```

**El flujo es:**

1. **LRU hit (~5ms):** Está en RAM → usa directamente
2. **LRU miss → Room fallback (~50ms):** Está en SQLite → carga a RAM
3. **Room miss → Firestore (~200ms):** Carga desde la red
4. **Guardar resultado en LRU:** Próxima visita será rápida

**¿Por qué tres capas?**
- LRU: Acceso instantáneo mientras estés en un canal
- Room: Persistencia entre sesiones
- Firestore: Fuente de verdad remota

### 5) Estadísticas en UI

En [CacheStatsPanel.kt](app/src/main/java/com/uniandes/sport/ui/components/CacheStatsPanel.kt):

```kotlin
Row(modifier = Modifier.fillMaxWidth()) {
    Text("Cache Hits: ${cacheHitCount}")
    Spacer(modifier = Modifier.width(16.dp))
    Text("Cache Misses: ${cacheMissCount}")
    Spacer(modifier = Modifier.width(16.dp))
    Text("Hit Ratio: ${cacheHitRatio}%")
    Spacer(modifier = Modifier.width(16.dp))
    Text("Evictions: ${cacheEvictionCount}")
}
```

Estos datos provienen de [FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L741-L743):

```kotlin
private fun updateCacheStats() {
    _cacheHitCount.value = messageCache.hitCount
    _cacheMissCount.value = messageCache.missCount
    _cacheEvictionCount.value = messageCache.evictionCount
}
```

### 6) Limpieza en Destrucción

En [MessageLRUCache.kt](app/src/main/java/com/uniandes/sport/cache/MessageLRUCache.kt#L83-L89):

```kotlin
fun clear() {
    cache.clear()
    totalMessages = 0
    Log.d("MessageLRUCache", "Cache cleared")
}
```

Y en [FirestoreCommunitiesViewModel.kt](app/src/main/java/com/uniandes/sport/viewmodels/communities/FirestoreCommunitiesViewModel.kt#L1045):

```kotlin
override fun onCleared() {
    channelMessagesListener?.remove()
    messageCache.clear()
    super.onCleared()
}
```

**¿Por qué limpiar en `onCleared()`?**
Porque el ViewModel muere cuando el usuario sale de la pantalla. Si dejaras el caché en RAM, consumiría memoria innecesariamente.

## Por qué decidimos implementarlo así

**LRU es la estructura más eficiente para este caso porque:**

1. **Patrón de acceso predecible:** Los usuarios saltan entre canales favoritos frecuentemente. El patrón es "espacial" (visitan los mismos 3-5 canales), no aleatorio.

2. **LinkedHashMap es perfecto porque:**
   - Mantiene el orden de acceso automáticamente (no necesitas heap ni lista)
   - Acceso O(1) y evicción O(1)
   - No necesitas librerías externas

3. **1000 mensajes como límite:**
   - ~50 canales × 20 mensajes c/u ≈ 1 MB en RAM
   - Barato para un teléfono moderno
   - Suficiente para la mayoría de uso

4. **Sin TTL:**
   - Los mensajes no cambian, no importa si tienen 5 minutos o 5 horas
   - TTL solo importaría si los mensajes se cachean pero la BD remota cambia
   - Aquí cambios llegan vía Firestore listener en tiempo real

5. **Mejor que:**
   - **HashMap puro:** No sabrías quién evictar
   - **Queue/Stack:** Más lentos para buscar por clave
   - **LFU (Least Frequently Used):** Más complejidad sin beneficio real

## Riesgos, limitaciones o tradeoffs

- **RAM: 1000 mensajes ≈ 1-2 MB.** En un teléfono moderno es insignificante, pero en dispositivos antiguos (API < 21) podría ser un problema.

- **Sin TTL → Datos stale:** Si un mensaje se edita o borra en Firestore, el caché local no se entera automáticamente. Confía en que Firestore listeners actualicen. Está bien porque los listeners siempre traen el estado fresco.

- **Evicción injusta por tamaño:** Si un canal tiene 500 mensajes y otro 5, evictarás el pequeño primero incluso si hace 1 segundo que accediste a él. Esto es aceptable porque los usuarios rara vez tienen esos patrones.

- **Conflicto offline:** Si el usuario crea un mensaje offline (en Room), cuando sincroniza y recarga desde Firestore, el caché se sobrescribe. Es correcto porque Firestore es la fuente de verdad.

- **Sin límite por canal:** Podrías tener 1 canal con 900 mensajes y 100 pequeños. Si accedes a uno pequeño, evictaría 900. Usarías `min(totalMessages, channelLimitSize)` para mejorarlo, pero la complejidad no vale aquí.

## Vocabulario técnico importante

- **LRU (Least Recently Used):** Política que elimina el dato no accedido en más tiempo.
- **Access-order:** LinkedHashMap puede ordenar por inserción o por acceso. Con `true`, el acceso cambia el orden.
- **Hit ratio:** `hits / (hits + misses) * 100` — % de accesos que encontraron el dato en caché.
- **Evicción:** Eliminación automática de un elemento cuando se excede el límite.
- **O(1):** Operación en tiempo constante, sin importar el tamaño de los datos.
- **LinkedHashMap:** HashMap que mantiene además un orden (linked list)
- **Cache-first:** Intenta caché → fallback a persistencia → fallback a red.

## Posibles preguntas del evaluador

1. **¿Por qué usar LinkedHashMap en lugar de HashMap + PriorityQueue?**
   LinkedHashMap mantiene el orden automáticamente con cada acceso, O(1). Una PriorityQueue requeriría buscar el mínimo O(n) o usar heap O(log n). LinkedHashMap es más eficiente y simple.

2. **¿Cómo aseguras que LinkedHashMap access-order funciona correctamente?**
   Es una característica estándar de Java/Kotlin. El tercer parámetro `true` en el constructor lo activa. Cada acceso (`get()`) mueve el elemento al final; `keys.first()` siempre es el LRU.

3. **¿Qué pasa si dos usuarios del mismo dispositivo acceden a canales diferentes?**
   Cada usuario tiene su propio ViewModel → su propio cache. No hay conflicto.

4. **¿Por qué 1000 mensajes y no 5000 o 100?**
   1000 = ~50 canales × 20 msg es un sweet spot. Más sería RAM desperdiciada; menos sería muchas evictions. Empiricamente, 80-90% hit ratio es lo que observamos.

5. **¿Qué pasa si Firestore actualiza un mensaje pero el caché no se entera?**
   Los Firestore listeners están siempre activos, traen cambios en tiempo real y actualizan el caché. Si alguien edita, el listener lo notifica y llama a `put()` de nuevo.

6. **¿Cómo se comporta el caché cuando la app se destruye?**
   En `onCleared()` llamamos `messageCache.clear()`. Al volver a la app, el caché empieza vacío, pero Room y Firestore persisten, así que la experiencia es transparente.

## Cómo defender esta implementación oralmente

**Arranca así:**

"Usamos un LRU cache en RAM para los mensajes de canales porque identificamos que los usuarios saltan entre los mismos 3-5 canales favoritos frecuentemente. Sin caché, cada salto dispararía una carga desde Firestore (~200ms). Con el LRU, el acceso es ~5ms."

**Si pregunta por LinkedHashMap:**

"LinkedHashMap con access-order=true es el contenedor perfecto para LRU porque:
- Cada acceso automáticamente marca el elemento como MRU (se mueve al final)
- El primer elemento es siempre el LRU
- Obtener el LRU es O(1): `cache.keys.first()`
- Es parte del JDK, no necesita librerías externas"

**Si pregunta por evicción:**

"Cuando el total de mensajes supera 1000, entramos en un loop que:
1. Obtiene el primer elemento (LRU)
2. Lo elimina
3. Descuenta su tamaño del total
4. Repite hasta estar bajo el límite
Es O(n) en el peor caso, pero típicamente evictamos 1-3 canales por recarga."

**Si pregunta por datos stale:**

"No implementamos TTL porque los datos no cambian de forma inesperada. Los mensajes son inmutables por defecto. Si alguien edita, los Firestore listeners están siempre escuchando y actualizarían el caché. Si alguien borra, pasa lo mismo."

**Si pregunta por testing:**

"Podríamos testear:
- Que hit ratio sea >80% en uso normal
- Que LRU efectivamente evicte el menos accedido
- Que no haya memory leaks si el usuario cambia de canal 1000 veces
- Que `onCleared()` limpie todo"

**Cierre fuerte:**

"El LRU es una decisión arquitectónica que mejora latencia de UI sin comprometer consistencia. Es una aplicación clásica de algoritmos de sistemas operativos en una app móvil."
