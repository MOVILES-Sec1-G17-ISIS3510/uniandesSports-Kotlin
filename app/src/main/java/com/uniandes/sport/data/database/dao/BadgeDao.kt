package com.uniandes.sport.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.uniandes.sport.data.entities.BadgeEntity
import kotlinx.coroutines.flow.Flow

/**
 * BadgeDao — Data Access Object para BadgeEntity.
 * 
 * DOCUMENTACIÓN:
 * - Query line 13-14: insertBadge() - Insert simple
 * - Query line 16-17: insertBadges() - Batch insert para inicializar caché
 * - Query line 19-20: getBadgesForUser() - Flow para observar cambios en Room
 * - Query line 22-23: getNewBadgesCount() - Badges que no han sido vistos
 * - Query line 25-26: markBadgesAsViewed() - Limpiar flag isNew
 * 
 * PATRÓN: Reactive queries con Flow para que Compose sea notificado de cambios.
 * Se usa OnConflictStrategy.REPLACE para actualizar badges si ya existen.
 * 
 * FEATURE: Local Storage (Requisito b)
 * Todas las queries operan en Room, sin tocar Firestore.
 * 
 * @author Juan Felipe Hernández
 * @since 26-may-2026
 */
@Dao
interface BadgeDao {
    
    /**
     * Línea 13-14: Insert único badge (ej: cuando se desbloquea uno nuevo)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBadge(badge: BadgeEntity)
    
    /**
     * Línea 16-17: Insert batch de badges (ej: carga inicial desde Firestore)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBadges(badges: List<BadgeEntity>)
    
    /**
     * Línea 19-20: Obtener todos los badges del usuario como Flow
     * Se ordena por unlockedAt DESC para mostrar últimos primero
     */
    @Query("SELECT * FROM badges WHERE userId = :userId ORDER BY unlockedAt DESC")
    fun getBadgesForUser(userId: String): Flow<List<BadgeEntity>>
    
    /**
     * Línea 22-23: Contar badges nuevos sin ver
     */
    @Query("SELECT COUNT(*) FROM badges WHERE userId = :userId AND isNew = 1")
    fun getNewBadgesCount(userId: String): Flow<Int>
    
    /**
     * Línea 25-26: Marcar badges como visto (limpiar flag isNew)
     */
    @Query("UPDATE badges SET isNew = 0 WHERE userId = :userId")
    suspend fun markBadgesAsViewed(userId: String)
}
