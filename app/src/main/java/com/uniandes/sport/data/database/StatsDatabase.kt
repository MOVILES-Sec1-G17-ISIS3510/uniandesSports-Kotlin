package com.uniandes.sport.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.uniandes.sport.data.database.dao.ActivityLogDao
import com.uniandes.sport.data.database.dao.BadgeDao
import com.uniandes.sport.data.database.dao.StreakDao
import com.uniandes.sport.data.database.dao.UserStatsDao
import com.uniandes.sport.data.entities.ActivityLogEntity
import com.uniandes.sport.data.entities.BadgeEntity
import com.uniandes.sport.data.entities.StreakEntity
import com.uniandes.sport.data.entities.UserStatsEntity

/**
 * StatsDatabase — Room Database para MyStats feature.
 * 
 * DOCUMENTACIÓN:
 * - Entities (líneas 17-22): 4 entidades definidas
 * - version = 1: Primera versión, sin migrations necesarias
 * - exportSchema = false: No exportar schema JSON
 * 
 * PATRÓN: Singleton pattern con companion object.
 * Double-checked locking para thread-safety en multi-threading.
 * 
 * FEATURE: Local Storage (Requisito b)
 * Base de datos SQLite para almacenamiento persistente.
 * Complementa caché en-memory (LRU + Room juntos).
 * 
 * ACCESO:
 * - badgeDao() → línea 38
 * - userStatsDao() → línea 39
 * - activityLogDao() → línea 40
 * - streakDao() → línea 41
 * 
 * @author Juan Felipe Hernández
 * @since 26-may-2026
 */
@Database(
    entities = [
        BadgeEntity::class,      // Línea 17
        UserStatsEntity::class,  // Línea 18
        ActivityLogEntity::class, // Línea 19
        StreakEntity::class       // Línea 20
    ],
    version = 1,
    exportSchema = false
)
abstract class StatsDatabase : RoomDatabase() {
    
    /**
     * Línea 38: DAOs disponibles desde base de datos
     */
    abstract fun badgeDao(): BadgeDao
    abstract fun userStatsDao(): UserStatsDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun streakDao(): StreakDao

    companion object {
        @Volatile
        private var INSTANCE: StatsDatabase? = null

        /**
         * Línea 47-62: Singleton factory con double-checked locking
         * FEATURE: Multi-threading (Requisito a)
         * @Volatile garantiza que todos los threads ven la última versión de INSTANCE
         * synchronized(this) previene que múltiples threads creen varias DBs
         */
        fun getDatabase(context: Context): StatsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StatsDatabase::class.java,
                    "stats_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
