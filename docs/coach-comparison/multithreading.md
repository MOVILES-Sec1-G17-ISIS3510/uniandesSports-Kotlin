# Parallel Data Loading & Concurrency: Kotlin Coroutines & Multithreading

## 1. Concurrency vs. Parallelism in Mobile Apps

When a user selects up to 3 coaches to compare in the side-by-side matrix, retrieving their data sequentially creates a poor user experience due to cumulative network and disk latencies.

### A. Sequential Loading (Blocking Flow)
If the application queries Coach A, waits for the response, then queries Coach B, and finally Coach C, the total loading time is the sum of all individual fetches:
$$\text{Total Time} = T_A + T_B + T_C \approx 400\text{ms} + 400\text{ms} + 400\text{ms} = 1200\text{ms}$$
In this model, execution is blocked sequentially, multiplying latency linear to the number of compared coaches ($O(N \times T)$).

### B. Parallel Loading (Non-Blocking Concurrent Flow)
By executing all queries concurrently using a multithreaded worker pool, the total duration is limited only by the single slowest fetch:
$$\text{Total Time} = \max(T_A, T_B, T_C) \approx 400\text{ms}$$
This model scales efficiently ($O(\max(T))$), yielding up to a **67% performance improvement** when comparing three coaches.

---

## 2. Structured Concurrency & Parent-Child Coroutines

Kotlin Coroutines utilize the principle of **Structured Concurrency**. This means that new coroutines can only be launched within a specific `CoroutineScope`, establishing a strict parent-child relationship that prevents resource leaks and guarantees clean cancellation.

```
       [ viewModelScope.launch(Dispatchers.Main) ]  <-- Parent Coroutine (UI Thread)
                      |
         +------------+------------+
         |                         |
  [ async(Dispatchers.IO) ]  [ async(Dispatchers.IO) ]  <-- Nested Child Coroutines (I/O Workers)
  (Query Coach A)            (Query Coach B)
```

