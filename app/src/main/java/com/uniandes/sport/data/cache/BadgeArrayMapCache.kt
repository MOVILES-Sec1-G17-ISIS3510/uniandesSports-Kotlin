package com.uniandes.sport.data.cache

import android.util.Log
import androidx.collection.ArrayMap
import com.uniandes.sport.data.entities.BadgeEntity

/**
 * BadgeArrayMapCache — In-memory ArrayMap cache para BadgeEntity.
 * 
 * DOCUMENTACIÓN:
 * - Patrón: ArrayMap (optimizado para <50 entries)
 * - TTL: 30 minutos (línea 18)
 * - Max: 18 badges (fijos, nunca crece más)
 * - Operaciones: O(log n) en lugar de O(1), pero con n=18 no importa
 * - Ventaja sobre HashMap: Menos memoria (no hash buckets)
 * 
 * FEATURE: Caching (Requisito c)
 * ArrayMap usa menos memoria que HashMap para <50 items.
 * Para 18 badges: ~1-2 KB vs 4-5 KB con HashMap.
 * 
 * USAR ARRAYMAP CUANDO:
 *  Pocas entradas (<50)
 *  Estructura relativamente fija (18 badges máximo)
 *  Lookup por clave es ocasional (no hot path)
 *  Queremos minimizar memoria
 * 
 * NO USAR ARRAYMAP CUANDO:
 *  Muchas entradas (>100)
 *  Lookups frecuentes (millones por segundo)
 *  Necesito HashMap.get() de O(1)
 * 
 * OPERACIONES:
 * - loadBadges(list) → línea 39: Cargar batch inicial
 * - getBadgeById(id) → línea 51: Lookup por ID
 * - searchByName(query) → línea 65: Búsqueda de texto
 * - getAllBadges() → línea 78: Obtener lista
 * - hasBadge(id) → línea 86: Verificación existencia
 * - addBadge(badge) → línea 94: Agregar nuevo
 * 
 * @author Juan Felipe Hernández
 * @since 26-may-2026
 */
class BadgeArrayMapCache {
    
    /**
     * Línea 43: ArrayMap<badgeId, BadgeEntity>
     * NOTA: No especificar initial capacity, ArrayMap lo maneja internamente
     */
    private val badgeMap: ArrayMap<String, BadgeEntity> = ArrayMap()
    private var lastUpdateTime: Long = 0
    private val ttlMillis: Long = 30 * 60 * 1000 // 30 minutos

    /**
     * Línea 39-50: Cargar todos los badges en el ArrayMap
     * Se llama una vez en init del ViewModel
     * @param badges Lista completa de BadgeEntity (max 18)
     */
    suspend fun loadBadges(badges: List<BadgeEntity>) {
        badgeMap.clear()
        badges.forEach { badge ->
            badgeMap[badge.badgeId] = badge
        }
        lastUpdateTime = System.currentTimeMillis()
        
        val memoryUsage = estimateMemoryUsage()
        Log.d("BADGE_CACHE", "LOADED: ${badges.size} badges into ArrayMap (est. memory: $memoryUsage bytes)")
    }

    /**
     * Línea 51-65: Lookup badge por ID
     * Operación: O(log n) en ArrayMap
     * Con n=18, es prácticamente instantáneo
     */
    fun getBadgeById(badgeId: String): BadgeEntity? {
        if (isExpired()) {
            Log.d("BADGE_CACHE", "EXPIRED: Cache invalidated (age: ${System.currentTimeMillis() - lastUpdateTime}ms)")
            return null
        }
        
        val badge = badgeMap[badgeId]
        if (badge != null) {
            Log.d("BADGE_CACHE", "HIT: Badge[$badgeId] found in ArrayMap")
        } else {
            Log.d("BADGE_CACHE", "MISS: Badge[$badgeId] not found in ArrayMap")
        }
        return badge
    }

    /**
     * Línea 65-73: Buscar badges por nombre
     * Útil para autocomplete o búsqueda
     */
    fun searchByName(query: String): List<BadgeEntity> {
        if (isExpired()) return emptyList()
        
        return badgeMap.values.filter { 
            it.name.contains(query, ignoreCase = true) 
        }
    }

    /**
     * Línea 78-84: Obtener todos los badges
     * Devuelve lista sin modificar Array subyacente
     */
    fun getAllBadges(): List<BadgeEntity> {
        if (isExpired()) return emptyList()
        return badgeMap.values.toList()
    }

    /**
     * Línea 86-92: Verificar si badge existe
     * Usado en BadgeCalculator para validar antes de crear
     */
    fun hasBadge(badgeId: String): Boolean {
        if (isExpired()) return false
        return badgeMap.containsKey(badgeId)
    }

    /**
     * Línea 94-100: Agregar badge nuevo
     * Se llama cuando BadgeCalculator desbloquea un badge
     */
    fun addBadge(badge: BadgeEntity) {
        badgeMap[badge.badgeId] = badge
        Log.d("BADGE_CACHE", "ADD: Badge[${badge.badgeId}] added to ArrayMap")
    }

    /**
     * Línea 102-107: Invalidar caché manualmente
     * Se llama al logout o cuando forceRefresh=true
     */
    fun invalidate() {
        badgeMap.clear()
        lastUpdateTime = 0
        Log.d("BADGE_CACHE", "INVALIDATE: Badge cache cleared")
    }

    /**
     * Línea 109-111: Validar si caché expiró
     */
    private fun isExpired(): Boolean {
        return System.currentTimeMillis() - lastUpdateTime > ttlMillis
    }

    /**
     * Línea 113-121: Estadísticas para debugging
     */
    fun getStats(): String {
        return """
            Badge ArrayMap Cache:
            - Size: ${badgeMap.size} badges
            - Age: ${System.currentTimeMillis() - lastUpdateTime}ms
            - Expired: ${isExpired()}
            - Memory: ${estimateMemoryUsage()} bytes (est.)
        """.trimIndent()
    }

    /**
     * Línea 123-126: Estimación de memoria usado
     * BadgeEntity ~400 bytes promedio
     */
    private fun estimateMemoryUsage(): Int {
        // Estimación: ~400 bytes por entrada
        return badgeMap.size * 400
    }
}
