package com.uniandes.sport.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// base de datos room (sqlite) para la cache local de warm-up routines.
// el archivo se llama "warmup_cache.db" y se puede inspeccionar
// desde android studio > app inspection > database inspector
@Database(
    entities = [CachedWarmupRoutineEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WarmupCacheDatabase : RoomDatabase() {
    abstract fun warmupDao(): WarmupCacheDao

    companion object {
        // singleton thread-safe con double-checked locking
        @Volatile
        private var INSTANCE: WarmupCacheDatabase? = null

        fun getInstance(context: Context): WarmupCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WarmupCacheDatabase::class.java,
                    "warmup_cache.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
