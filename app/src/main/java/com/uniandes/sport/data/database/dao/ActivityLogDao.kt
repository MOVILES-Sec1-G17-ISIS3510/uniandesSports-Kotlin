package com.uniandes.sport.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.uniandes.sport.data.entities.ActivityLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * ActivityLogDao — Data Access Object para ActivityLogEntity.
 * 
 * DOCUMENTACIÓN:
 * - Query line 16-17: insertActivity() - Registrar una actividad
 * - Query line 19-20: insertActivities() - Batch insert para carga inicial
 * - Query line 22-29: getActivitiesInRange() - Filtrar por fecha (para gráficas)
 * - Query line 31-39: getActivitySummaryByDay() - Agregar por día (para charts)
 * 
 * PATRÓN: Range queries para generar datos de gráficas.
 * Las queries con GROUP BY/SUM simulan agregaciones (no se pueden hacer en UI directamente).
 * 
 * FEATURE: Local Storage (Requisito b)
 * Se almacena histórico de actividades para calcular:
 * 1. Streaks (días seguidos escribiendo)
 * 2. Gráficas (km por día, posts por semana)
 * 3. Badges (condiciones como "50+ mensajes")
 * 
 * @author Juan Felipe Hernández
 * @since 26-may-2026
 */
@Dao
interface ActivityLogDao {
    
    /**
     * Línea 16-17: Insertar una actividad
     * IGNORE: Si la misma actividad existe, no duplicar
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActivity(activity: ActivityLogEntity)
    
    /**
     * Línea 19-20: Insertar batch de actividades
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActivities(activities: List<ActivityLogEntity>)
    
    /**
     * Línea 22-29: Obtener actividades en rango de fechas
     * Usado por gráficas para últimos 7 días
     * @param startDate timestamp de inicio (ej: hace 7 días)
     * @param endDate timestamp de fin (ahora)
     */
    @Query("""
        SELECT * FROM activity_log 
        WHERE userId = :userId 
        AND activityDate BETWEEN :startDate AND :endDate
        ORDER BY activityDate DESC
    """)
    fun getActivitiesInRange(
        userId: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<ActivityLogEntity>>
    
    /**
     * Línea 31-39: Resumen agregado por día
     * Agrupa actividades por fecha y suma valores
     * Devuelve datos listos para dibujar en gráfica
     */
    @Query("""
        SELECT strftime('%Y-%m-%d', activityDate / 1000, 'unixepoch') as date, 
               SUM(value) as total
        FROM activity_log
        WHERE userId = :userId 
        AND activityType = :type 
        AND activityDate > :since
        GROUP BY date
        ORDER BY date DESC
    """)
    fun getActivitySummaryByDay(
        userId: String,
        type: String,
        since: Long
    ): Flow<List<DailySummary>>
}

/**
 * Data class para resultado de query agregada
 * Mapea resultado del GROUP BY en SQL
 */
data class DailySummary(
    val date: String,
    val total: Float
)
