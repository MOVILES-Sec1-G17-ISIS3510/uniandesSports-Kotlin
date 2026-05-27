# Eventual Connectivity: Offline Fallback & Network State Detection

## 1. Overview
Mobile applications operate under unpredictable network conditions (subways, parks, offline states). The Coach Comparison feature implements the **Eventual Connectivity** design pattern exclusively for its comparison matrix. 

If there is no internet connection when the comparison screen is loaded, the app falls back to loading data from the local Room database (Local Storage) instead of displaying a blocking error. A non-intrusive warning banner notifies the user that the app is showing cached offline data.

---

## 2. Implementation Decisions
We evaluated two ways to handle offline states specifically for Coach Comparison:

1. **Passive Firestore Cache Fallback**:
   * *Pros*: Automatic.
   * *Cons*: Firestore offline caching can be slow to resolve on cold startups, and does not provide an explicit API to know whether the data is offline or online, making it difficult to display connectivity banners.
2. **Active System Network Check + Local DB Fallback (Selected)**:
   * *Pros*: Immediate detection, custom banner display, and fine-grained control over stale fallback data.
   * *Decision*: Checking connectivity via the system's `ConnectivityManager` combined with Room database queries ensures that we always know if the user is offline, can fetch cached data instantly, and display the appropriate "You are offline" banner exclusively in the comparison view.

---

## 3. Code Snippets

### Network State Detection
The [CoachComparisonRepository](file:///c:/Users/juli2/StudioProjects/uniandesSports-Kotlin/app/src/main/java/com/uniandes/sport/repositories/CoachComparisonRepository.kt) queries the current network capabilities:

```kotlin
fun isNetworkConnected(): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } else {
        @Suppress("DEPRECATION")
        return connectivityManager.activeNetworkInfo?.isConnected == true
    }
}
```

### Local Database Fallback Flow
If there is no internet, the repository bypasses Firestore entirely and loads from local Room database storage (updating the in-memory LRU cache to keep it unified):

```kotlin
suspend fun fetchCoach(id: String): CoachFetchResult {
    // 1. Check true Caching Strategy (In-Memory LRU Cache) first
    val memoryCached = CoachComparisonCache.get(id)
    if (memoryCached != null) {
        return CoachFetchResult.Success(memoryCached, isFromCache = true)
    }

    val isOnline = isNetworkConnected()
    val localCacheEntity = localRepository.getCachedProfesorEntity(id)

    if (isOnline) {
        ...
    }

    // Offline Fallback: Use Room persistent local storage
    return if (localCacheEntity != null) {
        Log.d("CoachComparisonRepo", "Fallback: Returning cached coach $id (Online: $isOnline)")
        val profesor = localCacheEntity.toModel()
        // Update in-memory LRU cache
        CoachComparisonCache.put(id, profesor)
        CoachFetchResult.Success(profesor, isFromCache = true)
    } else {
        Log.d("CoachComparisonRepo", "Failure: No internet and no local db data for coach $id")
        CoachFetchResult.Failure(Exception("No connection and no local db data available for coach $id"))
    }
}
```

### UI Connectivity Banner
In [CoachComparisonScreen](file:///c:/Users/juli2/StudioProjects/uniandesSports-Kotlin/app/src/main/java/com/uniandes/sport/ui/screens/tabs/CoachComparisonScreen.kt), an offline banner displays if the data is fetched under offline fallback:

```kotlin
if (isOffline) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = "Offline",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "You are offline – showing cached data",
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}
```

---

## 4. How to Test This Feature

1. Open the app while connected to the internet, select 2 or 3 coaches for comparison, and click **Compare Now** to download and persist the profiles locally.
2. Go back to the coaches screen.
3. Turn on **Airplane Mode** or disable Wi-Fi/Mobile Data on your device.
4. Click **Compare Now** again.
5. **Verification**:
   * The comparison matrix still opens.
   * A red/pink warning banner displays at the top: `You are offline – showing cached data`.
6. Clear the application cache or reinstall the application to empty the local database.
7. Disconnect from the internet and open the comparison screen.
8. **Verification**:
   * The app displays the friendly empty state: `No connection and no cached data available.` along with a **Retry** button.
