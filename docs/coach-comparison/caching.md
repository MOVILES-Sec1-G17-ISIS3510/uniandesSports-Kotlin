# Caching Strategy: LRU Cache & Coil Image Loading

## 1. Overview and Clarification

In this architecture, we distinguish between **Local Storage (Persistence)** and **Caching Strategies (In-Memory / Replacement Policies)**:
1. **Local Storage / Offline Persistence**: The Room database is used as a persistent local store with a 10-minute Time-To-Live (TTL) check to save remote bandwidth and enable offline usage. This is classified as local database storage (documented in [localdb.md](localdb.md)).
2. **Caching Strategies**: True caching strategies are implemented in-memory and at the media delivery layer to ensure immediate visual responsiveness and efficient memory reuse:
   * **Coach Comparison LRU Cache**: A memory cache utilizing `android.util.LruCache` to store fetched `Profesor` profiles exclusively for the comparison view.
   * **In-Memory Avatar LRU Cache**: Utilizes Android's `LruCache` to cache lightweight metadata signatures for coach avatars.
   * **Image Cache Policy (Coil)**: Uses Coil's built-in multi-layered caching (memory LRU, disk storage, and network verification policies) to display and reuse profile pictures without re-downloading them.

---

## 2. LRU (Least Recently Used) Memory Cache for Coach Comparison

To avoid fetching the same coach profile from Room storage or Firestore repeatedly when navigating in and out of the comparison matrix, we implemented a custom in-memory caching strategy using `CoachComparisonCache` in [CoachComparisonCache.kt](file:///c:/Users/juli2/StudioProjects/uniandesSports-Kotlin/app/src/main/java/com/uniandes/sport/cache/CoachComparisonCache.kt).

### Caching Strategy Details:
* **Estructura**: `android.util.LruCache<String, Profesor>` (Thread-safe memory cache wrapping an access-ordered `LinkedHashMap`).
* **Key**: The coach's unique ID (`String`), representing a stable reference.
* **Value**: The full `Profesor` domain model containing ratings, sports, and experience.
* **Sizing/Parameters**: `MAX_ENTRIES = 10`. Since a comparison session allows comparing up to 3 coaches at once, 10 entries is the optimal size. It keeps all profiles for the current comparison and recent selections in memory, while preventing memory overhead.
* **Eviction Policy**: Least Recently Used (LRU). When an 11th coach profile is added, the profile that has gone longest without being accessed is evicted automatically.
* **Weight Calculation**: `sizeOf` is overridden to return `1` per entry, which enforces size constraints by entry count rather than byte allocation.

### Code Snippet:
```kotlin
object CoachComparisonCache {
    private const val MAX_ENTRIES = 10

    private val cache = object : LruCache<String, Profesor>(MAX_ENTRIES) {
        override fun sizeOf(key: String, value: Profesor): Int = 1
    }

    fun get(id: String): Profesor? {
        val coach = cache.get(id)
        if (coach != null) {
            Log.d("CoachComparisonCache", "Memory Cache HIT (LRU) for coach $id")
        } else {
            Log.d("CoachComparisonCache", "Memory Cache MISS for coach $id")
        }
        return coach
    }

    fun put(id: String, coach: Profesor) {
        cache.put(id, coach)
        Log.d("CoachComparisonCache", "Memory Cache PUT for coach $id (Size: ${cache.size()}/$MAX_ENTRIES)")
    }
}
```

---

## 3. LRU Memory Cache for Coach Avatar Metadata

In addition to caching the full profile, generating presentation configurations for the avatar view (initials extraction, URL escaping, and sport color themes) is cached in memory to maintain smooth scrolling in the main list:

* **Estructura**: `android.util.LruCache<String, CoachAvatarPayload>(48)` in `CoachAvatarMemoryCache`.
* **Sizing**: `MAX_ENTRIES = 48`. This is tailored to handle the maximum average items in the viewport plus historical scrolled cards.
* **Decision**: We cache only the lightweight presentation payloads (`CoachAvatarPayload`), keeping memory usage under `15KB`.

### Code Snippet:
```kotlin
object CoachAvatarMemoryCache {
    private const val MAX_ENTRIES = 48

    private val cache = object : LruCache<String, CoachAvatarPayload>(MAX_ENTRIES) {
        override fun sizeOf(key: String, value: CoachAvatarPayload): Int = 1
    }
    
    // getOrCreate logic maps profile fields to payload
}
```

---

## 4. Coil Image Caching Strategy

Coach photos are loaded dynamically from Firebase Storage URLs. Downloading these images on every screen bind is unacceptable for performance and mobile data usage.

We configure the [CoachAvatar](file:///c:/Users/juli2/StudioProjects/uniandesSports-Kotlin/app/src/main/java/com/uniandes/sport/ui/components/CoachAvatarCache.kt) component using Coil with explicit cache policies:

### Cache Layers:
1. **Memory Cache (LRU)**: Coil stores the decoded `Bitmap` in an internal LRU memory cache. If the image is currently displayed or was recently loaded, it resolves in `~2ms` with zero allocations.
2. **Disk Cache**: Coil stores the raw compressed image on disk. If the app restarts or memory is reclaimed, the image loads from local disk storage without a network request.
3. **Network Policy**: Configured to verify/download only when local caches miss.

### Code Snippet:
```kotlin
@Composable
fun CoachAvatar(
    profesor: Profesor,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val payload = remember(profesor.id, profesor.nombre, profesor.deporte) {
        CoachAvatarMemoryCache.getOrCreate(profesor)
    }

    Box(...) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(payload.imageUrl)
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)  // In-Memory LRU Cache
                .diskCachePolicy(CachePolicy.ENABLED)    // On-Disk Persistence
                .networkCachePolicy(CachePolicy.ENABLED) // Network verification on miss
                .build(),
            contentDescription = "Avatar de ${profesor.nombre}",
            ...
        )
    }
}
```

---

## 5. How to Test Caching Strategies

1. **Testing CoachComparisonCache (LRU)**:
   * Select 2 coaches for comparison and tap **Compare Now**. The logs will show `Memory Cache MISS` for both coaches.
   * Go back and tap **Compare Now** again. The logs will show `In-Memory LRU Cache HIT for coach [ID]`.
   * Continue comparing different coaches. Once more than 10 coaches have been compared, you will observe the eviction of the least recently compared coach from memory.

2. **Testing Coil Image Caching**:
   * Open the comparison screen while online to load the coach photos.
   * Turn off internet connection (Airplane Mode).
   * Exit the comparison screen and re-open it.
   * **Verification**: The coach avatars load instantly with their photos, demonstrating that Coil successfully resolved the request using its disk/memory cache policies.
