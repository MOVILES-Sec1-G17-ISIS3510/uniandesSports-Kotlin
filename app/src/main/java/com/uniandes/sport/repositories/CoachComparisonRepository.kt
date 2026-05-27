package com.uniandes.sport.repositories

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.uniandes.sport.cache.CoachComparisonCache
import com.uniandes.sport.data.local.CachedProfesorEntity
import com.uniandes.sport.data.local.ProfesoresLocalRepository
import com.uniandes.sport.data.local.toModel
import com.uniandes.sport.models.Profesor
import kotlinx.coroutines.tasks.await

sealed interface CoachFetchResult {
    data class Success(val coach: Profesor, val isFromCache: Boolean) : CoachFetchResult
    data class Failure(val exception: Exception) : CoachFetchResult
}

class CoachComparisonRepository private constructor(
    private val context: Context,
    private val localRepository: ProfesoresLocalRepository,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

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

    suspend fun fetchCoach(id: String): CoachFetchResult {
        // 1. Check true Caching Strategy (In-Memory LRU Cache) first
        val memoryCached = CoachComparisonCache.get(id)
        if (memoryCached != null) {
            Log.d("CoachComparisonRepo", "In-Memory LRU Cache HIT for coach $id")
            return CoachFetchResult.Success(memoryCached, isFromCache = true)
        }

        val isOnline = isNetworkConnected()
        val localCacheEntity = localRepository.getCachedProfesorEntity(id)

        if (isOnline) {
            // Check if local database cache is fresh (less than 10 minutes old)
            val isFresh = localCacheEntity != null && (System.currentTimeMillis() - localCacheEntity.cachedAt < 10 * 60 * 1000L)
            if (isFresh && localCacheEntity != null) {
                Log.d("CoachComparisonRepo", "Local DB HIT (fresh < 10m) for coach $id")
                val profesor = localCacheEntity.toModel()
                // Update in-memory LRU cache
                CoachComparisonCache.put(id, profesor)
                return CoachFetchResult.Success(profesor, isFromCache = true)
            }

            // Fetch from Firestore
            try {
                Log.d("CoachComparisonRepo", "Local DB MISS or stale. Fetching coach $id from Firestore...")
                val doc = firestore.collection("profesores").document(id).get().await()
                if (doc.exists()) {
                    val profesor = doc.toObject(Profesor::class.java)?.apply { this.id = doc.id }
                    if (profesor != null) {
                        localRepository.upsertProfesor(profesor)
                        Log.d("CoachComparisonRepo", "Successfully fetched coach $id from Firestore and updated Room storage")
                        // Update in-memory LRU cache
                        CoachComparisonCache.put(id, profesor)
                        return CoachFetchResult.Success(profesor, isFromCache = false)
                    }
                }
            } catch (e: Exception) {
                Log.e("CoachComparisonRepo", "Firestore fetch failed for coach $id. Falling back to local db storage.", e)
            }
        }

        // Offline or Firestore failed -> fallback to local db storage (stale or otherwise)
        return if (localCacheEntity != null) {
            Log.d("CoachComparisonRepo", "Fallback: Returning cached coach $id (Online: $isOnline)")
            val profesor = localCacheEntity.toModel()
            // Update in-memory LRU cache
            CoachComparisonCache.put(id, profesor)
            return CoachFetchResult.Success(profesor, isFromCache = true)
        } else {
            Log.d("CoachComparisonRepo", "Failure: No internet and no local db data for coach $id")
            CoachFetchResult.Failure(Exception("No connection and no local db data available for coach $id"))
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: CoachComparisonRepository? = null

        fun getInstance(context: Context): CoachComparisonRepository {
            return INSTANCE ?: synchronized(this) {
                val localRepo = ProfesoresLocalRepository.getInstance(context)
                val instance = CoachComparisonRepository(context.applicationContext, localRepo)
                INSTANCE = instance
                instance
            }
        }
    }
}
