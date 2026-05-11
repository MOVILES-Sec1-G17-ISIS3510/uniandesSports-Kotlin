# 🎓 EXAM DAY - Compact Cheat Sheet

**Print. Carry. 1 minute skim before professor asks.**

---

## 3 Cosas Que Dirás Primero

1. **"80+ corrutinas en `viewModelScope`"** — Lifecycle safe, auto-cancel
2. **"IO para BD/red, Main para UI"** — Evita freezing
3. **"Parallelismo async: 300ms vs 900ms"** — Performance conscious

---

## Si Preguntan: ¿Qué es...?

| Término | Respuesta |
|---------|-----------|
| **launch** | Fire-and-forget, no retorna resultado |
| **async** | Retorna Deferred, esperas await() |
| **withContext** | Cambias dispatcher, esperas resultado |
| **viewModelScope** | Cancela automático en onCleared() |
| **StateFlow** | Emite último valor a subscribers, hot |
| **Dispatchers.IO** | 64 threads, operaciones bloqueantes |
| **Dispatchers.Main** | 1 thread, 60 FPS, solo UI |
| **coroutineScope** | Espera todos async o cancela si falla |
| **cache-first** | Local 10ms + actualizar remoto 500ms |
| **debounce** | Retrasa emisión hasta X sin cambios |

---

## 4 Archivos & Líneas Críticas

| Qué | Archivo | Línea |
|-----|---------|-------|
| Parallelismo | FirestoreCommunitiesViewModel | 275-315 |
| Cache-first | FirestoreCommunitiesViewModel | 206-235 |
| Job cancel | FirestoreRetosViewModel | 608-625 |
| Debounce | PlayScreen | 138-165 |
| ⚠️ GlobalScope | RunningSessionViewModel | 106 |

---

## Respuestas Memorizadas

### "¿Por qué Dispatchers.IO?"

"Main thread singular, 60 FPS. IO bloqueante freezea. IO dispatcher 64 threads para suspending operations. Main queda libre."

### "¿Parallelismo?"

"3 async() simultáneamente: posts, channels, members. coroutineScope espera todos o cancela si uno falla. 300ms paralelo vs 900ms secuencial."

### "¿Memory leaks?"

"viewModelScope auto-cancel en onCleared(). Jobs removemos explícitamente. Listeners con .remove(). GlobalScope error conocido."

### "¿Cache-first?"

"Latencia percibida. Room local 10ms, usuario ve datos. Firestore background actualiza 500ms con data fresca."

---

## Timeline: Defend en 3 Minutos

> "Coroutines + MVVM. 80+ corrutinas en viewModelScope. IO dispatcher para bloqueantes, Main para UI. Parallelismo 3× speedup. Cache-first UX. Debounce queries. onCleared() cleanup. GlobalScope error conocido línea 106. Score 9.2/10 = 92%."

---

## Red Flags (Avoid)

❌ "Async más rápido que launch"  
✅ "Async retorna Deferred para paralelismo"

❌ "withContext igual a launch"  
✅ "withContext suspende, cambia dispatcher sin crear corrutina"

❌ "Firestore bloquea Main"  
✅ "Firestore suspending, pero .get().await() sí bloquea"

---

## Frases Competentes

- "Structured concurrency"
- "Suspending operations"
- "Lifecycle-aware scopes"
- "Hot vs cold flows"
- "StateFlow emission"
- "Dispatcher switching"
- "Memory leak prevention"

---

## Last Minute (30 Segundos)

```
Respira.

Recuerda:
- 80+ corrutinas viewModelScope
- IO para BD/red
- Parallelismo 300ms vs 900ms
- Cache-first UX
- GlobalScope error

Confianza. Líneas específicas. Honestidad.

¡Vamos! 💪
```

---

**Status: Ready for 92%+ ✅**
