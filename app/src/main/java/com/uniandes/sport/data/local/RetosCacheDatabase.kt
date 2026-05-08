package com.uniandes.sport.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// base de datos room (sqlite) para la cache local de retos.
// el nombre del archivo es "retos_cache.db" y se puede inspeccionar
// desde android studio > app inspection > database inspector.
// fallbacktodestructivemigration borra y recrea la bd si cambia la version,
// esto es aceptable porque los datos son solo cache (la fuente de verdad es firestore)
@Database(
    entities = [CachedRetoEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RetosCacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): RetosCacheDao

    companion object {
        // volatile asegura que todos los hilos vean el mismo valor de instance.
        // synchronized evita que dos hilos creen la bd al mismo tiempo (singleton thread-safe)
        @Volatile
        private var INSTANCE: RetosCacheDatabase? = null

        fun getInstance(context: Context): RetosCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RetosCacheDatabase::class.java,
                    "retos_cache.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