### Parent-Child Relationships:
1. **Lifetime Binding**: The lifecycle of the child coroutines is bound to the parent coroutine. If the parent scope is destroyed (e.g., the user exits the screen and the ViewModel's `viewModelScope` is cleared), all active nested child coroutines are automatically canceled, preventing wasted network calls or database operations.
2. **Exception Propagation**: If one child coroutine encounters a fatal exception (e.g. Firestore timeout), the failure propagates upward to the parent. Under structured concurrency, the parent will immediately cancel the remaining sibling child coroutines, conserving battery and data usage.

---

## 3. Dispatchers & Thread Pool Mechanics

Android devices manage multiple thread pools. We explicitly switch and nest dispatchers to run each operation on its optimal thread pool:

| Dispatcher | Thread Pool Characteristics | Primary Usage in Coach Comparison |
| :--- | :--- | :--- |
| **`Dispatchers.Main`** | Single thread (the Android UI Main Thread). | Controls UI state transitions (`Loading` $\to$ `Success`). Triggers Jetpack Compose recomposition. |
| **`Dispatchers.IO`** | Elastic thread pool (scales on-demand, up to 64 threads or the number of CPU cores). | Executes blocking input-output operations: Room SQLite database queries and Firestore network queries. |

### Suspension vs. Blocking (The non-blocking magic)
When the parent coroutine on `Dispatchers.Main` reaches the `awaitAll()` statement, it goes into a **suspended** state:
* **Suspension is NOT blocking**: The Android Main Thread is **not** blocked. It is completely freed up and returned to the OS. This allows the system to continue rendering animations, handling user touches, and scrolling smoothly without causing jank or ANR (Application Not Responding) dialogs.
* While the main thread is free, the nested child coroutines execute their network/disk I/O operations in parallel on background worker threads belonging to the `Dispatchers.IO` pool.
* Once **all** background threads complete their I/O work, the suspension ends. The parent coroutine is rescheduled and resumes execution back on `Dispatchers.Main` to update the state flow and draw the matrix.

---

## 4. Execution Trace: Step-by-Step Thread Switching

The following diagram illustrates the thread lifecycle and dispatcher switching during a comparison request:

```
[Android Main Thread]                      [Dispatchers.IO Thread Pool]
      |                                                 |
1. loadComparison()                                     |
   - Sets state to Loading                              |
   - launches Parent Coroutine                          |
      |                                                 |
2. viewModelScope.launch(Dispatchers.Main)              |
   - Creates parent coroutine                           |
   - Map IDs & launches children                        |
      |-----------------(Launch async)----------------->|
      |                                            3. Child 1 (async - worker-1):
      |                                               - Queries Coach A (Room/Firestore)
      |                                            4. Child 2 (async - worker-2):
      |                                               - Queries Coach B (Room/Firestore)
      |                                                 |
5. awaitAll() [Suspended]                               |
   - Main thread is FREE for UI                         |
   - Parent is idle                                     |
      |                                                 |
      |<-----------(Return FetchResult success)---------|
      |                                                 |
6. Resumes on Dispatchers.Main                          |
   - Receives List<Profesor>                            |
   - Updates _uiState to Success                        |
   - Compose draws the Comparison Matrix                v
      v
```

---

## 5. Code Implementation: Nested Coroutines

The [CoachComparisonViewModel](file:///c:/Users/juli2/StudioProjects/uniandesSports-Kotlin/app/src/main/java/com/uniandes/sport/viewmodels/profesores/CoachComparisonViewModel.kt) implements this nesting explicitly:

```kotlin
fun loadComparison(coachIdsStr: String) {
    val ids = coachIdsStr.split(",").filter { it.isNotBlank() }
    if (ids.isEmpty()) {
        _uiState.value = CoachComparisonUiState.Error("No coaches selected.")
        return
    }

    _uiState.value = CoachComparisonUiState.Loading

    // 1. Parent Coroutine bound to Dispatchers.Main (UI Thread)
    viewModelScope.launch(Dispatchers.Main) {
        try {
            // 2. Multiple nested child coroutines launched on Dispatchers.IO for background I/O
            val deferredList = ids.map { id ->
                async(Dispatchers.IO) {
                    // This block executes concurrently on Dispatchers.IO threads
                    repository.fetchCoach(id) 
                }
            }

            // 3. Parent suspends on Main Thread, waiting for all background I/O tasks to finish
            val results = deferredList.awaitAll()

            val successfulCoaches = mutableListOf<Profesor>()
            var hasFailure = false
            val isOfflineFallback = !repository.isNetworkConnected()

            for (result in results) {
                when (result) {
                    is CoachFetchResult.Success -> {
                        successfulCoaches.add(result.coach)
                    }
                    is CoachFetchResult.Failure -> {
                        hasFailure = true
                    }
                }
            }
            
            // 4. Update UI states safely on Dispatchers.Main, triggering Compose recomposition
            if (hasFailure || successfulCoaches.size < ids.size) {
                if (!repository.isNetworkConnected()) {
                    _uiState.value = CoachComparisonUiState.EmptyOffline
                } else {
                    _uiState.value = CoachComparisonUiState.Error("Failed to load coaches.")
                }
            } else {
                _uiState.value = CoachComparisonUiState.Success(
                    coaches = successfulCoaches,
                    isOffline = isOfflineFallback
                )
            }
        } catch (e: Exception) {
            _uiState.value = CoachComparisonUiState.Error(e.message ?: "An error occurred.")
        }
    }
}
```

---

## 6. How to Verify Thread Execution in Logcat

To verify that the thread boundaries are respected during runtime:

1. Insert a log printing the current thread name inside the ViewModel and the Repository:
   ```kotlin
   Log.d("ThreadTrace", "ViewModel thread: ${Thread.currentThread().name}")
   // In Repository fetchCoach:
   Log.d("ThreadTrace", "Repository fetch thread: ${Thread.currentThread().name}")
   ```
2. Navigate to the comparison screen and inspect the Logcat logs filtered by `ThreadTrace`:
   * **Parent Entry**:
     `ViewModel thread: main` (Verifies the parent executes on the Main UI thread).
   * **Concurrently Spawned Children**:
     `Repository fetch thread: DefaultDispatcher-worker-1`
     `Repository fetch thread: DefaultDispatcher-worker-2`
     (Verifies that nested child coroutines are concurrently launched and executed on independent background threads of the elastic `Dispatchers.IO` pool).
   * **Parent Resumption**:
     `ViewModel thread: main` (Verifies that once background data loading is complete, execution successfully switches back to the Main thread to update UI state).
