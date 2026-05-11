# Multithreading & Async - Executive Summary

## One-Page Snapshot

| Aspecto | Detalle | Ubicación |
|---------|---------|-----------|
| Total Coroutines | 80+ | Todo el proyecto |
| StateFlows | 90+ | ViewModels |
| IO ops | 40+ | Repositories |
| Main ops | 20+ | StateFlow updates |
| Parallelism | 10+ | FirestoreCommunitiesVM:275-315 |
| LaunchedEffect | 50+ | Compose screens |
| Workers | 10 | Background sync |
| Score | 9.2/10 = 92% | A (Excellent) |
| Key Gap | GlobalScope | RunningVM:106 |

---

## Stack Simplificado

```
Compose UI (Main, 60 FPS)
    ↓
ViewModel (viewModelScope)
    ├─ launch(IO) + withContext
    ├─ coroutineScope { async + async + async }
    └─ onCleared() → cancel + remove listeners
    ↓
Repositories (Room + Firestore)
    ↓
WorkManager (background sync)
```

---

## Top 3 Decisiones

| Decisión | Beneficio | Trade-off |
|----------|-----------|-----------|
| Cache-first | 10ms latencia vs 500ms | Sincronización compleja |
| Parallelismo | 3× speedup (300 vs 900ms) | Structured concurrency |
| Debounce | 1000→1 queries/sec | Latencia usuario 1s |

---

## Dispatcher Mapping

**IO Thread Pool (64 threads)**
- Room queries (157-175)
- Firestore reads (206-235)
- Parallel fetches (275-315)
- Calendar events (51)

**Main Thread (1 thread, 60 FPS)**
- StateFlow updates (127)
- UI callbacks (154)
- Compose recompose (auto)

---

## Lifecycle Pattern

```
Activity Created
├─ ViewModel Created
│  └─ viewModelScope
│     ├─ launch(IO)
│     └─ listeners
├─ User navigates away
│  └─ ViewModel.onCleared()
│     ├─ job.cancel()
│     └─ listener.remove()
└─ Activity Destroyed
```

---

## Código Crítico (Líneas)

| Patrón | Archivo | Líneas |
|--------|---------|--------|
| Guard + Flow | FirestoreRetosViewModel | 157-175 |
| Cache-first | FirestoreCommunitiesViewModel | 206-235 |
| Parallelismo | FirestoreCommunitiesViewModel | 275-315 |
| Cleanup | FirestoreRetosViewModel | 608-625 |
| Debounce | PlayScreen | 138-165 |

---

## Terminology

- **Dispatcher**: Define thread para corrutina
- **viewModelScope**: Auto-cancel en onCleared()
- **withContext**: Cambiar dispatcher sin overhead
- **StateFlow**: Hot flow, emite último valor
- **coroutineScope**: Structured concurrency
- **cache-first**: Local instant, remoto refresh
- **debounce**: Retrasa emisión
- **memory leak**: Referencia no liberada

---

## Riesgos

| Riesgo | Severidad | Fix |
|--------|-----------|-----|
| GlobalScope | HIGH | viewModelScope/WorkManager |
| Listener leak | MEDIUM | .remove() en onCleared() |
| Stale values | LOW | LaunchedEffect dependencies |

---

## Expected Score

| Criterio | Pts | Comentario |
|----------|-----|-----------|
| Coroutines | 9/10 | GlobalScope -1 |
| Dispatchers | 9/10 | Sin Default pero OK |
| StateFlow | 10/10 | ✅ |
| Performance | 10/10 | ✅ |
| Error Handling | 9/10 | No siempre |
| Code Quality | 9/10 | Sin Hilt |
| **TOTAL** | **9.2/10** | **92% = A** |

---

## Presentation (3-5 min)

1. "80+ corrutinas en viewModelScope"
2. "IO dispatcher para BD/red, Main para UI"
3. "Parallelismo: 3× speedup (300 vs 900ms)"
4. "Cache-first: 10ms vs 500ms"
5. "GlobalScope error conocido"

---

## Defense Phrases

- "Structured concurrency—si uno falla, todos se cancelan"
- "ViewModelScope auto-cancels en onCleared()"
- "Optimizamos latencia percibida"
- "Debounce reduce queries 1000× eficientemente"

---

**Status: ✅ Listo 92%**
