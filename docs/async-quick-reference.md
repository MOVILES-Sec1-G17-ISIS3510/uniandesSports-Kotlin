# Quick Reference: Async/Multithreading Patterns

## Tabla de Dispatchers en el Código

| Dispatcher | Ubicación | Línea | Operación | Razón |
|-----------|-----------|-------|-----------|-------|
| **IO** | FirestoreRetosViewModel | 157-175 | observeLocalRetos() | Room query no bloquea Main |
| **IO** | FirestoreCommunitiesViewModel | 206-235 | loadCommunities() | Firestore `.get()` bloqueante |
| **IO** | FirestoreCommunitiesViewModel | 275-315 | 3× async() paralelo | Posts+Channels+Members |
| **IO** | RunningSessionViewModel | 106 | AI Analysis (⚠️) | GlobalScope anti-patrón |
| **Main** | FirestoreCommunitiesViewModel | 127 | Update StateFlow | UI requiere Main |
| **Main** | AuthScreen | 154 | onLoginSuccess | Callback UI |

## 5 Patrones Clave (Copy-Paste)

### Pattern 1: ViewModelScope + IO + Main

```kotlin
viewModelScope.launch {
    val result = withContext(Dispatchers.IO) {
        db.collection("x").get().await()
    }
    _state.value = result  // Ya en Main
}
```

### Pattern 2: coroutineScope { async() } — Parallelismo

```kotlin
val payload = coroutineScope {
    val a = async(Dispatchers.IO) { fetch1() }
    val b = async(Dispatchers.IO) { fetch2() }
    val c = async(Dispatchers.IO) { fetch3() }
    Data(a.await(), b.await(), c.await())
}
```

### Pattern 3: LaunchedEffect + Debounce

```kotlin
LaunchedEffect(searchQuery) {
    if (query.length >= 3) {
        delay(1000)
        performSearch(query)
    }
}
```

### Pattern 4: Job Guard + Cancellation

```kotlin
private var job: Job? = null
private fun observe() {
    if (job != null) return
    job = viewModelScope.launch { }
}
override fun onCleared() {
    job?.cancel()
    listener?.remove()
}
```

### Pattern 5: StateFlow + Emite

```kotlin
private val _state = MutableStateFlow<State>(initial)
override val state: StateFlow<State> = _state.asStateFlow()
_state.value = newValue  // Emite, Compose se recompone
```

## ⚠️ Anti-Patrones Identificados

| Anti-patrón | Dónde | Problema | Solución |
|-----------|-------|---------|---------|
| `GlobalScope.launch` | RunningVM:106 | No se cancela, memory leak | `viewModelScope` o WorkManager |
| No remover listener | N/A | Memory leak acumulativo | `listener?.remove()` en onCleared() |
| LaunchedEffect sin deps | N/A | Captura stale values | Incluir variable en `[]` |

## IO vs Main Dispatcher: Quick Decision Tree

```
¿Operación hace I/O o CPU-heavy?
├─ SÍ: ¿Es bloqueante?
│  ├─ SÍ: Dispatchers.IO
│  └─ NO: Dispatchers.Default
└─ NO: ¿Es UI update?
   ├─ SÍ: Dispatchers.Main
   └─ NO: Main por defecto
```

## Cosas que gritan "NO ENTIENDO"

- ❌ "Async es más rápido que launch"
  - ✅ "Async retorna Deferred para paralelismo"

- ❌ "withContext es igual a launch"
  - ✅ "withContext suspende y cambia dispatcher sin crear corrutina nueva"

- ❌ "viewModelScope se cancela cuando vuelves a abrir"
  - ✅ "Se cancela cuando ViewModel se destruye (navegación fuera)"

- ❌ "Firestore no bloquea, está en Main"
  - ✅ "Firestore es suspending, pero .get().await() si bloquea—movemos a IO"

## Frases que Demuestran Competencia

1. "Aquí usamos `withContext` para cambiar dispatcher sin overhead"
2. "`coroutineScope` asegura que si uno falla, todos se cancelan—structured concurrency"
3. "Esta corrutina está en `viewModelScope`, se cancela automáticamente"
4. "Debounce cancela automáticamente el anterior delay()"
5. "Cache-first optimiza latencia percibida—Room instant, Firestore network-bound"
6. "Si olvidamos remover el listener, el ViewModel no se garbage collect"
7. "Dispatchers.IO pool tiene 64 threads para operaciones suspendidas"
8. "StateFlow emite último valor al new subscriber—hot flow"
9. "LaunchedEffect con dependencias—sin incluir var, captures stale"
10. "WorkManager para tareas que DEBEN completarse incluso si cierra app"

## Score Actual por Tema

| Área | Score | Evidence |
|------|-------|----------|
| Coroutines | 9/10 | 80+ sitios, pero 1 GlobalScope |
| Dispatchers | 10/10 | IO vs Main claro |
| Parallelismo | 9/10 | coroutineScope { async() } bien |
| StateFlow/Flow | 10/10 | 90+ declarations |
| Performance | 10/10 | Cache-first, parallelismo |
| Error Handling | 7/10 | Try/catch pero no siempre |

**Total: 72/80 = 90%**
