package com.uniandes.sport.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.uniandes.sport.data.entities.StreakEntity
import kotlinx.coroutines.flow.Flow

/**
 * StreakDao — Data Access Object para StreakEntity.
 * 
 * DOCUMENTACIÓN:
 * - Query line 16-17: insertStreak() - Crear o actualizar racha
 * - Query line 19-20: getStreaksForUser() - Obtener todas las rachas del usuario
 * - Query line 22-23: updateStreak() - Actualizar contador y última fecha
 * 
 * PATRÓN: Optimización de queries.
 * updateStreak() hace una actualización parcial en lugar de recargar todo.
 * 
 * FEATURE: Local Storage (Requisito b)
 * Se almacenan rachas para validar badges tipo "Social Shark" (3 días seguidos).
 * Evita recalcular desde ActivityLog cada vez (más eficiente).
 * 
 * @author Juan Felipe Hernández
 * @since 26-may-2026
 */
@Dao
interface StreakDao {
    
    /**
     * Línea 16-17: Insertar o reemplazar racha
     * REPLACE: Si streakId ya existe, actualizar
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreak(streak: StreakEntity)
    
    /**
     * Línea 19-20: Obtener todas las rachas del usuario como Flow
     * ej: SOCIAL_DAYS (3), EVENT_WEEKS (4), RUN_CONSECUTIVE (2)
     */
    @Query("SELECT * FROM streaks WHERE userId = :userId")
    fun getStreaksForUser(userId: String): Flow<List<StreakEntity>>
    
    /**
     * Línea 22-23: Actualizar solo counters de racha
     * Más eficiente que recargar el entity completo
     */
    @Query("UPDATE streaks SET currentCount = :count, lastDate = :lastDate WHERE streakId = :streakId")
    suspend fun updateStreak(streakId: String, count: Int, lastDate: Long)
}
