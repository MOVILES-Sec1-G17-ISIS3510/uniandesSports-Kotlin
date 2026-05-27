# Coach Comparison Feature Documentation

Welcome to the **Coach Comparison** feature documentation. This folder documents the offline support, caching mechanisms, multithreading strategies, and responsive layout decisions applied to the side-by-side coach comparison matrix.

## Documentation Index

1. **[Caching Strategy](caching.md)**
   * Describes the in-memory LRU Cache and Coil image loading strategies used to optimize performance, and distinguishes them from Room Local Storage.
2. **[Parallel Data Loading](multithreading.md)**
   * Explains how Kotlin Coroutines (`async/await` and `awaitAll()`) are leveraged to load multiple profiles in parallel.
3. **[Eventual Connectivity](eventual-connectivity.md)**
   * Covers offline state detection via `ConnectivityManager` and how the application falls back to Room database storage when offline.
4. **[Responsive Layout](responsive-design.md)**
   * Outlines how the comparison matrix dynamically adjusts column widths, margins, and label spacing for small phones, large phones, and tablets.
5. **[Local Database Storage](localdb.md)**
   * Details Room database entities, DAOs, and TTL decisions used to persist data locally.

---

## Architectural Summary

The feature follows clean MVVM guidelines:
* **UI Layer**: Compose-based screens displaying structured states.
* **ViewModel Layer**: Launches async scopes, monitors network state, and propagates UI states (`Loading`, `Success`, `Error`, `EmptyOffline`).
* **Repository Layer**: Acts as the single source of truth deciding whether to fetch from local Room storage or Firestore remote storage.
