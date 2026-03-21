package com.uniandes.sport.repositories

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.uniandes.sport.models.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * TÁCTICA DE ARQUITECTURA: Caching local en RAM
 * Patrón: Proxy (Cache) / Repository Pattern
 * 
 * Propósito: Optimizar el rendimiento y disminuir costos de lectura de Firestore.
 * Mantiene los eventos en memoria. Si el usuario navega entre pantallas,
 * retorna la lista directamente desde la RAM evitando una re-lectura en Firebase,
 * respetando un Time-To-Live (TTL).
 */
object EventCacheRepository {
    private val db = FirebaseFirestore.getInstance()
    
    private val _cachedEvents = MutableStateFlow<List<Event>>(emptyList())
    val cachedEvents: StateFlow<List<Event>> = _cachedEvents.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var lastFetchTime: Long = 0
    private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 mins TTL

    /**
     * Refresca el caché sólo si expiró o si es forzado.
     */
    fun fetchEventsIfNeeded(forceRefresh: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        val isCacheExpired = (currentTime - lastFetchTime) > CACHE_TTL_MS

        if (forceRefresh || isCacheExpired || _cachedEvents.value.isEmpty()) {
            Log.d("EventCache", "Fetching from remote Firestore...")
            fetchFromRemote()
        } else {
            Log.d("EventCache", "Cache HIT: Returned ${cachedEvents.value.size} events from memory.")
        }
    }

    private fun fetchFromRemote() {
        _isLoading.value = true
        db.collection("events").get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.mapNotNull { doc ->
                    try {
                        val event = doc.toObject(Event::class.java)
                        event.id = doc.id
                        event
                    } catch (e: Exception) {
                        Log.e("EventCache", "Error parsing event ${doc.id}", e)
                        null
                    }
                }
                _cachedEvents.value = list
                lastFetchTime = System.currentTimeMillis()
                _isLoading.value = false
            }
            .addOnFailureListener { e ->
                Log.e("EventCache", "Failed fetching events", e)
                _isLoading.value = false
            }
    }

    /**
     * Invalida el caché para forzar la siguiente carga (ej: tras crear un partido)
     */
    fun invalidateCache() {
        lastFetchTime = 0
    }
}
