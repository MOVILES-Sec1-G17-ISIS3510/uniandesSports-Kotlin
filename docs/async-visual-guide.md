# Multithreading & Async - Visual Guide

## 🏗️ Architectura Completa

```
┌─────────────────────────────────────────┐
│         COMPOSE UI (Main, 60 FPS)       │
│  collectAsState → LaunchedEffect        │
└────────────────────┬────────────────────┘
                     │
         ┌───────────┴────────────┐
         ▼                        ▼
    VIEWMODEL               STATEFLOW
    viewModelScope          Emits changes
    ├─ launch(IO)           90+ declarations
    ├─ withContext
    └─ onCleared()
         │
    ┌────┴──────┐
    ▼           ▼
  REPO       LISTENERS
  Room       Firestore
  IO Thread  IO Thread
```

---

## 📊 Dispatcher Decision Tree

```
Operation Type?
├─ I/O (DB, Network, File) → Dispatchers.IO (64 threads)
├─ CPU Heavy (parsing) → Dispatchers.Default (#cores threads)
├─ UI Update (StateFlow) → Dispatchers.Main (1 thread, 60 FPS)
└─ Default → Dispatchers.Main (in viewModelScope)
```

---

## ⏱️ Timeline: Parallel vs Sequential

### Sequential (900ms)
```
Posts (300ms)    ════════════════
Channels (300ms)                  ════════════════
Members (300ms)                                    ════════════════
Total:                                                           900ms
```

### Parallel (300ms)
```
Posts (300ms)    ════════════════
Channels (300ms) ════════════════
Members (300ms)  ════════════════
Total:           300ms ✅ 3× faster
```

---

## 🔄 Dispatcher Chain: Main → IO → Main

```
viewModelScope.launch (Main)
├─ _isLoading.value = true
├─ withContext(Dispatchers.IO)
│  ├─ Load Room cache
│  ├─ withContext(Dispatchers.Main)
│  │  └─ Update _communities
│  └─ Firestore query
├─ (Back in Main)
├─ _communities.value = remote
└─ _isLoading.value = false
```

---

## 🎯 Coroutine Scope Hierarchy

```
viewModelScope (ViewModel lifetime)
├─ launch → observeLocalRetos
├─ coroutineScope { async, async, async }
└─ onCleared() → Cancel all

LaunchedEffect (Composable lifetime)
├─ dependency: searchText
└─ delay → search

rememberCoroutineScope (UI lifetime)
└─ onClick → launch
```

---

## 🚨 GlobalScope (Anti-Pattern)

```
GlobalScope.launch {
    ├─ No lifecycle ⚠️
    ├─ No cancellation
    └─ Memory leak accumulates
```

**Correct:**
```
viewModelScope.launch {
    └─ Auto-cancels in onCleared() ✅
}
```

---

## 📈 Performance: Cache-First

```
Without cache:
User opens screen  → Wait 500ms → See data

With cache-first:
User opens screen  → See data 10ms ← Cached
                   → Update 500ms → Refresh
```

**Impact:** 500ms perceived latency → 10ms first paint

---

## 📍 Debounce Flow

```
Type: "f"     → delay(1000) starts
Type: "o"     → [CANCEL] delay starts again
Type: "o"     → [CANCEL] delay starts again
Type: "ball"  → [CANCEL] delay starts again
Wait 1s       → delay COMPLETES → Search executes

Result: 1 query (not 100+)
```

---

## 🎛️ Thread Pools

### Main: 1 thread
```
UI Rendering 60 FPS
├─ State updates
├─ Callbacks
└─ Compose recompose
```

### IO: 64 threads
```
Database reads
Network requests
File I/O
Calendar queries
All suspending (don't block)
```

---

## ✅ Prevention Checklist

- [ ] Job tracking
- [ ] Listener .remove() in onCleared()
- [ ] viewModelScope (not GlobalScope)
- [ ] withContext for dispatcher switch
- [ ] LaunchedEffect with dependencies
- [ ] Try/catch on critical ops
- [ ] Cache-first strategy
- [ ] Debounce for searches

---

**Ready for oral exam** ✅
