package com.uniandes.sport.cache

import android.util.Log
import android.util.LruCache
import com.uniandes.sport.models.Profesor

/**
 * LRU (Least Recently Used) Memory Cache specifically for Coach Comparison.
 * Caches fetched Profesor profiles in memory during the active session.
 */
object CoachComparisonCache {
    private const val MAX_ENTRIES = 10 // Max capacity to hold recently compared coaches in memory

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

    fun clear() {
        cache.evictAll()
        Log.d("CoachComparisonCache", "Memory Cache cleared")
    }
}
