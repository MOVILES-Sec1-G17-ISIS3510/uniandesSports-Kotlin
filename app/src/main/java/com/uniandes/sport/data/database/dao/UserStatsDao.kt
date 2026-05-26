package com.uniandes.sport.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.uniandes.sport.data.entities.UserStatsEntity
import kotlinx.coroutines.flow.Flow

/**
 * UserStatsDao — Data Access Object para UserStatsEntity.
 * 
 * DOCUMENTACIÓN:
 * - Query line 15-16: insertStats() - Guardar stats en Room
 * - Query line 18-19: getStats() - Flow para observar cambios
 * - Query line 21-22: updateSyncStatus() - Actualizar estado de sync (para UI feedback)
 * - Query line 24-25: updateLastSync() - Actualizar timestamp de última sincronización
 * 
 * PATRÓN: Reactive queries con Flow.
 * Operación REPLACE para mantener la estadística más reciente.
 * 
 * FEATURE: Local Storage (Requisito b) + Caching (Requisito c)
 * Room actúa como caché persistente. Si LRU expira, Room sirve como fallback.
 * syncStatus es para UI (mostrar estado de sincronizacion).
 * 
 * @author Juan Felipe Hernández
 * @since 26-may-2026
 */
@Dao
interface UserStatsDao {
    
    /**
     * Línea 15-16: Guardar/actualizar estadísticas
     * REPLACE: Si ya existe para este userId, sobreescribir
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: UserStatsEntity)
    
    /**
     * Línea 18-19: Obtener stats del usuario como Flow
     * ViewModel observa este Flow para invalidar cuando cambian
     */
    @Query("SELECT * FROM user_stats WHERE userId = :userId")
    fun getStats(userId: String): Flow<UserStatsEntity?>
    
    /**
     * Línea 21-22: Actualizar solo syncStatus (para mostrar Sincronizando...)
     * Más eficiente que actualizar todo el entity
     */
    @Query("UPDATE user_stats SET syncStatus = :status WHERE userId = :userId")
    suspend fun updateSyncStatus(userId: String, status: String)
    
    /**
     * Línea 24-25: Actualizar timestamp de última sincronización
     * Se usa para validar TTL en caché-first pattern
     */
    @Query("UPDATE user_stats SET lastSyncAt = :timestamp WHERE userId = :userId")
    suspend fun updateLastSync(userId: String, timestamp: Long)
}
