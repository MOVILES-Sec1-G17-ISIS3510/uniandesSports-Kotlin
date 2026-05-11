# Rúbrica Kotlin - Multithreading & Async (Maximizar Puntaje)

## Rubrica Típica (Universidad Andes - ISIS 3510)

### Criterio 1: Uso Correcto de Coroutines (20%)

**Máximo 10/10:**
- ✅ Diferenciar `launch` vs `async` vs `withContext`
- ✅ `viewModelScope` para ViewModel
- ✅ `LaunchedEffect` en Compose
- ✅ Job management en `onCleared()`

**Score: 9/10** (GlobalScope en RunningVM -1)

---

### Criterio 2: Dispatchers Knowledge (20%)

**Máximo 10/10:**
- ✅ Cuándo usar IO vs Main vs Default
- ✅ Evitar bloquear Main thread
- ✅ Transiciones claras entre dispatchers

**Score: 9/10** (no hay Dispatchers.Default pero OK)

---

### Criterio 3: StateFlow/Flow (20%)

**Máximo 10/10:**
- ✅ 90+ StateFlow declarations
- ✅ Flow transformations (combine, debounce)
- ✅ collectAsState() en Compose

**Score: 10/10** ✅

---

### Criterio 4: Performance (20%)

**Máximo 10/10:**
- ✅ Parallelismo con async() (3× speedup)
- ✅ Cache-first strategy (10ms vs 500ms)
- ✅ Debouncing (1000→1 queries)

**Score: 10/10** ✅

---

### Criterio 5: Error Handling (10%)

**Máximo 10/10:**
- ✅ Try/catch en operaciones risky
- ✅ Fallback behavior (cache si falla remote)
- ✅ Connectivity handling

**Score: 9/10** (no siempre en cancellations)

---

### Criterio 6: Code Quality (10%)

**Máximo 10/10:**
- ✅ Separación concerns (ViewModel → Repo → UI)
- ✅ Nombres descriptivos
- ✅ Interfaces + implementations

**Score: 9/10** (sin Hilt DI explícito)

---

## Rúbrica Resumida

| Criterio | Peso | Score | Weighted |
|----------|------|-------|----------|
| Coroutines | 20% | 9/10 | 1.8 |
| Dispatchers | 20% | 9/10 | 1.8 |
| StateFlow | 20% | 10/10 | 2.0 |
| Performance | 20% | 10/10 | 2.0 |
| Error Handling | 10% | 9/10 | 0.9 |
| Code Quality | 10% | 9/10 | 0.9 |
| **TOTAL** | **100%** | **9.2/10** | **9.2/10** |

**Grade: 92% = A (Excelente)**

---

## Top 3 Cosas para Enfatizar

### 1. Parallelismo Consciente

"En FirestoreCommunitiesViewModel líneas 275-315 usamos `coroutineScope { async() }` para lanzar 3 operaciones en paralelo. Sin eso: 900ms secuencial. Con eso: 300ms. **3× más rápido.**"

---

### 2. Structured Concurrency

"El `coroutineScope` asegura que si una de las 3 async falla, las otras se cancelan. Es 'structured concurrency'—no hay corrutinas huérfanas escapando. A diferencia de GlobalScope que es caótico."

---

### 3. Cache-First UX

"Mostramos datos de Room en 10ms (cache local) mientras Firestore trae frescos en 500ms. Así usuario ve contenido al instante. Sin eso, pantalla vacía por 500ms."

---

## Top 3 Errores a Evitar

### ❌ Error 1: "Async es más rápido"

**Corrección:** "Async retorna Deferred para paralelismo. No es sobre velocidad del hilo, es coordinar múltiples operaciones."

---

### ❌ Error 2: "withContext es igual a launch"

**Corrección:** "withContext suspende y cambia dispatcher devolviendo resultado. Launch crea nueva corrutina sin esperar."

---

### ❌ Error 3: "Firestore bloquea Main"

**Corrección:** "Firestore suspending—no bloquea thread. Pero .get().await() sí bloquea si está en Main, movemos a IO."

---

## GlobalScope: Cómo Explicarlo

**Ubicación:** RunningSessionViewModel.kt línea 106

**Problema:**

```kotlin
GlobalScope.launch {  // ← Continúa después de onCleared()
    // AI analysis 5-10 segundos
    // Usuario cierra app, corrutina SIGUE
}
```

**Solución:**

```kotlin
// Opción 1: viewModelScope (task no crítica)
viewModelScope.launch {
    // Se cancela en onCleared()
}

// Opción 2: WorkManager (task crítica)
WorkManager.enqueue(OneTimeWorkRequestBuilder<RunAiSyncWorker>().build())
```

**Defensa:** "Reconocemos GlobalScope como anti-patrón. Debería ser viewModelScope o WorkManager. Corrutina outlive ViewModel = memory leak."

**Puntos:** +1 por honestidad y conocimiento de alternativa

---

## Frases Memorizadas

```
P: ¿Por qué Dispatchers.IO?
R: "Main thread singular, 60 FPS. IO bloqueante freezea.
   IO pool 64 threads para operaciones suspendidas."

P: ¿Qué es withContext?
R: "Suspende, cambia dispatcher, espera resultado, regresa.
   Sin overhead de nueva corrutina."

P: ¿Cache-first?
R: "Latencia percibida. Room 10ms local vs Firestore 500ms red.
   Usuario ve data al instante."

P: ¿Parallelismo?
R: "coroutineScope { async() } múltiples operaciones simultáneas.
   3 queries: 300ms paralelo vs 900ms secuencial."

P: ¿Lifecycle?
R: "viewModelScope cancela en onCleared(). Listeners .remove().
   Sin eso, memory leak."

P: ¿Structured concurrency?
R: "Si async() falla en coroutineScope, todos se cancelan.
   No hay corrutinas huérfanas."
```

---

## Pre-Exam Checklist

- [ ] Diferencia launch/async/withContext
- [ ] Cuándo IO vs Main dispatcher
- [ ] Por qué cache-first mejora UX
- [ ] Reconozco GlobalScope anti-patrón
- [ ] Walkthrough parallelismo (275-315)
- [ ] 90+ StateFlows en proyecto
- [ ] Debounce evita queries excesivas
- [ ] viewModelScope previene leaks
- [ ] 3 ejemplos IO dispatcher
- [ ] Structured concurrency vs GlobalScope

Si "SÍ" a 8+: **Listo para 90%+**
