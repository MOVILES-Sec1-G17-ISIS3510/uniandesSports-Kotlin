package com.uniandes.sport.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedProfesorEntity::class,
        CachedReviewEntity::class,
        CachedBookingRequestEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ProfesoresCacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): ProfesoresCacheDao

    companion object {
        @Volatile
        private var INSTANCE: ProfesoresCacheDatabase? = null

        fun getInstance(context: Context): ProfesoresCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ProfesoresCacheDatabase::class.java,
                    "profesores_cache.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
